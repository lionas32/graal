package com.oracle.svm.core.graal.snippets;

public class StaticObjectLifetimeTable {
    public static final int allocationSiteMask = 0x1fffffff;
    public static final int STATIC_SIZE = 65536;
    public static final int TABLE_PRIME = 7;
    public static long fieldCounter; //TODO: This is not necessary anymore, can remove
    //We will now try to track the allocation site of some objects
    public static int[] fieldCounters = new int[STATIC_SIZE]; //TODO: This is not necessary anymore, can remove

    public static int[][] allocationSiteCounters = new int[STATIC_SIZE][8];
    public static int[] allocationSites = new int[STATIC_SIZE];
    public static int objectCounter = 0;

    // Lifetime table
    public static final boolean incrementAllocation(int allocationSite, int lifetime){
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

    public static final boolean decrementAllocation(int allocationSite, int lifetime){
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == 0){
                return false;
            }
            if(allocationSites[hash] == allocationSite){
                if(allocationSiteCounters[hash][lifetime] > 0){
                    allocationSiteCounters[hash][lifetime] -= 1;
                }
                return true;
            }
        }
        return false;
    }


    public static final int[] getLifetimesForAllocationSite(int allocationSite){
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
