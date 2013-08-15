package edu.uci.ics.genomix.hadoop.contrailgraphbuilding;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

import edu.uci.ics.genomix.type.EdgeListWritable;
import edu.uci.ics.genomix.type.EdgeWritable;
import edu.uci.ics.genomix.type.KmerBytesWritable;
import edu.uci.ics.genomix.type.NodeWritable.DirectionFlag;
import edu.uci.ics.genomix.type.VKmerBytesWritable;
import edu.uci.ics.genomix.type.NodeWritable;
import edu.uci.ics.genomix.type.PositionListWritable;
import edu.uci.ics.genomix.type.PositionWritable;

@SuppressWarnings("deprecation")
public class GenomixMapper extends MapReduceBase implements
    Mapper<LongWritable, Text, VKmerBytesWritable, NodeWritable>{
    
    public static enum KmerDir{
        FORWARD,
        REVERSE,
    }
    
    public static int KMER_SIZE;
    private VKmerBytesWritable preForwardKmer;
    private VKmerBytesWritable preReverseKmer;
    private VKmerBytesWritable curForwardKmer;
    private VKmerBytesWritable curReverseKmer;
    private VKmerBytesWritable nextForwardKmer;
    private VKmerBytesWritable nextReverseKmer;
    private PositionWritable nodeId;
    private PositionListWritable nodeIdList;
    private EdgeWritable edgeForPreKmer;
    private EdgeWritable edgeForNextKmer;
    private EdgeListWritable edgeListForPreKmer;
    private EdgeListWritable edgeListForNextKmer;
    private NodeWritable outputNode;
    
    private KmerDir preKmerDir;
    private KmerDir curKmerDir;
    private KmerDir nextKmerDir;
    
    byte mateId = (byte)0;
    
    @Override
    public void configure(JobConf job) {
        KMER_SIZE = Integer.parseInt(job.get("sizeKmer"));
        KmerBytesWritable.setGlobalKmerLength(KMER_SIZE);
        preForwardKmer = new VKmerBytesWritable();
        preReverseKmer = new VKmerBytesWritable();
        curForwardKmer = new VKmerBytesWritable();
        curReverseKmer = new VKmerBytesWritable();
        nextForwardKmer = new VKmerBytesWritable();
        nextReverseKmer = new VKmerBytesWritable();
        nodeId = new PositionWritable();
        nodeIdList = new PositionListWritable();
        edgeForPreKmer = new EdgeWritable();
        edgeForNextKmer = new EdgeWritable();
        edgeListForPreKmer = new EdgeListWritable();
        edgeListForNextKmer = new EdgeListWritable();
        outputNode = new NodeWritable();
        preKmerDir = KmerDir.FORWARD;
        curKmerDir = KmerDir.FORWARD;
        nextKmerDir = KmerDir.FORWARD;
        
        // paired-end reads should be named something like dsm3757.01-31-2011.ln6_1.fastq
        // when we have a proper driver, we will set a config field instead of reading in the filename
        String filename = job.get("map.input.file");
        if (filename.endsWith("_2")) {
            mateId = (byte) 1;
        } else {
            mateId = (byte) 0;
        }
    }
    
    @Override
    public void map(LongWritable key, Text value, OutputCollector<VKmerBytesWritable, NodeWritable> output,
            Reporter reporter) throws IOException {
        String[] rawLine = value.toString().split("\\t"); // Read the Real Gene Line
        if (rawLine.length != 2) {
            throw new IOException("invalid data");
        }
        int readID = 0;
        readID = Integer.parseInt(rawLine[0]);
        String geneLine = rawLine[1];
        Pattern genePattern = Pattern.compile("[AGCT]+");
        Matcher geneMatcher = genePattern.matcher(geneLine);
        boolean isValid = geneMatcher.matches();
        if (isValid == true) {
            byte[] array = geneLine.getBytes();
            if (KMER_SIZE >= array.length) {
                throw new IOException("short read");
            }
            /** first kmer **/
            outputNode.reset();
            curForwardKmer.setByRead(KMER_SIZE, array, 0);
            curReverseKmer.setByReadReverse(KMER_SIZE, array, 0);
            curKmerDir = curForwardKmer.compareTo(curReverseKmer) <= 0 ? KmerDir.FORWARD : KmerDir.REVERSE;
            setNextKmer(array[KMER_SIZE]);
            //set nodeId
            setNodeId(mateId, readID, 0);
            //set value.edgeList and value.threads/readId
            setEdgeListForNextKmer();
            //set value.coverage = 1
            outputNode.setAvgCoverage(1);
            //set value.startReads because this is the first kmer in read
            if(curKmerDir == KmerDir.FORWARD)
                outputNode.setStartReads(nodeIdList);
            else 
                outputNode.setEndReads(nodeIdList);
            //output mapper result
            setMapperOutput(output);
            
            /** middle kmer **/
            for (int i = KMER_SIZE + 1; i < array.length; i++) {
                outputNode.reset();
            	setPreKmerByOldCurKmer();
            	setCurKmerByOldNextKmer();
            	setNextKmer(array[i]);
        	    //set nodeId
            	setNodeId(mateId, readID, i - KMER_SIZE + 1);
                //set value.edgeList and value.threads/readId
                setEdgeListForPreKmer();
                setEdgeListForNextKmer();
                //set coverage = 1
                outputNode.setAvgCoverage(1);
                //output mapper result
                setMapperOutput(output);
            }
            
            /** last kmer **/
            outputNode.reset();
        	setPreKmerByOldCurKmer();
        	setCurKmerByOldNextKmer();
        	//set nodeId
        	setNodeId(mateId, readID, array.length - KMER_SIZE + 1);
            //set value.edgeList and value.threads/readId
            setEdgeListForPreKmer();
            //set coverage = 1
            outputNode.setAvgCoverage(1);
            //output mapper result
            setMapperOutput(output);
        }
    }
    
    public void setNodeId(byte mateId, long readID, int posId){
        nodeId.set(mateId, readID, posId);
        nodeIdList.reset();
        nodeIdList.append(nodeId);
    }
    
    public void setEdgeListForPreKmer(){
    	switch(curKmerDir){
    		case FORWARD:
    			switch(preKmerDir){
    				case FORWARD:    				    
    				    edgeListForPreKmer.reset();
    				    edgeForPreKmer.setKey(preForwardKmer);
    				    edgeForPreKmer.setReadIDs(nodeIdList);
    				    edgeListForPreKmer.add(edgeForPreKmer);
    				    outputNode.setEdgeList(DirectionFlag.DIR_RR, edgeListForPreKmer);
    					break;
    				case REVERSE:
                        edgeListForPreKmer.reset();
                        edgeForPreKmer.setKey(preReverseKmer);
                        edgeForPreKmer.setReadIDs(nodeIdList);
                        edgeListForPreKmer.add(edgeForPreKmer);
    				    outputNode.setEdgeList(DirectionFlag.DIR_RF, edgeListForPreKmer);
    					break;
    			}
    			break;
    		case REVERSE:
    			switch(preKmerDir){
    				case FORWARD:
    				    edgeListForPreKmer.reset();
                        edgeForPreKmer.setKey(preForwardKmer);
                        edgeForPreKmer.setReadIDs(nodeIdList);
                        edgeListForPreKmer.add(edgeForPreKmer);
    				    outputNode.setEdgeList(DirectionFlag.DIR_FR, edgeListForPreKmer);
    					break;
    				case REVERSE:
    				    edgeListForPreKmer.reset();
                        edgeForPreKmer.setKey(preReverseKmer);
                        edgeForPreKmer.setReadIDs(nodeIdList);
                        edgeListForPreKmer.add(edgeForPreKmer);
    				    outputNode.setEdgeList(DirectionFlag.DIR_FF, edgeListForPreKmer);
    					break;
    			}
    			break;
    	}
    }
    
    public void setEdgeListForNextKmer(){
    	switch(curKmerDir){
    		case FORWARD:
    			switch(nextKmerDir){
    				case FORWARD:
    				    edgeListForNextKmer.reset();
    				    edgeForNextKmer.setKey(nextForwardKmer);
    				    edgeForNextKmer.setReadIDs(nodeIdList);
    				    edgeListForNextKmer.add(edgeForNextKmer);
    					outputNode.setEdgeList(DirectionFlag.DIR_FF, edgeListForNextKmer);
    					break;
    				case REVERSE:
    				    edgeListForNextKmer.reset();
                        edgeForNextKmer.setKey(nextReverseKmer);
                        edgeForNextKmer.setReadIDs(nodeIdList);
                        edgeListForNextKmer.add(edgeForNextKmer);
    					outputNode.setEdgeList(DirectionFlag.DIR_FR, edgeListForNextKmer);
    					break;
    			}
    			break;
    		case REVERSE:
    			switch(nextKmerDir){
    				case FORWARD:
    				    edgeListForNextKmer.reset();
                        edgeForNextKmer.setKey(nextForwardKmer);
                        edgeForNextKmer.setReadIDs(nodeIdList);
                        edgeListForNextKmer.add(edgeForNextKmer);
    				    new EdgeListWritable(Arrays.asList(new EdgeWritable(nextForwardKmer, nodeIdList)));
    					outputNode.setEdgeList(DirectionFlag.DIR_RF, edgeListForNextKmer);
    					break;
    				case REVERSE:
    				    edgeListForNextKmer.reset();
                        edgeForNextKmer.setKey(nextReverseKmer);
                        edgeForNextKmer.setReadIDs(nodeIdList);
                        edgeListForNextKmer.add(edgeForNextKmer);
    					outputNode.setEdgeList(DirectionFlag.DIR_RR, edgeListForNextKmer);
    					break;
    			}
    			break;
    	}
    }
    
    //set preKmer by shifting curKmer with preChar
    public void setPreKmer(byte preChar){
        preForwardKmer.setAsCopy(curForwardKmer);
        preForwardKmer.shiftKmerWithPreChar(preChar);
        preReverseKmer.setByReadReverse(KMER_SIZE, preForwardKmer.toString().getBytes(), preForwardKmer.getBlockOffset());
        preKmerDir = preForwardKmer.compareTo(preReverseKmer) <= 0 ? KmerDir.FORWARD : KmerDir.REVERSE;
    }
    
    //set nextKmer by shifting curKmer with nextChar
    public void setNextKmer(byte nextChar){
        nextForwardKmer.setAsCopy(curForwardKmer);
        nextForwardKmer.shiftKmerWithNextChar(nextChar);
        nextReverseKmer.setByReadReverse(KMER_SIZE, nextForwardKmer.toString().getBytes(), nextForwardKmer.getBlockOffset());
        nextKmerDir = nextForwardKmer.compareTo(nextReverseKmer) <= 0 ? KmerDir.FORWARD : KmerDir.REVERSE;
    }
    
    //old curKmer becomes current preKmer
    public void setPreKmerByOldCurKmer(){
    	preKmerDir = curKmerDir;
    	preForwardKmer.setAsCopy(curForwardKmer);
    	preReverseKmer.setAsCopy(curReverseKmer);
    }
    
    //old nextKmer becomes current curKmer
    public void setCurKmerByOldNextKmer(){
    	curKmerDir = nextKmerDir;
    	curForwardKmer.setAsCopy(nextForwardKmer);
    	curReverseKmer.setAsCopy(nextReverseKmer);
    }
    
    public void setMapperOutput(OutputCollector<VKmerBytesWritable, NodeWritable> output) throws IOException{
    	switch(curKmerDir){
    	case FORWARD:
    		output.collect(curForwardKmer, outputNode);
    		break;
    	case REVERSE:
    		output.collect(curReverseKmer, outputNode);
    		break;
    }
   }
}