package edu.uci.ics.hyracks.storage.am.lsm.inverteredindex;

import java.util.logging.Logger;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.invertedindex.tokenizers.ITokenFactory;
import edu.uci.ics.hyracks.storage.am.invertedindex.tokenizers.NGramUTF8StringBinaryTokenizer;
import edu.uci.ics.hyracks.storage.am.invertedindex.tokenizers.UTF8NGramTokenFactory;
import edu.uci.ics.hyracks.storage.am.lsm.invertedindex.utils.InvertedIndexTestUtils;

public class InMemoryBTreeInvertedIndexInsertTest extends AbstractInvertedIndexInsertTest {

    @Override
    protected void setTokenizer() {
        ITokenFactory tokenFactory = new UTF8NGramTokenFactory();
        tokenizer = new NGramUTF8StringBinaryTokenizer(3, false, true, false, tokenFactory);
//        ITokenFactory tokenFactory = new UTF8WordTokenFactory();
//        tokenizer = new DelimitedUTF8StringBinaryTokenizer(true, false, tokenFactory);
    }

    @Override
    protected void setInvertedIndex() throws HyracksDataException {
        invertedIndex = InvertedIndexTestUtils.createTestInMemoryBTreeInvertedIndex(harness, tokenizer);
        invertedIndex.create(harness.getFileId());
        invertedIndex.open(harness.getFileId());
    }

    @Override
    protected void setLogger() {
        LOGGER = Logger.getLogger(InMemoryBTreeInvertedIndexInsertTest.class.getName());
    }

    @Override
    protected void setRandom() {
        random = true;
    }

}
