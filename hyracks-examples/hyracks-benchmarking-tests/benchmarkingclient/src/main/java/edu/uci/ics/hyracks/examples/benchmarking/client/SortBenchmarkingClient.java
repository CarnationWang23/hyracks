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
package edu.uci.ics.hyracks.examples.benchmarking.client;

import java.io.File;
import java.util.UUID;
import java.util.regex.Pattern;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.uci.ics.hyracks.api.client.HyracksRMIConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.IConnectorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.UTF8StringBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.std.benchmarking.DataGeneratorOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.benchmarking.DummyInputSinkOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.benchmarking.IGenDistributionDescriptor;
import edu.uci.ics.hyracks.dataflow.std.benchmarking.ITypeGenerator;
import edu.uci.ics.hyracks.dataflow.std.benchmarking.RandomDistributionDescriptor;
import edu.uci.ics.hyracks.dataflow.std.benchmarking.UTF8StringGenerator;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.PlainFileWriterOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.sort.ExternalSortOperatorDescriptor;

/**
 * @author jarodwen
 */
public class SortBenchmarkingClient {

    private static class Options extends BenchmarkingCommonArguments {
        @Option(name = "-out-path", usage = "The prefix (including the path) of the output files")
        public String outPath = System.getProperty("java.io.tmpdir") + "/SortBenchmarking_output";

        @Option(name = "-data-only", usage = "Test overhead on data generating (so no sort or output)")
        public boolean isDataOnly = false;

        @Override
        public String getArgumentNames() {
            return super.getArgumentNames() + "outPath\t" + "dataOnly\t";
        }

        @Override
        public String getArgumentValues() {
            return super.getArgumentValues() + outPath + "\t" + isDataOnly + "\t";
        }
    }

    private static final Pattern splitPattern = Pattern.compile(",");

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        parser.parseArgument(args);

        IHyracksClientConnection hcc = new HyracksRMIConnection(options.host, options.port);

        JobSpecification job;

        System.out
                .println(options.getArgumentNames() + "\n" + options.getArgumentValues());

        String[] keys = splitPattern.split(options.keyFields);
        int[] keyFields = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            keyFields[i] = Integer.valueOf(keys[i]);
        }

        System.out.println("\tInitial\tRunning");
        for (int i = 0; i < options.testCount; i++) {
            long start = System.currentTimeMillis();
            job = createJob(options.dataSize, options.tupleLength, options.dataFields, options.cardRatio,
                    splitPattern.split(options.inNodeSplits), splitPattern.split(options.outNodeSplits),
                    options.frameLimit, options.outPath, keyFields, options.isDataOnly, options.randSeed,
                    options.repeatable);
            System.out.print(i + "\t" + (System.currentTimeMillis() - start));
            start = System.currentTimeMillis();
            UUID jobId = hcc.createJob(options.app, job);
            hcc.start(jobId);
            hcc.waitForCompletion(jobId);
            System.out.println("\t" + (System.currentTimeMillis() - start));
        }
    }

    private static JobSpecification createJob(int dataSize, int tupleLength, int dataFields, double cardRatio,
            String[] inNodes, String[] outNodes, int frameLimit, String outPath, int[] keyFields, boolean isDataOnly,
            int randSeed, boolean repeatable) throws Exception {
        JobSpecification spec = new JobSpecification();

        // Data Generator Operator
        @SuppressWarnings("rawtypes")
        ITypeGenerator[] dataTypeGenerators = new ITypeGenerator[dataFields];
        for (int i = 0; i < dataTypeGenerators.length; i++) {
            dataTypeGenerators[i] = new UTF8StringGenerator(tupleLength, true, randSeed + i);
        }

        IGenDistributionDescriptor[] dataDistributionDescriptors = new IGenDistributionDescriptor[dataFields];
        for (int i = 0; i < dataDistributionDescriptors.length; i++) {
            dataDistributionDescriptors[i] = new RandomDistributionDescriptor((int) (dataSize * cardRatio));
        }

        @SuppressWarnings("rawtypes")
        ISerializerDeserializer[] fields = new ISerializerDeserializer[dataFields];

        for (int i = 0; i < fields.length; i++) {
            fields[i] = UTF8StringSerializerDeserializer.INSTANCE;
        }

        RecordDescriptor inRecordDescriptor = new RecordDescriptor(fields);

        IBinaryComparatorFactory[] comparatorFactories = new IBinaryComparatorFactory[keyFields.length];
        for (int i = 0; i < keyFields.length; i++) {
            comparatorFactories[i] = UTF8StringBinaryComparatorFactory.INSTANCE;
        }

        DataGeneratorOperatorDescriptor generator = new DataGeneratorOperatorDescriptor(spec, dataTypeGenerators,
                dataDistributionDescriptors, dataSize, true, randSeed, repeatable);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, generator, inNodes);

        if (isDataOnly) {

            DummyInputSinkOperatorDescriptor sink = new DummyInputSinkOperatorDescriptor(spec);

            PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, sink, outNodes);

            IConnectorDescriptor conn = new OneToOneConnectorDescriptor(spec);
            spec.connect(conn, generator, 0, sink, 0);

            spec.addRoot(sink);

        } else {
            ExternalSortOperatorDescriptor sorter = new ExternalSortOperatorDescriptor(spec, frameLimit, keyFields,
                    comparatorFactories, inRecordDescriptor);

            PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, sorter, inNodes);

            IConnectorDescriptor conn1 = new OneToOneConnectorDescriptor(spec);
            spec.connect(conn1, generator, 0, sorter, 0);

            PlainFileWriterOperatorDescriptor printer = new PlainFileWriterOperatorDescriptor(spec,
                    new ConstantFileSplitProvider(parseFileSplits(outNodes, outPath)), "\t");
            PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, outNodes);

            IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
            spec.connect(conn2, sorter, 0, printer, 0);

            spec.addRoot(printer);
        }
        return spec;
    }

    private static FileSplit[] parseFileSplits(String[] outNodes, String outPath) {
        FileSplit[] fSplits = new FileSplit[outNodes.length];
        for (int i = 0; i < outNodes.length; ++i) {
            fSplits[i] = new FileSplit(outNodes[i], new FileReference(new File(outPath + "_" + outNodes[i] + ".txt")));
        }
        return fSplits;
    }

}
