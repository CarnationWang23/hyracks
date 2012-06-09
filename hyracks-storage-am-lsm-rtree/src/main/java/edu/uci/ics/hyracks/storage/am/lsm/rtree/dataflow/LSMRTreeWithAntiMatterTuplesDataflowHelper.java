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

package edu.uci.ics.hyracks.storage.am.lsm.rtree.dataflow;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.IIOManager;
import edu.uci.ics.hyracks.storage.am.common.api.IOperationCallbackProvider;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.api.TreeIndexException;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.lsm.common.freepage.InMemoryFreePageManager;
import edu.uci.ics.hyracks.storage.am.lsm.rtree.utils.LSMRTreeUtils;
import edu.uci.ics.hyracks.storage.am.rtree.frames.RTreePolicyType;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

public class LSMRTreeWithAntiMatterTuplesDataflowHelper extends AbstractLSMRTreeDataflowHelper {
    public LSMRTreeWithAntiMatterTuplesDataflowHelper(IIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx,
            int partition, IBinaryComparatorFactory[] btreeComparatorFactories,
            IPrimitiveValueProviderFactory[] valueProviderFactories, RTreePolicyType rtreePolicyType) {
        super(opDesc, ctx, partition, btreeComparatorFactories, valueProviderFactories, rtreePolicyType);
    }

    public LSMRTreeWithAntiMatterTuplesDataflowHelper(IIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx,
            IOperationCallbackProvider opCallbackProvider, int partition, boolean createIfNotExists, int memPageSize,
            int memNumPages, IBinaryComparatorFactory[] btreeComparatorFactories,
            IPrimitiveValueProviderFactory[] valueProviderFactories, RTreePolicyType rtreePolicyType) {
        super(opDesc, ctx, opCallbackProvider, partition, createIfNotExists, memPageSize, memNumPages,
                btreeComparatorFactories, valueProviderFactories, rtreePolicyType);
    }

    @Override
    protected ITreeIndex createLSMTree(IBufferCache memBufferCache, InMemoryFreePageManager memFreePageManager,
            IIOManager ioManager, String onDiskDir, IBufferCache diskBufferCache, IFileMapProvider diskFileMapProvider,
            ITypeTraits[] typeTraits, IBinaryComparatorFactory[] rtreeCmpFactories,
            IBinaryComparatorFactory[] btreeCmpFactories, IPrimitiveValueProviderFactory[] valueProviderFactories,
            RTreePolicyType rtreePolicyType) throws HyracksDataException {
        try {
            return LSMRTreeUtils.createLSMTreeWithAntiMatterTuples(memBufferCache, memFreePageManager, ioManager,
                    onDiskDir, diskBufferCache, diskFileMapProvider, typeTraits, rtreeCmpFactories, btreeCmpFactories,
                    valueProviderFactories, rtreePolicyType);
        } catch (TreeIndexException e) {
            throw new HyracksDataException(e);
        }
    }
}
