package edu.uci.ics.genomix.pregelix.format;

import java.io.IOException;

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import edu.uci.ics.genomix.oldtype.NodeWritable;
import edu.uci.ics.genomix.oldtype.PositionWritable;
import edu.uci.ics.genomix.pregelix.api.io.binary.BinaryVertexOutputFormat;
import edu.uci.ics.pregelix.api.graph.Vertex;
import edu.uci.ics.pregelix.api.io.VertexWriter;
import edu.uci.ics.genomix.pregelix.io.VertexValueWritable;

public class LogAlgorithmForPathMergeOutputFormat extends
        BinaryVertexOutputFormat<PositionWritable, VertexValueWritable, NullWritable> {

    @Override
    public VertexWriter<PositionWritable, VertexValueWritable, NullWritable> createVertexWriter(
            TaskAttemptContext context) throws IOException, InterruptedException {
        @SuppressWarnings("unchecked")
        RecordWriter<NodeWritable, NullWritable> recordWriter = binaryOutputFormat.getRecordWriter(context);
        return new BinaryLoadGraphVertexWriter(recordWriter);
    }

    /**
     * Simple VertexWriter that supports {@link BinaryLoadGraphVertex}
     */
    public static class BinaryLoadGraphVertexWriter extends
            BinaryVertexWriter<PositionWritable, VertexValueWritable, NullWritable> {
        private NodeWritable node = new NodeWritable();
        private NullWritable nul = NullWritable.get();
        
        public BinaryLoadGraphVertexWriter(RecordWriter<NodeWritable, NullWritable> lineRecordWriter) {
            super(lineRecordWriter);
        }

        @Override
        public void writeVertex(Vertex<PositionWritable, VertexValueWritable, NullWritable, ?> vertex)
                throws IOException, InterruptedException {
            node.set(vertex.getVertexId(), vertex.getVertexValue().getFFList(),
                    vertex.getVertexValue().getFRList(), vertex.getVertexValue().getRFList(),
                    vertex.getVertexValue().getRRList(), vertex.getVertexValue().getKmer());
            getRecordWriter().write(node, nul);
        }
    }
}