package com.shanaurin.performance.files.service;

import com.shanaurin.performance.files.model.CurrencyRate;
import com.shanaurin.performance.files.util.MemoryMonitor;
import com.shanaurin.performance.files.util.PerformanceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class RandomAccessFileService {

    private static final String FILE_PATH = "data_random_access.dat";
    private static final int RECORD_SIZE = 108;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public PerformanceMetrics writeData(List<CurrencyRate> data) {
        PerformanceMetrics metrics = new PerformanceMetrics("RandomAccessFile - Запись", data.size());
        MemoryMonitor monitor = new MemoryMonitor();
        monitor.start();

        long startTime = System.nanoTime();

        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "rw")) {
            for (int i = 0; i < data.size(); i++) {
                writeRecord(raf, data.get(i), i);
                if (i % 10000 == 0) {
                    monitor.updatePeak();
                }
            }
        } catch (IOException e) {
            log.error("Ошибка при записи данных через RandomAccessFile", e);
        }

        long endTime = System.nanoTime();
        metrics.setDuration(endTime - startTime);
        metrics.setMemoryUsedBytes(monitor.getMemoryUsed());
        metrics.setPeakMemoryBytes(monitor.getPeakMemory());

        return metrics;
    }

    public PerformanceMetrics readDataSequential(int count) {
        PerformanceMetrics metrics = new PerformanceMetrics("RandomAccessFile - Последовательное чтение", count);
        MemoryMonitor monitor = new MemoryMonitor();
        monitor.start();

        long startTime = System.nanoTime();
        List<CurrencyRate> result = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "r")) {
            long fileSize = raf.length();
            int maxRecords = (int) (fileSize / RECORD_SIZE);
            int recordsToRead = Math.min(count, maxRecords);

            for (int i = 0; i < recordsToRead; i++) {
                raf.seek((long) i * RECORD_SIZE);
                CurrencyRate rate = readRecord(raf);
                result.add(rate);
                if (i % 10000 == 0) {
                    monitor.updatePeak();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при чтении данных через RandomAccessFile", e);
        }

        long endTime = System.nanoTime();
        metrics.setDuration(endTime - startTime);
        metrics.setMemoryUsedBytes(monitor.getMemoryUsed());
        metrics.setPeakMemoryBytes(monitor.getPeakMemory());

        return metrics;
    }

    public PerformanceMetrics readDataRandom(int totalRecords, int samplesToRead) {
        PerformanceMetrics metrics = new PerformanceMetrics("RandomAccessFile - Случайное чтение", samplesToRead);
        MemoryMonitor monitor = new MemoryMonitor();
        monitor.start();

        Random random = new Random(42);
        long startTime = System.nanoTime();
        List<CurrencyRate> result = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "r")) {
            long fileSize = raf.length();
            int maxRecords = (int) (fileSize / RECORD_SIZE);

            for (int i = 0; i < samplesToRead; i++) {
                int randomIndex = random.nextInt(Math.min(totalRecords, maxRecords));
                raf.seek((long) randomIndex * RECORD_SIZE);
                CurrencyRate rate = readRecord(raf);
                result.add(rate);
                if (i % 1000 == 0) {
                    monitor.updatePeak();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при случайном чтении данных через RandomAccessFile", e);
        }

        long endTime = System.nanoTime();
        metrics.setDuration(endTime - startTime);
        metrics.setMemoryUsedBytes(monitor.getMemoryUsed());
        metrics.setPeakMemoryBytes(monitor.getPeakMemory());

        return metrics;
    }

    private void writeRecord(RandomAccessFile raf, CurrencyRate rate, int index) throws IOException {
        long position = (long) index * RECORD_SIZE;
        raf.seek(position);

        raf.writeLong(rate.getId());
        writeFixedString(raf, rate.getCurrencyPair(), 20);
        raf.writeDouble(rate.getRate());
        raf.writeDouble(rate.getVolume());
        String timestampStr = rate.getTimestamp().format(FORMATTER);
        writeFixedString(raf, timestampStr, 64);
    }

    private CurrencyRate readRecord(RandomAccessFile raf) throws IOException {
        long id = raf.readLong();
        String currencyPair = readFixedString(raf, 20);
        double rate = raf.readDouble();
        double volume = raf.readDouble();
        String timestampStr = readFixedString(raf, 64);

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(timestampStr, FORMATTER);
        } catch (Exception e) {
            log.warn("Ошибка парсинга даты '{}', используем текущее время", timestampStr);
            timestamp = LocalDateTime.now();
        }

        return new CurrencyRate(id, currencyPair, rate, volume, timestamp);
    }

    private void writeFixedString(RandomAccessFile raf, String str, int length) throws IOException {
        byte[] bytes = new byte[length];
        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        int copyLength = Math.min(strBytes.length, length);
        System.arraycopy(strBytes, 0, bytes, 0, copyLength);
        // Заполняем остаток пробелами
        for (int i = copyLength; i < length; i++) {
            bytes[i] = ' ';
        }
        raf.write(bytes);
    }

    private String readFixedString(RandomAccessFile raf, int length) throws IOException {
        byte[] bytes = new byte[length];
        raf.readFully(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8).trim();
        return result.replace("\0", "").trim();
    }
}