package com.oracle.svm.core.graal.snippets;

import com.oracle.svm.core.log.Log;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import sun.misc.Unsafe;

public class DynamicObjectLifetimeTable {

    private final static int ALLOCATION_SITE_MASK = 0x1fffffff;
    private final static int INT = 4;
    private final static int NUMBER_OF_LIFETIMES = 8;
    public final static int TABLE_PRIME = 7;
    private static Unsafe unsafe = GraalUnsafeAccess.getUnsafe();

    public static int size = 65536;
    private static long siteAddress;
    private static long lifetimeAddress;

    private static int[] testInt = {0,0,0,0,0,0,0,0};

    public static int differentObjectCounter = 0;

    public static void move(){
//        siteAddress = unsafe.allocateMemory(size * INT);
//        lifetimeAddress = unsafe.allocateMemory(size * INT * NUMBER_OF_LIFETIMES);
//        // Clear the garbage, and set all of it to 0
//        unsafe.setMemory(siteAddress, size * INT, (byte) 0);
//        unsafe.setMemory(lifetimeAddress, size * INT * NUMBER_OF_LIFETIMES, (byte) 0);
//        // Copy over the lifetimes of 0 for all allocation sites
//        for(int i = 0; i < StaticObjectLifetimeTable.STATIC_SIZE; i++){
//            int allocationSite = StaticObjectLifetimeTable.allocationSites[i];
//            if (StaticObjectLifetimeTable.allocationSites[i] != 0){
//                int allocations = StaticObjectLifetimeTable.allocationSiteCounters[i][0];
//                setAllocation(allocationSite, allocations);
//            }
//        }
//        Log.log().string("before setIsMoved").newline();
//        StaticObjectLifetimeTable.setIsMoved();
    }

    public static int maskAllocationSite(int allocationSite){
        return allocationSite & ALLOCATION_SITE_MASK;
    }

    public static boolean incrementAllocation(int allocationSite, int lifetime){
        allocationSite = maskAllocationSite(allocationSite);
        for(int i = 0; i < size; i++){
            int hash = hash(i, allocationSite);
            if(getAllocationSite(hash) == 0){
                setAllocationSite(hash, allocationSite);
                differentObjectCounter += 1;
            }
            if(getAllocationSite(hash) == allocationSite){
                incrementLifetime(hash, lifetime);
                return true;
            }
        }
        return false;
    }

    public static boolean setAllocation(int allocationSite, int allocations){
        allocationSite = maskAllocationSite(allocationSite);
        for(int i = 0; i < size; i++){
            int hash = hash(i, allocationSite);
            if(getAllocationSite(hash) == 0){
                setAllocationSite(hash, allocationSite);
                differentObjectCounter += 1;
            }
            if(getAllocationSite(hash) == allocationSite){
                setLifetime(hash, 0, allocations);
                return true;
            }
        }
        return false;
    }

    public static boolean decrementAllocation(int allocationSite, int lifetime){
        allocationSite = maskAllocationSite(allocationSite);
        for(int i = 0; i < size; i++){
            int hash = hash(i, allocationSite);
            if(getAllocationSite(hash) == 0){
                return false;
            }
            if(getAllocationSite(hash) == allocationSite){
                if(getLifetime(hash, lifetime) > 0){
                    decrementLifetime(hash, lifetime);
                    return true;
                } else {
                    return false; // Can't decrement something of age 0
                }
            }
        }
        return false;
    }

    private static void setAllocationSite(long idx, int value) {
        unsafe.putInt(siteAddress + idx * INT, value);
    }

    private static int getAllocationSite(long idx) {
        return unsafe.getInt(siteAddress + idx * INT);
    }

    private static int getLifetime(int idx, int lifetime){
        return unsafe.getInt(lifetimeAddress + idx * INT * NUMBER_OF_LIFETIMES + (INT * lifetime));
    }

    private static void incrementLifetime(int idx, int lifetime){
        int lifetimeValue = getLifetime(idx, lifetime);
        unsafe.putInt(lifetimeAddress + idx * INT * NUMBER_OF_LIFETIMES + (INT * lifetime), lifetimeValue + 1);
    }

    private static void decrementLifetime(int idx, int lifetime){
        int lifetimeValue =  getLifetime(idx, lifetime);
        unsafe.putInt(lifetimeAddress + idx * INT * NUMBER_OF_LIFETIMES + (INT * lifetime), lifetimeValue - 1);
    }

    private static void setLifetime(int idx, int lifetime, int val){
        unsafe.putInt(lifetimeAddress + idx * INT * NUMBER_OF_LIFETIMES + (INT * lifetime), val);
    }

    public static boolean exists(int allocationSite){
        allocationSite = maskAllocationSite(allocationSite);
        for(int i = 0; i < size; i++){
            int hash = hash(i, allocationSite);
            if(getAllocationSite(hash) == 0){
                return false;
            } else if (getAllocationSite(hash) == allocationSite) {
                return true;
            }
        }
        return false;
    }

    public static int[] getLifetimesForAllocationSite(int allocationSite){
        allocationSite = maskAllocationSite(allocationSite);
        for (int i = 0; i < testInt.length; i++){
            testInt[i] = 0;
        }
        for(int i = 0; i < size; i += 0){
            int hash = hash(i, allocationSite);
            int alloc = getAllocationSite(hash);
            if(alloc == 0){
                return null;
            } else if (alloc == allocationSite){
                for(int y = 0; y < 8; y++){
                    testInt[y] = getLifetime(hash, y);
                }
                return testInt;
            }
        }
        return null;
    }

    public long size() {
            return size;
    }

    public static int hash(int i, int key){
        if (i == 0){
            return key % size;
        } else {
            return i * (TABLE_PRIME - (key % TABLE_PRIME));
        }
    }
}
