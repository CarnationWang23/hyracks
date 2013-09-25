package edu.uci.ics.genomix.pregelix.operator.tipremove;

import java.util.Iterator;
import java.util.logging.Logger;

import edu.uci.ics.genomix.pregelix.client.Client;
import edu.uci.ics.genomix.pregelix.io.VertexValueWritable;
import edu.uci.ics.genomix.pregelix.io.message.MessageWritable;
import edu.uci.ics.genomix.pregelix.operator.BasicGraphCleanVertex;
import edu.uci.ics.genomix.pregelix.operator.aggregator.StatisticsAggregator;
import edu.uci.ics.genomix.pregelix.operator.pathmerge.P4ForPathMergeVertex;
import edu.uci.ics.genomix.pregelix.type.StatisticsCounter;
import edu.uci.ics.genomix.type.NodeWritable.EDGETYPE;
import edu.uci.ics.genomix.type.VKmerBytesWritable;
import edu.uci.ics.genomix.type.NodeWritable.DIR;

/**
 * Remove tip or single node when l > constant
 * @author anbangx
 *
 */
public class TipRemoveVertex extends
        BasicGraphCleanVertex<VertexValueWritable, MessageWritable> {
    
    private static final Logger LOG = Logger.getLogger(TipRemoveVertex.class.getName());
    private int length = -1;
    
    /**
     * initiate kmerSize, length
     */
    @Override
    public void initVertex() {
        super.initVertex();
        if(incomingMsg == null)
            incomingMsg = new MessageWritable();
        if(outgoingMsg == null)
            outgoingMsg = new MessageWritable();
        else
            outgoingMsg.reset();
        if(destVertexId == null)
            destVertexId = new VKmerBytesWritable();
        if(getSuperstep() == 1)
            StatisticsAggregator.preGlobalCounters.clear();
//        else
//            StatisticsAggregator.preGlobalCounters = BasicGraphCleanVertex.readStatisticsCounterResult(getContext().getConfiguration());
        counters.clear();
        getVertexValue().getCounters().clear();
    }
    
    /**
     * detect the tip and figure out what edgeType neighborToTip is
     */
    public EDGETYPE getTipEdgetype(){
        VertexValueWritable vertex = getVertexValue();
        if(vertex.getDegree(DIR.PREVIOUS) == 0 && vertex.getDegree(DIR.NEXT) == 1){ //INCOMING TIP
            return vertex.getEdgetypeFromDir(DIR.NEXT);
        } else if(vertex.getDegree(DIR.PREVIOUS) == 1 && vertex.getDegree(DIR.NEXT) == 0){ //OUTGOING TIP
            return vertex.getEdgetypeFromDir(DIR.PREVIOUS);
        } else
            return null;
    }
    
    /**
     * step1
     */
    public void detectTip(){
        EDGETYPE neighborToTipEdgetype = getTipEdgetype();
        //I'm tip and my length is less than the minimum
        if(neighborToTipEdgetype != null && getVertexValue().getKmerLength() <= length){ 
            EDGETYPE tipToNeighborEdgetype = neighborToTipEdgetype.mirror();
            outgoingMsg.setFlag(tipToNeighborEdgetype.get());
            outgoingMsg.setSourceVertexId(getVertexId());
            destVertexId = getDestVertexId(neighborToTipEdgetype.dir());
            sendMsg(destVertexId, outgoingMsg);
            deleteVertex(getVertexId());
            
            if(verbose){
                LOG.fine("I'm tip! " + "\r\n"
                		+ "My vertexId is " + getVertexId() + "\r\n"
                        + "My vertexValue is " + getVertexValue() + "\r\n"
                        + "Kill self and broadcast kill self to " + destVertexId + "\r\n"
                        + "The message is: " + outgoingMsg + "\r\n\n");
            }
            //set statistics counter: Num_RemovedTips
            updateStatisticsCounter(StatisticsCounter.Num_RemovedTips);
            getVertexValue().setCounters(counters);
        }
    }
    
    /**
     * step2
     */
    public void responseToDeadTip(Iterator<MessageWritable> msgIterator){
        if(verbose){
            LOG.fine("Before update " + "\r\n"
                    + "My vertexId is " + getVertexId() + "\r\n"
                    + "My vertexValue is " + getVertexValue() + "\r\n\n");
        }
        while(msgIterator.hasNext()){
            incomingMsg = msgIterator.next();
            EDGETYPE tipToMeEdgetype = EDGETYPE.fromByte(incomingMsg.getFlag());
            getVertexValue().getEdgeList(tipToMeEdgetype).remove(incomingMsg.getSourceVertexId());
            
            if(verbose){
                LOG.fine("Receive message from tip!" + incomingMsg.getSourceVertexId() + "\r\n"
                        + "The tipToMeEdgetype in message is: " + tipToMeEdgetype + "\r\n\n");
            }
        }
        if(verbose){
            LOG.fine("After update " + "\r\n"
                    + "My vertexId is " + getVertexId() + "\r\n"
                    + "My vertexValue is " + getVertexValue() + "\r\n\n");
        }
    }
    
    @Override
    public void compute(Iterator<MessageWritable> msgIterator) {
        initVertex(); 
        if(getSuperstep() == 1)
            detectTip();
        else if(getSuperstep() == 2)
            responseToDeadTip(msgIterator);
        voteToHalt();
    }

    public static void main(String[] args) throws Exception {
        Client.run(args, getConfiguredJob(null, TipRemoveVertex.class));
    }
}
