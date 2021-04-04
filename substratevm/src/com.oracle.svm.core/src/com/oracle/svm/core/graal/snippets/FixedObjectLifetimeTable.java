package com.oracle.svm.core.graal.snippets;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.util.Arrays;

public class FixedObjectLifetimeTable {
    public static final int allocationSiteMask = 0x3fffffff;
    public static final int MAX_AGE = 0b11;
    public static final int STATIC_SIZE = 65536;

    public static int[][] allocationSiteCounters = new int[STATIC_SIZE][MAX_AGE + 1];
    public static int[] youngOrOld = new int[STATIC_SIZE];

    // Set if profiling turned on
    public static boolean toProfile = true;

    public static UnsignedWord epoch = WordFactory.unsigned(0); // total GC cycles

    public static final boolean incrementAllocation(int allocationSite, int lifetime){
        allocationSite &= allocationSiteMask;
        allocationSiteCounters[allocationSite][lifetime] += 1;
        return true;
    }

    /**
     * Cache the distribution (1 for old, 0 for young)
     */
    public static final void cacheTable(){
        for(int i = 0; i < STATIC_SIZE; i++){
            int[] allocations = allocationSiteCounters[i];
            if(allocations != null){
                boolean toCache = allocations[0] < allocations[1] + allocations[2] + allocations[3];
                if(toCache) {
                    youngOrOld[i] = 1;
                }
            }
        }
        toProfile = false;
    }

    /**
     * Ran every X epoch to ensure freshness
     */
    public static final void clearTable(){
        for(int i = 0; i < STATIC_SIZE; i++){
            Arrays.fill(allocationSiteCounters[i], 0);
        }
    }

    public static final int maskAge(int allocationContext){
        return allocationContext >>> 30;
    }

    public static final int maskAllocationSite(int allocationContext){
        return allocationContext & allocationSiteMask;
    }

    public static final boolean exists(int allocationSite){
        allocationSite &= allocationSiteMask;
        return allocationSite < STATIC_SIZE;
    }

    public static final boolean decrementAllocation(int allocationSite, int lifetime){
        allocationSite &= allocationSiteMask;
        if(allocationSiteCounters[allocationSite][lifetime] > 0){
            allocationSiteCounters[allocationSite][lifetime] -= 1;
            return true;
        }
        return false;
    }

    public static final int[] getLifetimesForAllocationSite(int allocationSite){
        allocationSite &= allocationSiteMask;
        return allocationSiteCounters[allocationSite];
    }

    public static final boolean getCachedGeneration(int allocationSite){
        allocationSite &= allocationSiteMask;
        return youngOrOld[allocationSite] > 0;
    }
}
