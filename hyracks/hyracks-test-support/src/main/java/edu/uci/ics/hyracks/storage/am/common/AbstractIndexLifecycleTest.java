package edu.uci.ics.hyracks.storage.am.common;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.common.api.IIndex;

public abstract class AbstractIndexLifecycleTest {

    protected IIndex index;

    protected abstract boolean persistentStateExists() throws Exception;

    protected abstract boolean isEmptyIndex() throws Exception;

    protected abstract void performInsertions() throws Exception;

    protected abstract void checkInsertions() throws Exception;

    protected abstract void clearCheckableInsertions() throws Exception;

    @Before
    public abstract void setup() throws Exception;

    @After
    public abstract void tearDown() throws Exception;

    @Test
    public void validSequenceTest() throws Exception {
        // Double create is valid
        index.create();
        Assert.assertTrue(persistentStateExists());
        index.create();
        Assert.assertTrue(persistentStateExists());

        // Double open is valid
        index.activate();
        index.activate();
        Assert.assertTrue(isEmptyIndex());

        // Insert some stuff
        performInsertions();
        checkInsertions();

        // Check that the inserted stuff isn't there
        clearCheckableInsertions();
        index.clear();
        Assert.assertTrue(isEmptyIndex());

        // Insert more stuff
        performInsertions();

        // Double close is valid
        index.deactivate();
        index.deactivate();

        // Check that the inserted stuff is still there
        index.activate();
        checkInsertions();
        index.deactivate();

        // Double destroy is valid
        index.destroy();
        Assert.assertFalse(persistentStateExists());
        index.destroy();
        Assert.assertFalse(persistentStateExists());
    }

    @Test(expected = HyracksDataException.class)
    public void invalidSequenceTest1() throws Exception {
        index.create();
        index.activate();
        index.create();
    }

    @Test(expected = HyracksDataException.class)
    public void invalidSequenceTest2() throws Exception {
        index.create();
        index.activate();
        index.destroy();
    }

    @Test(expected = HyracksDataException.class)
    public void invalidSequenceTest3() throws Exception {
        index.create();
        index.clear();
    }

    @Test(expected = HyracksDataException.class)
    public void invalidSequenceTest4() throws Exception {
        index.clear();
    }
}
