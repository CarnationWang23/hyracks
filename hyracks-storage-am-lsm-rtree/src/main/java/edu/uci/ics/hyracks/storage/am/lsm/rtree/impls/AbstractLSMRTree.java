/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.storage.am.lsm.rtree.impls;

import java.util.LinkedList;
import java.util.List;

import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ILinearizeComparatorFactory;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.storage.am.btree.exceptions.BTreeDuplicateKeyException;
import edu.uci.ics.hyracks.storage.am.btree.exceptions.BTreeNonExistentKeyException;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.btree.impls.RangePredicate;
import edu.uci.ics.hyracks.storage.am.common.api.IFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexBulkLoadContext;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexOpContext;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexBulkLoader;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexCursor;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.IndexType;
import edu.uci.ics.hyracks.storage.am.common.api.TreeIndexException;
import edu.uci.ics.hyracks.storage.am.common.impls.NoOpOperationCallback;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.IndexOp;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMComponentFinalizer;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMFileManager;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMFlushPolicy;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIndex;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMMergePolicy;
import edu.uci.ics.hyracks.storage.am.lsm.common.freepage.InMemoryFreePageManager;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.LSMHarness;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.TreeFactory;
import edu.uci.ics.hyracks.storage.am.rtree.api.IRTreeInteriorFrame;
import edu.uci.ics.hyracks.storage.am.rtree.api.IRTreeLeafFrame;
import edu.uci.ics.hyracks.storage.am.rtree.impls.RTree;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

public abstract class AbstractLSMRTree implements ILSMIndex, ITreeIndex {

    public class LSMRTreeComponent {
        private final RTree rtree;
        private final BTree btree;

        LSMRTreeComponent(RTree rtree, BTree btree) {
            this.rtree = rtree;
            this.btree = btree;
        }

        public RTree getRTree() {
            return rtree;
        }

        public BTree getBTree() {
            return btree;
        }
    }

    protected final LSMHarness lsmHarness;

    protected final ILinearizeComparatorFactory linearizer;
    protected final int[] comparatorFields;
    protected final IBinaryComparatorFactory[] linearizerArray;

    // In-memory components.
    protected final LSMRTreeComponent memComponent;
    protected final InMemoryFreePageManager memFreePageManager;
    protected final static int MEM_RTREE_FILE_ID = 0;
    protected final static int MEM_BTREE_FILE_ID = 1;

    // This is used to estimate number of tuples in the memory RTree and BTree
    // for efficient memory allocation in the sort operation prior to flushing
    protected int memRTreeTuples = 0;
    protected int memBTreeTuples = 0;
    protected TreeTupleSorter rTreeTupleSorter = null;

    // On-disk components.
    protected final ILSMFileManager fileManager;
    protected final IBufferCache diskBufferCache;
    protected final IFileMapProvider diskFileMapProvider;
    // For creating RTree's used in flush and merge.
    protected final RTreeFactory diskRTreeFactory;
    // List of LSMRTreeComponent instances. Using Object for better sharing via
    // ILSMTree + LSMHarness.
    protected final LinkedList<Object> diskComponents = new LinkedList<Object>();
    // Helps to guarantees physical consistency of LSM components.
    protected final ILSMComponentFinalizer componentFinalizer;

    private IBinaryComparatorFactory[] btreeCmpFactories;
    private IBinaryComparatorFactory[] rtreeCmpFactories;

    // Common for in-memory and on-disk components.
    protected final ITreeIndexFrameFactory rtreeInteriorFrameFactory;
    protected final ITreeIndexFrameFactory btreeInteriorFrameFactory;
    protected final ITreeIndexFrameFactory rtreeLeafFrameFactory;
    protected final ITreeIndexFrameFactory btreeLeafFrameFactory;

