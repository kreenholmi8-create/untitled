package com.kolmykova.jobparser.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class UrlQueueService {

    private final BlockingQueue<String> urlQueue = new LinkedBlockingQueue<>();

    public UrlQueueService(MeterRegistry registry) {
        registry.gauge("jobparser.url.queue.size", urlQueue, BlockingQueue::size);
    }

    public void addAll(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        urlQueue.addAll(urls);
    }

    public List<String> pollBatch(int maxCount) {
        List<String> result = new ArrayList<>(maxCount);
        urlQueue.drainTo(result, maxCount);
        return result;
    }

    public int size() {
        return urlQueue.size();
    }
}