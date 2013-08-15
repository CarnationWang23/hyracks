package edu.uci.ics.genomix.pregelix.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;

import edu.uci.ics.genomix.type.NodeWritable;
import edu.uci.ics.genomix.type.VKmerBytesWritable;

public class BubbleMergeMessageWritable extends MessageWritable{

    private VKmerBytesWritable majorVertexId; //use for MergeBubble
    private NodeWritable node; //except kmer, other field should be updated when MergeBubble
    
    public BubbleMergeMessageWritable(){
        super();
        majorVertexId = new VKmerBytesWritable();
        node = new NodeWritable();
    }
    
    public void set(BubbleMergeMessageWritable msg){
        this.setSourceVertexId(msg.getSourceVertexId());
        this.setFlag(msg.getFlag());
        this.setMajorVertexId(msg.getMajorVertexId());
        this.setNode(msg.node);
    }
    
    public void reset(){
        super.reset();
        majorVertexId.reset(0);
        node.reset();
    }
    
    public VKmerBytesWritable getMajorVertexId() {
        return majorVertexId;
    }

    public void setMajorVertexId(VKmerBytesWritable majorVertexId) {
        this.majorVertexId.setAsCopy(majorVertexId);
    }
    
    public NodeWritable getNode() {
        return node;
    }

    public void setNode(NodeWritable node) {
        this.node = node;
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        reset();
        super.readFields(in);
        majorVertexId.readFields(in);
        node.readFields(in);
    }
    
    @Override
    public void write(DataOutput out) throws IOException {
        super.write(out);
        majorVertexId.write(out);
        node.write(out);
    }
    
    public static class SortByCoverage implements Comparator<BubbleMergeMessageWritable> {
        @Override
        public int compare(BubbleMergeMessageWritable left, BubbleMergeMessageWritable right) {
            return Float.compare(left.node.getAverageCoverage(), right.node.getAverageCoverage());
        }
    }
}