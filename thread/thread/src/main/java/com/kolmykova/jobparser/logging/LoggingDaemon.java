package com.kolmykova.jobparser.logging;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class LoggingDaemon {

    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    public LoggingDaemon() {
        Thread daemon = new Thread(this::processLogs);
        daemon.setDaemon(true);
        daemon.setName("logging-daemon");
        daemon.start();
    }

    public void log(String message) {
        logQueue.offer(message);
    }

    private void processLogs() {
        while (true) {
            try {
                String msg = logQueue.take();
                // писать в файл или системный лог
                System.out.println("[ASYNC LOG] " + msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}