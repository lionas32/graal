package com.oracle.svm.core.graal.snippets;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.util.Arrays;

public class StaticObjectLifetimeTable {
    public static final int allocationSiteMask = 0x3fffffff;
    public static final int MAX_AGE = 0b11;
    public static final int STATIC_SIZE = 65536;

    public static int[][] allocationSiteCounters = new int[STATIC_SIZE][MAX_AGE + 1];
    public static int[] allocationSites = new int[STATIC_SIZE];
    public static int[] youngOrOld = new int[STATIC_SIZE];

    // Set if profiling turned on
    public static boolean toProfile = true;

    public static UnsignedWord epoch = WordFactory.unsigned(0); // total GC cycles

    public static final boolean incrementAllocation(int allocationSite, int lifetime){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == allocationSite){
                allocationSiteCounters[hash][lifetime] += 1;
                return true;
            }
            if(allocationSites[hash] == 0){
                allocationSites[hash] = allocationSite;
                allocationSiteCounters[hash][lifetime] += 1;
                return true;
            }
        }
        return false;
    }

    /**
     * Cache the distribution (1 for old, 0 for young)
     */
    public static final void cacheTable(){
        for(int i = 0; i < STATIC_SIZE; i++){
            int[] allocations = allocationSiteCounters[i];
            if(allocations != null){
                boolean toCache = allocations[0] * 0.5 < allocations[1] + allocations[2] + allocations[3];
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
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if (allocationSites[hash] == allocationSite) {
                return true;
            } else if (allocationSites[hash] == 0){
                return false;
            }
        }
        return false;
    }

    public static final boolean decrementAllocation(int allocationSite, int lifetime){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == allocationSite){
                if(allocationSiteCounters[hash][lifetime] > 0){
                    allocationSiteCounters[hash][lifetime] -= 1;
                    return true;
                }else{
                    return false; // Can't decrement something of age 0
                }
            } else if (allocationSites[hash] == 0){
                return false;
            }
        }
        return false;
    }

    public static final int[] getLifetimesForAllocationSite(int allocationSite){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if (allocationSites[hash] == allocationSite){
                return allocationSiteCounters[hash];
            }
            if (allocationSites[hash] == 0){
                return null;
            }
        }
        return null;
    }

    public static final boolean getCachedGeneration(int allocationSite){
        allocationSite &= allocationSiteMask;
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if (allocationSites[hash] == allocationSite) {
                return youngOrOld[hash] == 1;
            }
            if (allocationSites[hash] == 0){
                return false;
            }
        }
        return false;
    }

    public static int hash(int i, int key){
        return (hash(key) + quadProbing(i)) % STATIC_SIZE;
    }

    public static int quadProbing(int index){
        if(index == 0){
            return 0;
        }
        return index * index;
    }

    // Based on: https://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
    public static int hash(int x) {
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = (x >> 16) ^ x;
        return x;
    }
}
