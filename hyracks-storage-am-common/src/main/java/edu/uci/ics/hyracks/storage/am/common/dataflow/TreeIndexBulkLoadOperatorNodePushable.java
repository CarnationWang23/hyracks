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
package edu.uci.ics.hyracks.storage.am.common.dataflow;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryInputSinkOperatorNodePushable;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexBulkLoader;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexLifecycleManager;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.api.IndexException;

public class TreeIndexBulkLoadOperatorNodePushable extends AbstractUnaryInputSinkOperatorNodePushable {
    private final AbstractTreeIndexOperatorDescriptor opDesc;
    private final IHyracksTaskContext ctx;
    private final IIndexLifecycleManager lcManager;
    private final float fillFactor;
    private final boolean verifyInput;
    private final TreeIndexDataflowHelper treeIndexHelper;
    private FrameTupleAccessor accessor;
    private IIndexBulkLoader bulkLoader;
    private ITreeIndex treeIndex;

    private IRecordDescriptorProvider recordDescProvider;

    private PermutingFrameTupleReference tuple = new PermutingFrameTupleReference();

    public TreeIndexBulkLoadOperatorNodePushable(AbstractTreeIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx,
            int partition, int[] fieldPermutation, float fillFactor, boolean verifyInput, IRecordDescriptorProvider recordDescProvider) {
        this.opDesc = opDesc;
        this.ctx = ctx;
        this.lcManager = opDesc.getLifecycleManagerProvider().getLifecycleManager(ctx);
        this.treeIndexHelper = (TreeIndexDataflowHelper) opDesc.getIndexDataflowHelperFactory()
                .createIndexDataflowHelper(opDesc, ctx, partition);
        this.fillFactor = fillFactor;
        this.verifyInput = verifyInput;
        this.recordDescProvider = recordDescProvider;
        tuple.setFieldPermutation(fieldPermutation);
    }

    @Override
    public void open() throws HyracksDataException {
        RecordDescriptor recDesc = recordDescProvider.getInputRecordDescriptor(opDesc.getActivityId(), 0);
        accessor = new FrameTupleAccessor(ctx.getFrameSize(), recDesc);
        treeIndex = (ITreeIndex) lcManager.open(treeIndexHelper);
        try {
            bulkLoader = treeIndex.createBulkLoader(fillFactor, verifyInput);
        } catch (Exception e) {
            lcManager.close(treeIndexHelper);
            throw new HyracksDataException(e);
        }
    }

    @Override
    public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
        accessor.reset(buffer);
        int tupleCount = accessor.getTupleCount();
        for (int i = 0; i < tupleCount; i++) {
            tuple.reset(accessor, i);
            try {
				bulkLoader.add(tuple);
			} catch (IndexException e) {
				throw new HyracksDataException(e);
			}
        }
    }

    @Override
    public void close() throws HyracksDataException {
        try {
            bulkLoader.end();
        } catch (Exception e) {
            throw new HyracksDataException(e);
        } finally {
            lcManager.close(treeIndexHelper);
        }
    }

    @Override
    public void fail() throws HyracksDataException {
    }
}