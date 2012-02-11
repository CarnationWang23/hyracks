package edu.uci.ics.hyracks.storage.am.common.datagen;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;

/**
 * Quick & dirty data generator for performance testing. 
 *
 */
public class DataGenThread extends Thread {
    public final BlockingQueue<TupleBatch> tupleBatchQueue;
    private final int maxNumBatches;
    private final int maxOutstandingBatches;        
    private int numBatches;
    private final Random rnd;
    
    // maxOutstandingBatches pre-created tuple-batches for populating the queue.
    private TupleBatch[] tupleBatches;
    private int ringPos;
    
    public DataGenThread(int numConsumers, int maxNumBatches, int batchSize, ISerializerDeserializer[] fieldSerdes, int payloadSize, int rndSeed, int maxOutstandingBatches, boolean sorted) {
        this.maxNumBatches = maxNumBatches;
        this.maxOutstandingBatches = maxOutstandingBatches;
        rnd = new Random(rndSeed);
        tupleBatches = new TupleBatch[maxOutstandingBatches];
        IFieldValueGenerator[] fieldGens = DataGenUtils.getFieldGensFromSerdes(fieldSerdes, rnd, sorted);
        for (int i = 0; i < maxOutstandingBatches; i++) {
            tupleBatches[i] = new TupleBatch(batchSize, fieldGens, fieldSerdes, payloadSize);
        }
        // make sure we don't overwrite tuples that are in use by consumers. 
        // -1 because we first generate a new tuple, and then try to put it into the queue.
        int capacity = Math.max(maxOutstandingBatches - numConsumers - 1, 1);
        tupleBatchQueue = new LinkedBlockingQueue<TupleBatch>(capacity);
        ringPos = 0;
    }
    
    @Override
    public void run() {
        while(numBatches < maxNumBatches) {
            try {
                tupleBatches[ringPos].generate();
                tupleBatchQueue.put(tupleBatches[ringPos]);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            numBatches++;
            ringPos++;
            if (ringPos >= maxOutstandingBatches) {
                ringPos = 0;
            }
        }
    }
}
