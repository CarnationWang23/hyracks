package edu.uci.ics.hyracks.storage.am.lsm.invertedindex.impls;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import edu.uci.ics.hyracks.storage.am.common.api.ICursorInitialState;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexAccessor;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.LSMHarness;
import edu.uci.ics.hyracks.storage.common.buffercache.ICachedPage;

public class LSMInvertedIndexCursorInitialState implements ICursorInitialState {

    private final boolean includeMemComponent;
    private final AtomicInteger searcherfRefCount;
    private final LSMHarness lsmHarness;
    private final List<IIndexAccessor> indexAccessors;

    public LSMInvertedIndexCursorInitialState(List<IIndexAccessor> indexAccessors, boolean includeMemComponent,
            AtomicInteger searcherfRefCount, LSMHarness lsmHarness) {
        this.indexAccessors = indexAccessors;
        this.includeMemComponent = includeMemComponent;
        this.searcherfRefCount = searcherfRefCount;
        this.lsmHarness = lsmHarness;
    }

    @Override
    public ICachedPage getPage() {
        return null;
    }

    @Override
    public void setPage(ICachedPage page) {
    }
    
    public List<IIndexAccessor> getIndexAccessors() {
        return indexAccessors;
    }

    public AtomicInteger getSearcherRefCount() {
        return searcherfRefCount;
    }

    public boolean getIncludeMemComponent() {
        return includeMemComponent;
    }

    public LSMHarness getLSMHarness() {
        return lsmHarness;
    }
}
