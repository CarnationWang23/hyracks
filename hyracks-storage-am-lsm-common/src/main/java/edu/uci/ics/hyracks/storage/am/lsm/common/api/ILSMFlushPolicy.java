package edu.uci.ics.hyracks.storage.am.lsm.common.api;


public interface ILSMFlushPolicy {
    public void memoryComponentExceededThreshold(ILSMIndex index);
}