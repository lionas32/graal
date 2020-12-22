package com.oracle.svm.core.graal.snippets;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

public final class OffHeapTable {
    private final static int INTEGER_SIZE = 4;
    private static long size;
    private static long address;
    private static boolean allocated = false;

    static {
        size = 65536;
    }

    public final static void set(long i, int value){
        if(!allocated){
            address = GraalUnsafeAccess.getUnsafe().allocateMemory(size * INTEGER_SIZE);
            allocated = true;
        }
        GraalUnsafeAccess.getUnsafe().putInt(address + (i + INTEGER_SIZE), value);
    }
}
//    public OffHeapTable(int size) {
////        this.size = size;
////        address = GraalUnsafeAccess.getUnsafe().allocateMemory(size * INTEGER_SIZE);
//    }
//
//    public final void set(long i, int value)  {
//        address = GraalUnsafeAccess.getUnsafe().allocateMemory(size * INTEGER_SIZE);
//        if(!allocated){
//            allocated = true;
//        }
//        GraalUnsafeAccess.getUnsafe().putInt(address + (i * INTEGER_SIZE), value);
//    }
//
//    public int get(long i)  {
//        return GraalUnsafeAccess.getUnsafe().getInt(address + (i * INTEGER_SIZE));
//    }
//
//    public long size() {
//        return size;
//    }
//
//    public long address(){
//        return address;
//    }
//
//    public void freeMemory() {
//        GraalUnsafeAccess.getUnsafe().freeMemory(address);
//    }
