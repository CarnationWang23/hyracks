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

package edu.uci.ics.hyracks.storage.am.lsm.btree.dataflow;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexDataflowHelperFactory;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IndexDataflowHelper;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMFlushControllerProvider;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIOOperationSchedulerProvider;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMMergePolicyProvider;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMOperationTrackerProvider;

public class LSMBTreeDataflowHelperFactory implements IIndexDataflowHelperFactory {

    private static final long serialVersionUID = 1L;

    private final ILSMFlushControllerProvider flushControllerProvider;
    private final ILSMMergePolicyProvider mergePolicyProvider;
    private final ILSMOperationTrackerProvider opTrackerProvider;
    private final ILSMIOOperationSchedulerProvider ioSchedulerProvider;

    public LSMBTreeDataflowHelperFactory(ILSMFlushControllerProvider flushControllerProvider,
            ILSMMergePolicyProvider mergePolicyProvider, ILSMOperationTrackerProvider opTrackerProvider,
            ILSMIOOperationSchedulerProvider ioSchedulerProvider) {
        this.flushControllerProvider = flushControllerProvider;
        this.mergePolicyProvider = mergePolicyProvider;
        this.opTrackerProvider = opTrackerProvider;
        this.ioSchedulerProvider = ioSchedulerProvider;
    }

    @Override
    public IndexDataflowHelper createIndexDataflowHelper(IIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx,
            int partition) {
        return new LSMBTreeDataflowHelper(opDesc, ctx, partition, flushControllerProvider.getFlushController(),
                mergePolicyProvider.getMergePolicy(), opTrackerProvider.getOperationTracker(),
                ioSchedulerProvider.getIOScheduler());
    }
}
