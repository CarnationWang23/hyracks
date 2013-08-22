package edu.uci.ics.genomix.pregelix.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;

import edu.uci.ics.genomix.pregelix.type.MessageFlag;
import edu.uci.ics.genomix.type.NodeWritable;
import edu.uci.ics.genomix.type.VKmerBytesWritable;

public class BubbleMergeMessageWritable extends MessageWritable{

    public static class DirToMajor{
        public static final byte FORWARD = 0;
        public static final byte REVERSE = 1;
    }
    
    private VKmerBytesWritable majorVertexId; //use for MergeBubble
    private NodeWritable node; //except kmer, other field should be updated when MergeBubble
    private byte meToMajorDir;
    private byte meToMinorDir;
    private boolean isFlip;
    
    public BubbleMergeMessageWritable(){
        super();
        majorVertexId = new VKmerBytesWritable();
        node = new NodeWritable();
        meToMajorDir = 0;
        meToMinorDir = 0;
        isFlip = false;
    }
    
    public void set(BubbleMergeMessageWritable msg){
        this.setSourceVertexId(msg.getSourceVertexId());
        this.setFlag(msg.getFlag());
        this.setMajorVertexId(msg.getMajorVertexId());
        this.setNode(msg.node);
        this.setMeToMajorDir(msg.meToMajorDir);
        this.setMeToMinorDir(msg.meToMinorDir);
        this.setFlip(msg.isFlip());
    }
    
    public void reset(){
        super.reset();
        majorVertexId.reset(0);
        node.reset();
        meToMajorDir = 0;
        meToMinorDir = 0;
        isFlip = false;
    }
    
    public byte getRelativeDirToMajor(){
        switch(meToMajorDir){
            case MessageFlag.DIR_FF:
            case MessageFlag.DIR_RR:
                return DirToMajor.FORWARD;
            case MessageFlag.DIR_FR:
            case MessageFlag.DIR_RF:
                return DirToMajor.REVERSE;
        }
        return 0;
    }
    
    public VKmerBytesWritable getMajorVertexId() {
        return majorVertexId;
    }

    public void setMajorVertexId(VKmerBytesWritable majorVertexId) {
        this.majorVertexId.setAsCopy(majorVertexId);
    }
    
    public VKmerBytesWritable getTopCoverageVertexId() {
        return majorVertexId;
    }

    public void setTopCoverageVertexId(VKmerBytesWritable topCoverageVertexId) {
        this.majorVertexId.setAsCopy(topCoverageVertexId);
    }
    
    public NodeWritable getNode() {
        return node;
    }

    public void setNode(NodeWritable node) {
        this.node.setAsCopy(node);
    }
    
    public byte getMeToMajorDir() {
        return meToMajorDir;
    }

    public void setMeToMajorDir(byte meToMajorDir) {
        this.meToMajorDir = meToMajorDir;
    }

    public byte getMeToMinorDir() {
        return meToMinorDir;
    }

    public void setMeToMinorDir(byte meToMinorDir) {
        this.meToMinorDir = meToMinorDir;
    }
    
    public boolean isFlip() {
        return isFlip;
    }

    public void setFlip(boolean isFlip) {
        this.isFlip = isFlip;
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        reset();
        super.readFields(in);
        majorVertexId.readFields(in);
        node.readFields(in);
        meToMajorDir = in.readByte();
        meToMinorDir = in.readByte();
        isFlip = in.readBoolean();
    }
    
    @Override
    public void write(DataOutput out) throws IOException {
        super.write(out);
        majorVertexId.write(out);
        node.write(out);
        out.writeByte(meToMajorDir);
        out.write(meToMinorDir);
        out.writeBoolean(isFlip);
    }
    
    public static class SortByCoverage implements Comparator<BubbleMergeMessageWritable> {
        @Override
        public int compare(BubbleMergeMessageWritable left, BubbleMergeMessageWritable right) {
            return Float.compare(left.node.getAverageCoverage(), right.node.getAverageCoverage());
        }
    }
    
    public boolean isFlip(BubbleMergeMessageWritable other){
        return this.getRelativeDirToMajor() != other.getRelativeDirToMajor();
    }
    
    public float computeDissimilar(BubbleMergeMessageWritable other){
        if(isFlip(other)){
            String reverse = other.getNode().getInternalKmer().toString();
            VKmerBytesWritable reverseKmer = new VKmerBytesWritable();
            reverseKmer.setByReadReverse(reverse.length(), reverse.getBytes(), 0);
            return this.getNode().getInternalKmer().fracDissimilar(reverseKmer);
        } else
            return this.getNode().getInternalKmer().fracDissimilar(other.getNode().getInternalKmer());
    }
}