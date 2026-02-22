package com.shanaurin.performance.files.util;

public class MemoryMonitor {
    private final Runtime runtime = Runtime.getRuntime();
    private long startMemory;
    private long peakMemory;

    public void start() {
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        startMemory = getUsedMemory();
        peakMemory = startMemory;
    }

    public void updatePeak() {
        long current = getUsedMemory();
        if (current > peakMemory) {
            peakMemory = current;
        }
    }

    public long getMemoryUsed() {
        return getUsedMemory() - startMemory;
    }

    public long getPeakMemory() {
        return peakMemory;
    }

    private long getUsedMemory() {
        return runtime.totalMemory() - runtime.freeMemory();
    }
}