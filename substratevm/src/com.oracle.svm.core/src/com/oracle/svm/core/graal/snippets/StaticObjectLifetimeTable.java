package com.oracle.svm.core.graal.snippets;

public class StaticObjectLifetimeTable {
    public static final int allocationSiteMask = 0x1fffffff;
    public static final int STATIC_SIZE = 65536;
    public static final int TABLE_PRIME = 7;
    //We will now try to track the allocation site of some objects

    public static int[][] allocationSiteCounters = new int[STATIC_SIZE][8];
    public static int[] allocationSites = new int[STATIC_SIZE];
    public static int objectCounter = 0;

    // Lifetime table
    public static final boolean incrementAllocation(int allocationSite, int lifetime){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == 0){
                allocationSites[hash] = allocationSite;
                objectCounter += 1;
            }
            if(allocationSites[hash] == allocationSite){
                allocationSiteCounters[hash][lifetime] += 1;
                return true;
            }
        }
        return false;
    }

    public static final boolean exists(int allocationSite){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == 0){
                return false;
            } else if (allocationSites[hash] == allocationSite) {
                return true;
            }
        }
        return false;
    }

    public static final boolean decrementAllocation(int allocationSite, int lifetime){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == 0){
                return false; // Can't decrement something of age 0
            }
            if(allocationSites[hash] == allocationSite && allocationSiteCounters[hash][lifetime] > 0){
                allocationSiteCounters[hash][lifetime] -= 1;
                return true;
            }
        }
        return false;
    }


    public static final int[] getLifetimesForAllocationSite(int allocationSite){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == 0){
                return null;
            } else if (allocationSites[hash] == allocationSite){
                return allocationSiteCounters[hash];
            }
        }
        return null;
    }

    public static int hash(int i, int key){
        if (i == 0){
            return key % STATIC_SIZE;
        } else {
           return i * (TABLE_PRIME - (key % TABLE_PRIME));
        }
    }


}
