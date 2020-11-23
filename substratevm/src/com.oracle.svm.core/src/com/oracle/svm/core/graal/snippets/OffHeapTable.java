package com.oracle.svm.core.graal.snippets;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

public class OffHeapTable {
    private final static int INTEGER_SIZE = 4;
    private long size;
    private long address;

    public OffHeapTable(int size) {
        this.size = size;
        address = GraalUnsafeAccess.getUnsafe().allocateMemory(size * INTEGER_SIZE);
    }

    public void set(long i, int value)  {
        GraalUnsafeAccess.getUnsafe().putInt(address + (i * INTEGER_SIZE), value);
    }

    public int get(long i)  {
        return GraalUnsafeAccess.getUnsafe().getInt(address + (i * INTEGER_SIZE));
    }

    public long size() {
        return size;
    }

    public void freeMemory() {
        GraalUnsafeAccess.getUnsafe().freeMemory(address);
    }
}
