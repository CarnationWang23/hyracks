package edu.uci.ics.hyracks.storage.am.lsm.common.impls;

import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIOScheduler;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIOSchedulerProvider;

public enum SequentialSchedulerProvider implements ILSMIOSchedulerProvider {
    INSTANCE;

    @Override
    public ILSMIOScheduler getIOScheduler() {
        return SequentialScheduler.INSTANCE;
    }

}
