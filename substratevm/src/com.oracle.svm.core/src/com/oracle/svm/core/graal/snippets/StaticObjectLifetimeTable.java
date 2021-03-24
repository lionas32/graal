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
    public static int[] youngOrOld = new int[STATIC_SIZE]; // 0 > for old, else young?

    /**
     * If distribution doesn't change for 2 table clears, don't compute anymore.
     * This could maybe be changed to a list of allocations sites? (such that we know which objects we are skipping).
     */
    public static boolean[] skippableObjects = new boolean[STATIC_SIZE];
    public static int lastSkippableObject; // To avoid looking up in the table.

    // Variables to track the survivor rate
    public static int aliveBeforeGC = 0;
    public static int aliveAfterGC = 0;
    public static int aliveBefore0thIndex = 0;
    public static int aliveBefore2stIndex = 0;
    public static int aliveAfter0thIndex = 0;
    public static boolean start = true;


    public static UnsignedWord epoch = WordFactory.unsigned(0); // total GC cycles


    // methods for survivor rate
    public static final void calculateBefore(){
        if(start){
            for(int i = 0; i < allocationSiteCounters.length; i++){
                aliveBeforeGC += allocationSiteCounters[i][0];
            }
        } else {
            aliveBeforeGC = 0;
            aliveBefore0thIndex = 0;
            aliveBefore2stIndex = 0;
            for(int i = 0; i < allocationSiteCounters.length; i++) {
                aliveBeforeGC += allocationSiteCounters[i][0];
                aliveBefore0thIndex += allocationSiteCounters[i][0];
                aliveBefore2stIndex += allocationSiteCounters[i][2];
            }
            aliveBeforeGC = (aliveBeforeGC - aliveAfter0thIndex) + aliveAfterGC;
        }
    }

    public static final void calculateAfter(){
        if(start){
            for(int i = 0; i < allocationSiteCounters.length; i++){
                aliveAfterGC += allocationSiteCounters[i][0];
            }
            aliveAfter0thIndex = aliveAfterGC;
            aliveAfterGC = aliveBeforeGC - aliveAfterGC;
            start = false;
        } else {
            int aliveAfter0thIndex = 0;
            int aliveAfter2thIndex = 0;
            for(int[] i : allocationSiteCounters){
                aliveAfter0thIndex += i[0];
                aliveAfter2thIndex += i[2];
            }

            aliveAfterGC = Math.abs(aliveBefore0thIndex -  aliveAfter0thIndex) + Math.abs(aliveBefore2stIndex -  aliveAfter2thIndex);
        }
    }

    public static final void setSkippableSite(int hash){
        skippableObjects[hash] = true;
    }

    public static final boolean incrementAllocation(int allocationSite, int lifetime){
        allocationSite &= allocationSiteMask;
        if(lastSkippableObject == allocationSite){
            return false;
        }
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == allocationSite){
                if(skippableObjects[hash]){
                    lastSkippableObject = allocationSite;
                    return false;
                }
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

    public static final boolean getIsSkippable(int allocationSite) {
        allocationSite &= allocationSiteMask;
        if(lastSkippableObject == allocationSite){
            return true;
        }
        for(int i = 0; i < STATIC_SIZE; i++){
            int hash = hash(i, allocationSite);
            if(allocationSites[hash] == 0){
                return true;
            }
            if(allocationSites[hash] == allocationSite){
                if(skippableObjects[hash]){
                    lastSkippableObject = allocationSite;
                    return true;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Cache the distribution (1 for old, -1 for young)
     * It's possible I need to change the 'toCache' condition. (promotions/allocations > 0.5)
     * Also not sure about the last else-branch.
     */
    public static final void cacheTable(){
        for(int i = 0; i < STATIC_SIZE; i++){
            int[] allocations = allocationSiteCounters[i];
            if(allocations != null){
                if(skippableObjects[i]){
                    continue;
                }
                boolean toCache = allocations[0] < allocations[1] + allocations[2] + allocations[3];
                if(toCache) {
                    if (youngOrOld[i] == 1) {
                        setSkippableSite(i);
                    } else {
                        youngOrOld[i] += 1;
                    }
                } else {
                    if(youngOrOld[i] == 1 || youngOrOld[i] == -1){
                        setSkippableSite(i);
                    } else {
                        youngOrOld[i] -= 1;
                    }
                }
            }
        }
    }

    // Ran every X epoch to ensure freshness
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
                return youngOrOld[hash] > 0;
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
