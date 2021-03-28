package com.oracle.svm.core.graal.snippets;

public final class DynamicObjectLifetimeTable {

    public static final int allocationSiteMask = 0x3fffffff;
    public static final int MAX_AGE = 0b11;

    public int[][] allocationSiteCounters;

    public DynamicObjectLifetimeTable(){
        allocationSiteCounters = new int[1][MAX_AGE];
    }


    public final void incrementAllocation(int site, int lifetime){
        allocationSiteCounters[site][lifetime] += 1;
    }

    public synchronized final void increaseSize(){
        allocationSiteCounters = new int[allocationSiteCounters.length + 1][MAX_AGE];
    }
}