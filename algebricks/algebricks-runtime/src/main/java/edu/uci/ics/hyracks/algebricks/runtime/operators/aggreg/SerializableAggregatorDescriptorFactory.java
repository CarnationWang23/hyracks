/*
 * Copyright 2009-2013 by The Regents of the University of California
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
package edu.uci.ics.hyracks.algebricks.runtime.operators.aggreg;

import java.io.DataOutput;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopySerializableAggregateFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopySerializableAggregateFunctionFactory;
import edu.uci.ics.hyracks.api.comm.IFrameTupleAccessor;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.FrameTupleReference;
import edu.uci.ics.hyracks.dataflow.std.group.AggregateState;
import edu.uci.ics.hyracks.dataflow.std.group.IAggregatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.IAggregatorDescriptorFactory;

public class SerializableAggregatorDescriptorFactory implements IAggregatorDescriptorFactory {
    private static final long serialVersionUID = 1L;
    private ICopySerializableAggregateFunctionFactory[] aggFactories;

    public SerializableAggregatorDescriptorFactory(ICopySerializableAggregateFunctionFactory[] aggFactories) {
        this.aggFactories = aggFactories;
    }

    @Override
    public IAggregatorDescriptor createAggregator(IHyracksTaskContext ctx, RecordDescriptor inRecordDescriptor,
            final RecordDescriptor outRecordDescriptor, int[] keyFields, final int[] keyFieldsInPartialResults)
            throws HyracksDataException {
        final int[] keys = keyFields;

        /**
         * one IAggregatorDescriptor instance per Gby operator
         */
        return new IAggregatorDescriptor() {
            private FrameTupleReference ftr = new FrameTupleReference();
            private ICopySerializableAggregateFunction[] aggs = new ICopySerializableAggregateFunction[aggFactories.length];
            private int offsetFieldIndex = keys.length;
            private int stateFieldLength[] = new int[aggFactories.length];

            @Override
            public AggregateState createAggregateStates() {
                return new AggregateState();
            }

            @Override
            public void init(ArrayTupleBuilder tb, IFrameTupleAccessor accessor, int tIndex, AggregateState state)
                    throws HyracksDataException {
                DataOutput output = tb.getDataOutput();
                ftr.reset(accessor, tIndex);
                for (int i = 0; i < aggs.length; i++) {
                    try {
                        int begin = tb.getSize();
                        if (aggs[i] == null) {
                            aggs[i] = aggFactories[i].createAggregateFunction();
                        }
                        aggs[i].init(output);
                        tb.addFieldEndOffset();
                        stateFieldLength[i] = tb.getSize() - begin;
                    } catch (AlgebricksException e) {
                        throw new HyracksDataException(e);
                    }
                }

                // doing initial aggregate
                ftr.reset(accessor, tIndex);
                for (int i = 0; i < aggs.length; i++) {
                    try {
                        byte[] data = tb.getByteArray();
                        int prevFieldPos = i + keys.length - 1;
                        int start = prevFieldPos >= 0 ? tb.getFieldEndOffsets()[prevFieldPos] : 0;
                        aggs[i].step(ftr, data, start, stateFieldLength[i]);
                    } catch (AlgebricksException e) {
                        throw new HyracksDataException(e);
                    }
                }
            }

            @Override
            public void aggregate(IFrameTupleAccessor accessor, int tIndex, byte[] data, int offset, int length,
                    AggregateState state) throws HyracksDataException {
                ftr.reset(accessor, tIndex);
                int fieldSlotLength = outRecordDescriptor.getFieldCount() * 4;
                for (int i = 0; i < aggs.length; i++) {
                    try {
                        int stateFieldOffset = ((keys.length + i == 0) ? 0 : getInt(data, offset
                                + (keys.length + i - 1) * 4))
                                + offset + fieldSlotLength;
                        aggs[i].step(ftr, data, stateFieldOffset, stateFieldLength[i]);
                    } catch (AlgebricksException e) {
                        throw new HyracksDataException(e);
                    }
                }
            }

            private int getInt(byte[] data, int offset) {
                return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16)
                        | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
            }

            @Override
            public void outputPartialResult(ArrayTupleBuilder tb, IFrameTupleAccessor accessor, int tIndex,
                    AggregateState state) throws HyracksDataException {
                byte[] data = accessor.getBuffer().array();
                int startOffset = accessor.getTupleStartOffset(tIndex);
                int aggFieldOffset = accessor.getFieldStartOffset(tIndex, offsetFieldIndex);
                int refOffset = startOffset + accessor.getFieldSlotsLength() + aggFieldOffset;
                int start = refOffset;
                for (int i = 0; i < aggs.length; i++) {
                    try {
                        aggs[i].finishPartial(data, start, stateFieldLength[i], tb.getDataOutput());
                        start += stateFieldLength[i];
                        tb.addFieldEndOffset();
                    } catch (AlgebricksException e) {
                        throw new HyracksDataException(e);
                    }
                }
            }

            @Override
            public void outputFinalResult(ArrayTupleBuilder tb, IFrameTupleAccessor accessor, int tIndex,
                    AggregateState state) throws HyracksDataException {
                byte[] data = accessor.getBuffer().array();
                int startOffset = accessor.getTupleStartOffset(tIndex);
                int aggFieldOffset = accessor.getFieldStartOffset(tIndex, offsetFieldIndex);
                int refOffset = startOffset + accessor.getFieldSlotsLength() + aggFieldOffset;
                int start = refOffset;
                for (int i = 0; i < aggs.length; i++) {
                    try {
                        aggs[i].finish(data, start, stateFieldLength[i], tb.getDataOutput());
                        start += stateFieldLength[i];
                        tb.addFieldEndOffset();
                    } catch (AlgebricksException e) {
                        throw new HyracksDataException(e);
                    }
                }
            }

            @Override
            public void reset() {

            }

            @Override
            public void close() {
                reset();
            }

        };
    }
}
