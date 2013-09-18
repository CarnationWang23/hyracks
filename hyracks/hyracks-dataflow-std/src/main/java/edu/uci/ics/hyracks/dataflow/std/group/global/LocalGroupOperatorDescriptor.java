/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.dataflow.std.group.global;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.IActivityGraphBuilder;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFamily;
import edu.uci.ics.hyracks.api.dataflow.value.INormalizedKeyComputerFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.IOperatorDescriptorRegistry;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryInputUnaryOutputOperatorNodePushable;
import edu.uci.ics.hyracks.dataflow.std.group.IAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.group.global.base.IPushBasedGrouper;

public class LocalGroupOperatorDescriptor extends AbstractSingleActivityOperatorDescriptor {

    private static final long serialVersionUID = 1L;

    private final int framesLimit;

    private final int[] keyFields, decorFields;

    private final IAggregatorDescriptorFactory aggregatorFactory, partialMergerFactory, finalMergerFactory;

    private final IBinaryComparatorFactory[] comparatorFactories;

    private final INormalizedKeyComputerFactory firstNormalizerFactory;

    private final IBinaryHashFunctionFamily[] hashFamilies;

    private final GroupAlgorithms algorithm;

    public enum GroupAlgorithms {
        SORT_GROUP,
        SORT_GROUP_MERGE_GROUP,
        HASH_GROUP,
        HASH_GROUP_SORT_MERGE_GROUP,
        SIMPLE_HYBRID_HASH,
        RECURSIVE_HYBRID_HASH
    }

    public LocalGroupOperatorDescriptor(IOperatorDescriptorRegistry spec, int[] keyFields, int[] decorFields,
            int framesLimit, IBinaryComparatorFactory[] comparatorFactories, IBinaryHashFunctionFamily[] hashFamilies,
            INormalizedKeyComputerFactory firstNormalizerFactory, IAggregatorDescriptorFactory aggregatorFactory,
            IAggregatorDescriptorFactory partialMergerFactory, IAggregatorDescriptorFactory finalMergerFactory,
            RecordDescriptor outRecDesc, GroupAlgorithms algorithm) throws HyracksDataException {
        super(spec, 1, 1);
        this.framesLimit = framesLimit;
        if (framesLimit <= 3) {
            throw new HyracksDataException("Not enough memory assigned for " + this.displayName
                    + ": at least 3 frames are necessary but just " + framesLimit + " available.");
        }
        this.keyFields = keyFields;
        this.decorFields = decorFields;
        this.aggregatorFactory = aggregatorFactory;
        this.partialMergerFactory = partialMergerFactory;
        this.finalMergerFactory = finalMergerFactory;
        this.comparatorFactories = comparatorFactories;
        this.firstNormalizerFactory = firstNormalizerFactory;
        this.hashFamilies = hashFamilies;
        recordDescriptors[0] = outRecDesc;
        this.algorithm = algorithm;
    }

    @Override
    public IOperatorNodePushable createPushRuntime(final IHyracksTaskContext ctx,
            IRecordDescriptorProvider recordDescProvider, int partition, int nPartitions) throws HyracksDataException {

        final IBinaryComparator[] comparators = new IBinaryComparator[comparatorFactories.length];
        for (int i = 0; i < comparators.length; i++) {
            comparators[i] = comparatorFactories[i].createBinaryComparator();
        }

        final RecordDescriptor inRecDesc = recordDescProvider.getInputRecordDescriptor(getActivityId(), 0);

        final RecordDescriptor outRecDesc = recordDescriptors[0];

        final int frameSize = ctx.getFrameSize();

        return new AbstractUnaryInputUnaryOutputOperatorNodePushable() {

            private IPushBasedGrouper grouper = null;

            @Override
            public void open() throws HyracksDataException {
                switch (algorithm) {
                    case SORT_GROUP:
                        grouper = new SortGrouper(ctx, keyFields, decorFields, frameSize, firstNormalizerFactory,
                                comparatorFactories, aggregatorFactory, inRecDesc, outRecDesc);
                        break;
                    case SORT_GROUP_MERGE_GROUP:
                    default:
                        grouper = new SortGroupMergeGrouper(ctx, keyFields, decorFields, framesLimit,
                                firstNormalizerFactory, comparatorFactories, aggregatorFactory, partialMergerFactory,
                                finalMergerFactory, inRecDesc, outRecDesc);
                        break;

                }
                grouper.init();
                writer.open();
            }

            @Override
            public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
                if (!grouper.nextFrame(buffer)) {
                    switch (algorithm) {
                        case SORT_GROUP:
                            grouper.flush(writer);
                            grouper.reset();
                            if (!grouper.nextFrame(buffer)) {
                                throw new HyracksDataException("Failed to aggregate a tuple using SortGrouper.");
                            }
                            break;
                        case SORT_GROUP_MERGE_GROUP:
                        default:
                            throw new HyracksDataException("Failed to aggregate a tuple using " + algorithm.name()
                                    + ": nextFrame() should never return false for this algorithm.");

                    }
                }
            }

            @Override
            public void fail() throws HyracksDataException {
                // TODO Auto-generated method stub

            }

            @Override
            public void close() throws HyracksDataException {
                // process before the close
                switch (algorithm) {
                    case SORT_GROUP_MERGE_GROUP:
                        grouper.flush(writer);
                        break;
                    case SORT_GROUP:
                        if (((SortGrouper) grouper).getFrameCount() > 0) {
                            grouper.flush(writer);
                        }
                        break;
                    default:
                        break;
                }
                grouper.close();
                writer.close();
            }

        };
    }
}
