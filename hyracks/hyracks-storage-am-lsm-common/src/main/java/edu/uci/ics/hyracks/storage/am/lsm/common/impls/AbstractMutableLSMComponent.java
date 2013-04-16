package edu.uci.ics.hyracks.storage.am.lsm.common.impls;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMComponent;

public abstract class AbstractMutableLSMComponent implements ILSMComponent {

    private int readerCount;
    private int writerCount;
    private ComponentState state;
    
    private boolean isModified;

    private enum ComponentState {
        READABLE_WRITABLE,
        READABLE_UNWRITABLE,
        READABLE_UNWRITABLE_FLUSHING,
        UNREADABLE_UNWRITABLE
    }

    public AbstractMutableLSMComponent() {
        readerCount = 0;
        writerCount = 0;
        state = ComponentState.READABLE_WRITABLE;
        isModified = false;
    }

    @Override
    public synchronized boolean threadEnter(LSMOperationType opType) throws InterruptedException {
        switch (opType) {
            case FORCE_MODIFICATION:
                if (state != ComponentState.READABLE_WRITABLE && state != ComponentState.READABLE_UNWRITABLE) {
                    return false;
                }
                writerCount++;
                break;
            case MODIFICATION:
                if (state != ComponentState.READABLE_WRITABLE) {
                    return false;
                }
                writerCount++;
                break;
            case SEARCH:
                if (state == ComponentState.UNREADABLE_UNWRITABLE) {
                    return false;
                }
                readerCount++;
                break;
            case FLUSH:
                if (state == ComponentState.READABLE_UNWRITABLE_FLUSHING
                        || state == ComponentState.UNREADABLE_UNWRITABLE) {
                    return false;
                }

                state = ComponentState.READABLE_UNWRITABLE_FLUSHING;
                while (writerCount > 0) {
                    wait();
                }
                readerCount++;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported operation " + opType);
        }
        return true;
    }

    @Override
    public synchronized void threadExit(LSMOperationType opType, boolean failedOperation) throws HyracksDataException {
        switch (opType) {
            case FORCE_MODIFICATION:
            case MODIFICATION:
                writerCount--;
                if (state == ComponentState.READABLE_WRITABLE && isFull()) {
                    state = ComponentState.READABLE_UNWRITABLE;
                }
                break;
            case SEARCH:
                readerCount--;
                if (state == ComponentState.UNREADABLE_UNWRITABLE && readerCount == 0) {
                    reset();
                    state = ComponentState.READABLE_WRITABLE;
                } else if (state == ComponentState.READABLE_WRITABLE && isFull()) {
                    state = ComponentState.READABLE_UNWRITABLE;
                }
                break;
            case FLUSH:
                if (failedOperation) {
                    state = isFull() ? ComponentState.READABLE_UNWRITABLE : ComponentState.READABLE_WRITABLE;
                }
                readerCount--;
                if (readerCount == 0) {
                    reset();
                    state = ComponentState.READABLE_WRITABLE;
                } else if (state == ComponentState.READABLE_UNWRITABLE_FLUSHING) {
                    state = ComponentState.UNREADABLE_UNWRITABLE;
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported operation " + opType);
        }
        notifyAll();
    }
    

    public void setIsModified() {
        isModified = true;
    }

    public boolean isModified() {
        return isModified;
    }

    protected abstract boolean isFull();

    protected void reset() throws HyracksDataException {
        isModified = false;
    }
}