    public AbstractLSMRTree(IBufferCache memBufferCache, InMemoryFreePageManager memFreePageManager,
            ITreeIndexFrameFactory rtreeInteriorFrameFactory, ITreeIndexFrameFactory rtreeLeafFrameFactory,
            ITreeIndexFrameFactory btreeInteriorFrameFactory, ITreeIndexFrameFactory btreeLeafFrameFactory,
            ILSMFileManager fileManager, RTreeFactory diskRTreeFactory, IFileMapProvider diskFileMapProvider,
            ILSMComponentFinalizer componentFinalizer, int fieldCount, IBinaryComparatorFactory[] rtreeCmpFactories,
            IBinaryComparatorFactory[] btreeCmpFactories, ILinearizeComparatorFactory linearizer,
            int[] comparatorFields, IBinaryComparatorFactory[] linearizerArray, ILSMFlushPolicy flushPolicy, ILSMMergePolicy mergePolicy) {
        RTree memRTree = new RTree(memBufferCache, fieldCount, rtreeCmpFactories, memFreePageManager,
                rtreeInteriorFrameFactory, rtreeLeafFrameFactory);
        // TODO: Do we need another operation callback here?
        BTree memBTree = new BTree(memBufferCache, fieldCount, btreeCmpFactories,
                memFreePageManager, btreeInteriorFrameFactory, btreeLeafFrameFactory);
        memComponent = new LSMRTreeComponent(memRTree, memBTree);
        this.memFreePageManager = memFreePageManager;
        this.diskBufferCache = diskRTreeFactory.getBufferCache();
        this.diskFileMapProvider = diskFileMapProvider;
        this.fileManager = fileManager;
        this.rtreeInteriorFrameFactory = rtreeInteriorFrameFactory;
        this.rtreeLeafFrameFactory = rtreeLeafFrameFactory;
        this.btreeInteriorFrameFactory = btreeInteriorFrameFactory;
        this.btreeLeafFrameFactory = btreeLeafFrameFactory;
        this.diskRTreeFactory = diskRTreeFactory;
        this.btreeCmpFactories = btreeCmpFactories;
        this.rtreeCmpFactories = rtreeCmpFactories;
        this.lsmHarness = new LSMHarness(this, flushPolicy, mergePolicy);
        this.componentFinalizer = componentFinalizer;
        this.linearizer = linearizer;
        this.comparatorFields = comparatorFields;
        this.linearizerArray = linearizerArray;
    }

    @Override
    public void create(int indexFileId) throws HyracksDataException {
        memComponent.getRTree().create(MEM_RTREE_FILE_ID);
        memComponent.getBTree().create(MEM_BTREE_FILE_ID);
        fileManager.createDirs();
    }

    @Override
    public void open(int indexFileId) throws HyracksDataException {
        memComponent.getRTree().open(MEM_RTREE_FILE_ID);
        memComponent.getBTree().open(MEM_BTREE_FILE_ID);
    }

    @Override
    public void close() throws HyracksDataException {
        memComponent.getRTree().close();
        memComponent.getBTree().close();
    }

    @SuppressWarnings("rawtypes")
    protected ITreeIndex createDiskTree(TreeFactory diskTreeFactory, FileReference fileRef, boolean createTree)
            throws HyracksDataException {
        // File will be deleted during cleanup of merge().
        diskBufferCache.createFile(fileRef);
        int diskTreeFileId = diskFileMapProvider.lookupFileId(fileRef);
        // File will be closed during cleanup of merge().
        diskBufferCache.openFile(diskTreeFileId);
        // Create new tree instance.
        ITreeIndex diskTree = diskTreeFactory.createIndexInstance();
        if (createTree) {
            diskTree.create(diskTreeFileId);
        }
        // Tree will be closed during cleanup of merge().
        diskTree.open(diskTreeFileId);
        return diskTree;
    }

    @Deprecated
    private ITreeIndexBulkLoader bulkloader;

    @Override
    public IIndexBulkLoadContext beginBulkLoad(float fillFactor) throws TreeIndexException, HyracksDataException {
        bulkloader = createBulkLoader(fillFactor);
        return null;
    }

    @Override
    public void bulkLoadAddTuple(ITupleReference tuple, IIndexBulkLoadContext ictx) throws HyracksDataException {
        bulkloader.add(tuple);
    }

    @Override
    public void endBulkLoad(IIndexBulkLoadContext ictx) throws HyracksDataException {
        bulkloader.end();
    }

    @Override
    public ITreeIndexFrameFactory getLeafFrameFactory() {
        return memComponent.getRTree().getLeafFrameFactory();
    }

    @Override
    public ITreeIndexFrameFactory getInteriorFrameFactory() {
        return memComponent.getRTree().getInteriorFrameFactory();
    }

    @Override
    public IFreePageManager getFreePageManager() {
        return memComponent.getRTree().getFreePageManager();
    }

    @Override
    public int getFieldCount() {
        return memComponent.getRTree().getFieldCount();
    }

    @Override
    public int getRootPageId() {
        return memComponent.getRTree().getRootPageId();
    }

