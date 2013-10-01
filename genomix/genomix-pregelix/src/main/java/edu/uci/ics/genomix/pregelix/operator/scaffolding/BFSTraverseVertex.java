package edu.uci.ics.genomix.pregelix.operator.scaffolding;

import java.util.Iterator;

import edu.uci.ics.genomix.pregelix.client.Client;
import edu.uci.ics.genomix.pregelix.io.VertexValueWritable;
import edu.uci.ics.genomix.pregelix.io.common.ArrayListWritable;
import edu.uci.ics.genomix.pregelix.io.message.BFSTraverseMessageWritable;
import edu.uci.ics.genomix.pregelix.operator.BasicGraphCleanVertex;
import edu.uci.ics.genomix.pregelix.operator.scaffolding.ScaffoldingVertex.SearchInfo;
import edu.uci.ics.genomix.pregelix.type.EdgeDirs;
import edu.uci.ics.genomix.pregelix.type.EdgeTypes;
import edu.uci.ics.genomix.type.NodeWritable.DIR;
import edu.uci.ics.genomix.type.VKmerBytesWritable;
import edu.uci.ics.genomix.type.VKmerListWritable;
import edu.uci.ics.genomix.type.NodeWritable.EDGETYPE;

public class BFSTraverseVertex extends
    BasicGraphCleanVertex<VertexValueWritable, BFSTraverseMessageWritable> {
    
//    protected VKmerBytesWritable srcNode = new VKmerBytesWritable("AAT");
//    protected VKmerBytesWritable destNode = new VKmerBytesWritable("AGA");
    protected long commonReadId = 2; 
    
//    private EdgeDirs edgeDirs =  new EdgeDirs();
//    private ArrayListWritable<EdgeDirs> edgeDirsList = new ArrayListWritable<EdgeDirs>();
//    protected VKmerListWritable kmerList = new VKmerListWritable();
    
    /**
     * initiate kmerSize, maxIteration
     */
    @Override
    public void initVertex() {
        super.initVertex();
        if(outgoingMsg == null)
            outgoingMsg = new BFSTraverseMessageWritable();
        else
            outgoingMsg.reset();
        if(fakeVertex == null){
            fakeVertex = new VKmerBytesWritable();
//            String random = generaterRandomString(kmerSize + 1);
//            fakeVertex.setByRead(kmerSize + 1, random.getBytes(), 0); 
        }
    }
    
    public VKmerBytesWritable initiateSrcAndDestNode(long readId, ArrayListWritable<SearchInfo> searchInfoList){
        VKmerBytesWritable srcNode = searchInfoList.get(0).getKmer();
        outgoingMsg.setSrcFlip(searchInfoList.get(0).isFlip());
        VKmerBytesWritable destNode = searchInfoList.get(1).getKmer();
        outgoingMsg.setDestFlip(searchInfoList.get(1).isFlip());
        outgoingMsg.setReadId(readId); // commonReadId
        outgoingMsg.setSeekedVertexId(destNode);
        
        return srcNode;
    }
    
//    public void initiateSrcAndDestNode(VKmerListWritable pairKmerList, long readId, boolean srcFlip, 
//            boolean destFlip){
//        srcNode.setAsCopy(pairKmerList.getPosition(0));
//        destNode.setAsCopy(pairKmerList.getPosition(1));
//        outgoingMsg.setSrcFlip(srcFlip);
//        outgoingMsg.setDestFlip(destFlip);
//        outgoingMsg.setReadId(readId);
//        outgoingMsg.setSeekedVertexId(destNode);
//    }
    
    public void broadcaseBFSTraverse(BFSTraverseMessageWritable incomingMsg){
        outgoingMsg.reset();
        outgoingMsg.setSourceVertexId(getVertexId());
        outgoingMsg.setSeekedVertexId(incomingMsg.getSeekedVertexId());
        outgoingMsg.setSrcFlip(incomingMsg.isSrcFlip());
        outgoingMsg.setDestFlip(incomingMsg.isDestFlip());
        
        VKmerListWritable kmerList = outgoingMsg.getPathList();
        kmerList.append(getVertexId());
        outgoingMsg.setReadId(incomingMsg.getReadId()); //only one readId
        
        // A -> B -> C, neighor: A, me: B, validDir: B -> C 
        if(getSuperstep() > 3){
            EDGETYPE meToNeighbor = EDGETYPE.fromByte(incomingMsg.getFlag());
            DIR validDir = meToNeighbor.dir().mirror();
            // set EdgeTypes
            ArrayListWritable<EdgeTypes> edgeTypesList = incomingMsg.getEdgeTypesList();
            // send msg to valid destination
            sendSettledMsgs(validDir, getVertexValue());
        }
    }
    
//    public void broadcaseBFSTraverse(BFSTraverseMessageWritable incomingMsg){
//        outgoingMsg.reset();
//        outgoingMsg.setSourceVertexId(incomingMsg.getSourceVertexId());
//        outgoingMsg.setSeekedVertexId(incomingMsg.getSeekedVertexId());
//        outgoingMsg.setSrcFlip(incomingMsg.isSrcFlip());
//        outgoingMsg.setDestFlip(incomingMsg.isDestFlip());
//        kmerList.setCopy(incomingMsg.getPathList());
//        kmerList.append(getVertexId());
//        outgoingMsg.setPathList(kmerList);
//        outgoingMsg.setReadId(incomingMsg.getReadId()); //only one readId
//        EDGETYPE meToNeighborDir = EDGETYPE.fromByte(incomingMsg.getFlag());
//        EDGETYPE neighborToMeDir = meToNeighborDir.mirror(); 
//        /** set edgeDirs **/
//        setEdgeDirs(incomingMsg, meToNeighborDir, neighborToMeDir);
//        switch(neighborToMeDir){
//            case FF:
//            case FR:
////                sendSettledMsgs(DIR.REVERSE ,getVertexValue());
//                break;
//            case RF:
//            case RR:
////                sendSettledMsgs(DIR.FORWARD, getVertexValue());
//                break;
//        }
//    }
    
    public void setEdgeDirs(BFSTraverseMessageWritable incomingMsg, EDGETYPE meToNeighborDir, EDGETYPE neighborToMeDir){
        edgeDirsList.clear();
        edgeDirsList.addAll(incomingMsg.getEdgeTypesList());
        if(edgeDirsList.isEmpty()){ //first time from srcNode
            /** set srcNode's next dir **/
            edgeDirs.reset();
            edgeDirs.setNextToMeDir(meToNeighborDir.get());
            edgeDirsList.add(new EdgeDirs(edgeDirs)); 
            /** set curNode's prev dir **/
            edgeDirs.reset();
            edgeDirs.setPrevToMeDir(neighborToMeDir.get());
            edgeDirsList.add(new EdgeDirs(edgeDirs));
        } else {
            /** set preNode's next dir **/
            edgeDirs.set(edgeDirsList.get(edgeDirsList.size() - 1));
            edgeDirs.setNextToMeDir(meToNeighborDir.get());
            edgeDirsList.set(edgeDirsList.size() - 1, new EdgeDirs(edgeDirs));
            /** set curNode's prev dir **/
            edgeDirs.reset();
            edgeDirs.setPrevToMeDir(neighborToMeDir.get());
            edgeDirsList.add(new EdgeDirs(edgeDirs));
        }
        outgoingMsg.setEdgeTypesList(edgeDirsList);
    }
    
    public boolean isValidDestination(BFSTraverseMessageWritable incomingMsg){
        EDGETYPE meToNeighbor = EDGETYPE.fromByte(incomingMsg.getFlag());
        if(incomingMsg.isDestFlip())
            return meToNeighbor.dir() == DIR.REVERSE;
        else
            return meToNeighbor.dir() == DIR.FORWARD;
    }
    
    public void sendMsgToPathNodeToAddCommondReadId(BFSTraverseMessageWritable incomingMsg){
        outgoingMsg.reset();
        outgoingMsg.setTraverseMsg(false);
        outgoingMsg.setReadId(incomingMsg.getReadId());
        int count = kmerList.getCountOfPosition();
        for(int i = 0; i < count; i++){
            outgoingMsg.getEdgeTypesList().clear();
            outgoingMsg.getEdgeTypesList().add(incomingMsg.getEdgeTypesList().get(i));
            outgoingMsg.getPathList().reset();
            if(i == 0){
                outgoingMsg.getPathList().append(new VKmerBytesWritable());
                outgoingMsg.getPathList().append(kmerList.getPosition(i + 1));
            } else if(i == count - 1){
                outgoingMsg.getPathList().append(kmerList.getPosition(i - 1));
                outgoingMsg.getPathList().append(new VKmerBytesWritable());
            } else{
                outgoingMsg.getPathList().append(kmerList.getPosition(i - 1));
                outgoingMsg.getPathList().append(kmerList.getPosition(i + 1));  
            }
            VKmerBytesWritable destVertexId = kmerList.getPosition(i);
            sendMsg(destVertexId, outgoingMsg);
        }
    }
    
    public void finalProcessBFS(BFSTraverseMessageWritable incomingMsg){
        VKmerListWritable kmerList = incomingMsg.getPathList();
        kmerList.append(getVertexId());
        EDGETYPE neighborToMe = EDGETYPE.fromByte(incomingMsg.getFlag());
        setEdgeDirs(incomingMsg, meToNeighborDir, neighborToMeDir);
        incomingMsg.setEdgeTypesList(outgoingMsg.getEdgeTypesList());
    }
    
    public void appendCommonReadId(BFSTraverseMessageWritable incomingMsg){
        long readId = incomingMsg.getReadId();
        //add readId to prev edge 
        byte prevToMeDir = incomingMsg.getEdgeTypesList().get(0).getPrevToMeDir();
        VKmerBytesWritable tmpKmer;
        tmpKmer = incomingMsg.getPathList().getPosition(0);
        if(tmpKmer.getKmerLetterLength() != 0)
            getVertexValue().getEdgeList(EDGETYPE.fromByte(prevToMeDir)).getReadIDs(tmpKmer).appendReadId(readId);
        //set readId to next edge
        byte nextToMeDir = incomingMsg.getEdgeTypesList().get(0).getNextToMeDir();
        tmpKmer = incomingMsg.getPathList().getPosition(1);
        if(tmpKmer.getKmerLetterLength() != 0)
            getVertexValue().getEdgeList(EDGETYPE.fromByte(nextToMeDir)).getReadIDs(tmpKmer).appendReadId(readId);
    }
    
    @Override
    public void compute(Iterator<BFSTraverseMessageWritable> msgIterator) {
        initVertex();
        if(getSuperstep() == 1){
            addFakeVertex("A");
            voteToHalt();
        }
        else if(getSuperstep() == 2){
            // for test, assign two kmer to srcNode and destNode
            kmerList.append(srcNode);
            kmerList.append(destNode);
            // initiate two nodes -- srcNode and destNode
            initiateSrcAndDestNode(kmerList, commonReadId, false, true);
            sendMsg(srcNode, outgoingMsg);
            
            deleteVertex(getVertexId());
        } else if(getSuperstep() == 3){
            while(msgIterator.hasNext()){
                BFSTraverseMessageWritable incomingMsg = msgIterator.next();
                // begin to BFS
                initialBroadcaseBFSTraverse(incomingMsg);
            }
            voteToHalt();
        } else if(getSuperstep() > 3){
            while(msgIterator.hasNext()){
                BFSTraverseMessageWritable incomingMsg = msgIterator.next();
                if(incomingMsg.isTraverseMsg()){
                    // check if find destination
                    if(incomingMsg.getSeekedVertexId().equals(getVertexId())){
                        if(isValidDestination(incomingMsg)){
                            // final step to process BFS -- pathList and dirList
                            finalProcessBFS(incomingMsg);
                            // send message to all the path nodes to add this common readId 
                            sendMsgToPathNodeToAddCommondReadId(incomingMsg);
                        }
                        else{//continue to BFS
                            broadcaseBFSTraverse(incomingMsg);
                        }
                    } else {//continue to BFS
                        broadcaseBFSTraverse(incomingMsg);
                    }
                } else{// append common readId to the corresponding edge
                    appendCommonReadId(incomingMsg);
                }
            }
            voteToHalt();
        }
        
    }
    
    public static void main(String[] args) throws Exception {
        Client.run(args, getConfiguredJob(null, BFSTraverseVertex.class));
    }

}
