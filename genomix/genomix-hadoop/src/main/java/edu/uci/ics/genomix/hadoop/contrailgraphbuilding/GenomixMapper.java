package edu.uci.ics.genomix.hadoop.contrailgraphbuilding;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

import edu.uci.ics.genomix.type.KmerBytesWritable;
import edu.uci.ics.genomix.type.VKmerListWritable;
import edu.uci.ics.genomix.type.NodeWritable;
import edu.uci.ics.genomix.type.PositionListWritable;
import edu.uci.ics.genomix.type.PositionWritable;
import edu.uci.ics.genomix.type.VKmerBytesWritable;

@SuppressWarnings("deprecation")
public class GenomixMapper extends MapReduceBase implements
    Mapper<LongWritable, Text, KmerBytesWritable, NodeWritable>{
    
    public static enum KmerDir{
        FORWARD,
        REVERSE,
    }
    
    public static int KMER_SIZE;
    private KmerBytesWritable preForwardKmer;
    private KmerBytesWritable preReverseKmer;
    private KmerBytesWritable curForwardKmer;
    private KmerBytesWritable curReverseKmer;
    private KmerBytesWritable nextForwardKmer;
    private KmerBytesWritable nextReverseKmer;
    private PositionWritable nodeId;
    private PositionListWritable nodeIdList;
    private VKmerListWritable edgeListForPreKmer;
    private VKmerListWritable edgeListForNextKmer;
    private NodeWritable outputNode;
    
    private KmerDir preKmerDir;
    private KmerDir curKmerDir;
    private KmerDir nextKmerDir;
    
    byte mateId = (byte)0;
    
    @Override
    public void configure(JobConf job) {
        KMER_SIZE = Integer.parseInt(job.get("sizeKmer"));
        KmerBytesWritable.setGlobalKmerLength(KMER_SIZE);
        preForwardKmer = new KmerBytesWritable();
        preReverseKmer = new KmerBytesWritable();
        curForwardKmer = new KmerBytesWritable();
        curReverseKmer = new KmerBytesWritable();
        nextForwardKmer = new KmerBytesWritable();
        nextReverseKmer = new KmerBytesWritable();
        nodeId = new PositionWritable();
        nodeIdList = new PositionListWritable();
        edgeListForPreKmer = new VKmerListWritable();
        edgeListForNextKmer = new VKmerListWritable();
        outputNode = new NodeWritable();
        preKmerDir = KmerDir.FORWARD;
        curKmerDir = KmerDir.FORWARD;
        nextKmerDir = KmerDir.FORWARD;
    }
    
    @Override
    public void map(LongWritable key, Text value, OutputCollector<KmerBytesWritable, NodeWritable> output,
            Reporter reporter) throws IOException {
        /** first kmer */
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
            curForwardKmer.setByRead(array, 0);
            curReverseKmer.setByReadReverse(array, 0);
            curKmerDir = curForwardKmer.compareTo(curReverseKmer) <= 0 ? KmerDir.FORWARD : KmerDir.REVERSE;
            setNextKmer(array[KMER_SIZE]);
            //set value.nodeId
            setNodeId(mateId, readID, 1);
            //set value.edgeList
            setEdgeListForNextKmer();
            //output mapper result
            setMapperOutput(output);
            
            /** middle kmer **/
            for (int i = KMER_SIZE + 1; i < array.length; i++) {
                outputNode.reset();
            	setPreKmerByOldCurKmer();
            	setCurKmerByOldNextKmer();
            	setNextKmer(array[i]);
        	    //set value.nodeId
            	setNodeId(mateId, readID, i - KMER_SIZE + 1);
                //set value.edgeList
                setEdgeListForPreKmer();
                setEdgeListForNextKmer();
                //output mapper result
                setMapperOutput(output);
            }
            
            /** last kmer **/
            outputNode.reset();
        	setPreKmerByOldCurKmer();
        	setCurKmerByOldNextKmer();
        	//set value.nodeId
        	setNodeId(mateId, readID, array.length - KMER_SIZE + 1);
            //set value.edgeList
            setEdgeListForPreKmer();
            //output mapper result
            setMapperOutput(output);
        }
    }
    
    public void setNodeId(byte mateId, long readID, int posId){
        nodeId.set(mateId, readID, posId);
        nodeIdList.reset();
        nodeIdList.append(nodeId);
        outputNode.setNodeIdList(nodeIdList);
    }
    
    public void setEdgeListForPreKmer(){
    	switch(curKmerDir){
    		case FORWARD:
    			switch(preKmerDir){
    				case FORWARD:
    				    edgeListForPreKmer.reset();
    				    edgeListForPreKmer.append(preForwardKmer);
    					outputNode.setRRList(edgeListForPreKmer);
    					break;
    				case REVERSE:
    				    edgeListForPreKmer.reset();
    				    edgeListForPreKmer.append(preReverseKmer);
    					outputNode.setRFList(edgeListForPreKmer);
    					break;
    			}
    			break;
    		case REVERSE:
    			switch(preKmerDir){
    				case FORWARD:
    				    edgeListForPreKmer.reset();
    				    edgeListForPreKmer.append(preForwardKmer);
    					outputNode.setFRList(edgeListForPreKmer);
    					break;
    				case REVERSE:
    				    edgeListForPreKmer.reset();
    				    edgeListForPreKmer.append(preReverseKmer);
    					outputNode.setFFList(edgeListForPreKmer);
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
    					edgeListForNextKmer.append(nextForwardKmer);
    					outputNode.setFFList(edgeListForNextKmer);
    					break;
    				case REVERSE:
    					edgeListForNextKmer.reset();
    					edgeListForNextKmer.append(nextReverseKmer);
    					outputNode.setFRList(edgeListForNextKmer);
    					break;
    			}
    			break;
    		case REVERSE:
    			switch(nextKmerDir){
    				case FORWARD:
    					edgeListForNextKmer.reset();
    					edgeListForNextKmer.append(nextForwardKmer);
    					outputNode.setRFList(edgeListForNextKmer);
    					break;
    				case REVERSE:
    					edgeListForNextKmer.reset();
    					edgeListForNextKmer.append(nextReverseKmer);
    					outputNode.setRRList(edgeListForNextKmer);
    					break;
    			}
    			break;
    	}
    }
    
    //set preKmer by shifting curKmer with preChar
    public void setPreKmer(byte preChar){
        preForwardKmer.setAsCopy(curForwardKmer);
        preForwardKmer.shiftKmerWithPreChar(preChar);
        preReverseKmer.setByReadReverse(preForwardKmer.toString().getBytes(), preForwardKmer.getOffset());
        preKmerDir = preForwardKmer.compareTo(preReverseKmer) <= 0 ? KmerDir.FORWARD : KmerDir.REVERSE;
    }
    
    //set nextKmer by shifting curKmer with nextChar
    public void setNextKmer(byte nextChar){
        nextForwardKmer.setAsCopy(curForwardKmer);
        nextForwardKmer.shiftKmerWithNextChar(nextChar);
        nextReverseKmer.setByReadReverse(nextForwardKmer.toString().getBytes(), nextForwardKmer.getOffset());
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
    
    public void setMapperOutput(OutputCollector<KmerBytesWritable, NodeWritable> output) throws IOException{
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