    @Override
    public IndexType getIndexType() {
        return memComponent.getRTree().getIndexType();
    }

    @Override
    public int getFileId() {
        return memComponent.getRTree().getFileId();
    }

    public boolean insertUpdateOrDelete(ITupleReference tuple, IIndexOpContext ictx) throws HyracksDataException,
            TreeIndexException {
        LSMRTreeOpContext ctx = (LSMRTreeOpContext) ictx;
        if (ctx.getIndexOp() == IndexOp.PHYSICALDELETE) {
            throw new UnsupportedOperationException("Physical delete not yet supported in LSM R-tree");
        }

        if (ctx.getIndexOp() == IndexOp.INSERT) {
            // Before each insert, we must check whether there exist a killer
            // tuple in the memBTree. If we find a killer tuple, we must truly
            // delete the existing tuple from the BTree, and then insert it to
            // memRTree. Otherwise, the old killer tuple will kill the newly
            // added RTree tuple.
            RangePredicate btreeRangePredicate = new RangePredicate(tuple, tuple, true, true,
                    ctx.getBTreeMultiComparator(), ctx.getBTreeMultiComparator());
            ITreeIndexCursor cursor = ctx.memBTreeAccessor.createSearchCursor();
            ctx.memBTreeAccessor.search(cursor, btreeRangePredicate);
            boolean foundTupleInMemoryBTree = false;
            try {
                if (cursor.hasNext()) {
                    foundTupleInMemoryBTree = true;
                }
            } finally {
                cursor.close();
            }
            if (foundTupleInMemoryBTree) {
                try {
                    ctx.memBTreeAccessor.delete(tuple);
                    memBTreeTuples--;
                } catch (BTreeNonExistentKeyException e) {
                    // Tuple has been deleted in the meantime. Do nothing.
                    // This normally shouldn't happen if we are dealing with
                    // good citizens since LSMRTree is used as a secondary
                    // index and a tuple shouldn't be deleted twice without
                    // insert between them.
                }
            } else {
                ctx.memRTreeAccessor.insert(tuple);
                memRTreeTuples++;
            }

        } else {
            try {
                ctx.memBTreeAccessor.insert(tuple);
                memBTreeTuples++;
            } catch (BTreeDuplicateKeyException e) {
                // Do nothing, because one delete tuple is enough to indicate
                // that all the corresponding insert tuples are deleted
            }
        }
        return true;
    }

    @Override
    public void addMergedComponent(Object newComponent, List<Object> mergedComponents) {
        diskComponents.removeAll(mergedComponents);
        diskComponents.addLast(newComponent);
    }

    @Override
    public void addFlushedComponent(Object index) {
        diskComponents.addFirst(index);
    }

    @Override
    public InMemoryFreePageManager getInMemoryFreePageManager() {
        return memFreePageManager;
    }

    @Override
    public void resetInMemoryComponent() throws HyracksDataException {
        memComponent.getRTree().create(MEM_RTREE_FILE_ID);
        memComponent.getBTree().create(MEM_BTREE_FILE_ID);
        memFreePageManager.reset();
        memRTreeTuples = 0;
        memBTreeTuples = 0;
    }

    @Override
    public List<Object> getDiskComponents() {
        return diskComponents;
    }

    protected LSMRTreeOpContext createOpContext() {
        return new LSMRTreeOpContext((RTree.RTreeAccessor) memComponent.getRTree().createAccessor(
                NoOpOperationCallback.INSTANCE, NoOpOperationCallback.INSTANCE),
                (IRTreeLeafFrame) rtreeLeafFrameFactory.createFrame(),
                (IRTreeInteriorFrame) rtreeInteriorFrameFactory.createFrame(), memFreePageManager
                        .getMetaDataFrameFactory().createFrame(), 8, (BTree.BTreeAccessor) memComponent.getBTree()
                        .createAccessor(NoOpOperationCallback.INSTANCE, NoOpOperationCallback.INSTANCE),
                btreeLeafFrameFactory, btreeInteriorFrameFactory, memFreePageManager.getMetaDataFrameFactory()
                        .createFrame(), rtreeCmpFactories, btreeCmpFactories, null, null);
    }

    @Override
    public IBinaryComparatorFactory[] getComparatorFactories() {
        return rtreeCmpFactories;
    }

    @Override
    public IBufferCache getBufferCache() {
        return diskBufferCache;
    }

    @Override
    public ILSMComponentFinalizer getComponentFinalizer() {
        return componentFinalizer;
    }

}
