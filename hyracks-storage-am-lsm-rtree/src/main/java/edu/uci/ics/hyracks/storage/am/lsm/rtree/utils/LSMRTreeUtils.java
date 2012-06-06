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

package edu.uci.ics.hyracks.storage.am.lsm.rtree.utils;

import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ILinearizeComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.api.io.IIOManager;
import edu.uci.ics.hyracks.data.std.primitive.DoublePointable;
import edu.uci.ics.hyracks.data.std.primitive.IntegerPointable;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.TreeIndexException;
import edu.uci.ics.hyracks.storage.am.common.frames.LIFOMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.freepage.LinkedListFreePageManagerFactory;
import edu.uci.ics.hyracks.storage.am.common.impls.NoOpOperationCallback;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMFileManager;
import edu.uci.ics.hyracks.storage.am.lsm.common.freepage.InMemoryFreePageManager;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.BTreeFactory;
import edu.uci.ics.hyracks.storage.am.lsm.rtree.impls.LSMRTree;
import edu.uci.ics.hyracks.storage.am.lsm.rtree.impls.LSMRTreeFileManager;
import edu.uci.ics.hyracks.storage.am.lsm.rtree.impls.RTreeFactory;
import edu.uci.ics.hyracks.storage.am.lsm.rtree.tuples.LSMTypeAwareTupleWriterFactory;
import edu.uci.ics.hyracks.storage.am.rtree.frames.RTreeNSMInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.rtree.frames.RTreeNSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.rtree.frames.RTreePolicyType;
import edu.uci.ics.hyracks.storage.am.rtree.linearize.HilbertDoubleComparatorFactory;
import edu.uci.ics.hyracks.storage.am.rtree.linearize.ZCurveDoubleComparatorFactory;
import edu.uci.ics.hyracks.storage.am.rtree.linearize.ZCurveIntComparatorFactory;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

public class LSMRTreeUtils {
    public static LSMRTree createLSMTree(IBufferCache memBufferCache, InMemoryFreePageManager memFreePageManager,
            IIOManager ioManager, String onDiskDir, IBufferCache diskBufferCache, IFileMapProvider diskFileMapProvider,
            ITypeTraits[] typeTraits, IBinaryComparatorFactory[] rtreeCmpFactories,
            IBinaryComparatorFactory[] btreeCmpFactories, IPrimitiveValueProviderFactory[] valueProviderFactories,
            RTreePolicyType rtreePolicyType, ILinearizeComparatorFactory linearizer) {
        LSMTypeAwareTupleWriterFactory rtreeTupleWriterFactory = new LSMTypeAwareTupleWriterFactory(typeTraits, false);
        LSMTypeAwareTupleWriterFactory btreeTupleWriterFactory = new LSMTypeAwareTupleWriterFactory(typeTraits, true);

        ITreeIndexFrameFactory rtreeInteriorFrameFactory = new RTreeNSMInteriorFrameFactory(rtreeTupleWriterFactory,
                valueProviderFactories, rtreePolicyType);
        ITreeIndexFrameFactory rtreeLeafFrameFactory = new RTreeNSMLeafFrameFactory(rtreeTupleWriterFactory,
                valueProviderFactories, rtreePolicyType);

        ITreeIndexFrameFactory btreeInteriorFrameFactory = new BTreeNSMInteriorFrameFactory(btreeTupleWriterFactory);
        ITreeIndexFrameFactory btreeLeafFrameFactory = new BTreeNSMLeafFrameFactory(btreeTupleWriterFactory);

        ITreeIndexMetaDataFrameFactory metaFrameFactory = new LIFOMetaDataFrameFactory();
        LinkedListFreePageManagerFactory freePageManagerFactory = new LinkedListFreePageManagerFactory(diskBufferCache,
                metaFrameFactory);

        RTreeFactory diskRTreeFactory = new RTreeFactory(diskBufferCache, freePageManagerFactory, rtreeCmpFactories,
                typeTraits.length, rtreeInteriorFrameFactory, rtreeLeafFrameFactory);
        // TODO: Do we need another operation callback here?
        BTreeFactory diskBTreeFactory = new BTreeFactory(diskBufferCache, NoOpOperationCallback.INSTANCE,
                freePageManagerFactory, btreeCmpFactories, typeTraits.length, btreeInteriorFrameFactory,
                btreeLeafFrameFactory);
        
        ILSMFileManager fileNameManager = new LSMRTreeFileManager(ioManager, diskFileMapProvider, onDiskDir);
        LSMRTree lsmTree = new LSMRTree(memBufferCache, memFreePageManager, rtreeInteriorFrameFactory,
                rtreeLeafFrameFactory, btreeInteriorFrameFactory, btreeLeafFrameFactory, fileNameManager,
                diskRTreeFactory, diskBTreeFactory, diskFileMapProvider, typeTraits.length, rtreeCmpFactories,
                btreeCmpFactories, linearizer);
        return lsmTree;
    }

	public static ILinearizeComparatorFactory proposeBestLinearizer(ITypeTraits[] typeTraits, int numKeyFields) throws TreeIndexException {
		for(int i = 0; i < numKeyFields; i++) {
    		if(!(typeTraits[i].getClass().equals(typeTraits[0].getClass()))) {
    			throw new TreeIndexException("Cannot propose linearizer if dimensions have different types");
    		}
    	}
		
    	if(numKeyFields / 2 == 2 && (typeTraits[0] == DoublePointable.TYPE_TRAITS)) {
        	return new HilbertDoubleComparatorFactory(2);
        } else if(typeTraits[0] == DoublePointable.TYPE_TRAITS) {
    		return new ZCurveDoubleComparatorFactory(numKeyFields / 2);
    	} else if(typeTraits[0] == IntegerPointable.TYPE_TRAITS) {
    		return new ZCurveIntComparatorFactory(numKeyFields / 2);
    	}
    	
    	throw new TreeIndexException("Cannot propose linearizer");
	}
}
