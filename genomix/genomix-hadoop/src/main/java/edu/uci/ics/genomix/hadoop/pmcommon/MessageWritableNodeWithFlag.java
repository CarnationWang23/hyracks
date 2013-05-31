package edu.uci.ics.genomix.hadoop.pmcommon;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.BinaryComparable;
import org.apache.hadoop.io.WritableComparable;

import edu.uci.ics.genomix.type.NodeWritable;

public class MessageWritableNodeWithFlag extends BinaryComparable implements WritableComparable<BinaryComparable> {
    private byte flag;
    private NodeWritable node;

    public MessageWritableNodeWithFlag(int k) {
        this.flag = 0;
        this.node = new NodeWritable(k);
    }

    public MessageWritableNodeWithFlag(byte flag, int kmerSize) {
        this.flag = flag;
        this.node = new NodeWritable(kmerSize);
    }

    public void set(MessageWritableNodeWithFlag right) {
        set(right.getFlag(), right.getNode());
    }

    public void set(byte flag, NodeWritable node) {
        this.node.set(node);
        this.flag = flag;
    }

    @Override
    public void readFields(DataInput arg0) throws IOException {
        node.readFields(arg0);
        flag = arg0.readByte();
    }

    @Override
    public void write(DataOutput arg0) throws IOException {
        node.write(arg0);
        arg0.writeByte(flag);
    }

    public NodeWritable getNode() {
        if (node.getCount() != 0) {
            return node;
        }
        return null;
    }

    public byte getFlag() {
        return this.flag;
    }

    public String toString() {
        return node.toString() + '\t' + String.valueOf(flag);
    }

    @Override
    public byte[] getBytes() {
        if (node.getCount() != 0) {
            return node.getKmer().getBytes();
        } else
            return null;
    }

    @Override
    public int getLength() {
        return node.getCount();
    }
}