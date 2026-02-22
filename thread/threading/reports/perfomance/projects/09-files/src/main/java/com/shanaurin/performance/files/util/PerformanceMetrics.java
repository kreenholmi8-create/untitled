package com.shanaurin.performance.files.util;

import lombok.Data;

@Data
public class PerformanceMetrics {
    private String operationName;
    private int recordCount;
    private long durationNanos;
    private long durationMillis;
    private long memoryUsedBytes;
    private long peakMemoryBytes;

    public PerformanceMetrics(String operationName, int recordCount) {
        this.operationName = operationName;
        this.recordCount = recordCount;
    }

    public void setDuration(long nanos) {
        this.durationNanos = nanos;
        this.durationMillis = nanos / 1_000_000;
    }

    public double getThroughput() {
        if (durationMillis == 0) return 0;
        return (double) recordCount / durationMillis * 1000; // записей в секунду
    }

    @Override
    public String toString() {
        return String.format(
                "%-40s | Записей: %,8d | Время: %,10d мс | Память: %,12d байт | Пик памяти: %,12d байт | Throughput: %,.2f записей/сек",
                operationName, recordCount, durationMillis, memoryUsedBytes, peakMemoryBytes, getThroughput()
        );
    }
}