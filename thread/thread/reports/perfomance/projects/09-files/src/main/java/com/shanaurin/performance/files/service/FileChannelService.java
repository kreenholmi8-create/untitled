package com.shanaurin.performance.files.service;

import com.shanaurin.performance.files.model.CurrencyRate;
import com.shanaurin.performance.files.util.MemoryMonitor;
import com.shanaurin.performance.files.util.PerformanceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FileChannelService {

    private static final String FILE_PATH = "data_file_channel.dat";
    private static final int RECORD_SIZE = 108;
    private static final int BUFFER_SIZE = 8192; // 8KB буфер
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public PerformanceMetrics writeData(List<CurrencyRate> data) {
        PerformanceMetrics metrics = new PerformanceMetrics("FileChannel - Запись", data.size());
        MemoryMonitor monitor = new MemoryMonitor();
        monitor.start();

        long startTime = System.nanoTime();

        try (RandomAccessFile file = new RandomAccessFile(FILE_PATH, "rw");
             FileChannel channel = file.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            for (CurrencyRate rate : data) {
                if (buffer.remaining() < RECORD_SIZE) {
                    buffer.flip();
                    channel.write(buffer);
                    buffer.clear();
                    monitor.updatePeak();
                }

                writeRecordToBuffer(buffer, rate);
            }

            // Записываем оставшиеся данные
            if (buffer.position() > 0) {
                buffer.flip();
                channel.write(buffer);
            }

            channel.force(true); // Синхронизация с диском

        } catch (IOException e) {
            log.error("Ошибка при записи данных через FileChannel", e);
        }

        long endTime = System.nanoTime();
        metrics.setDuration(endTime - startTime);
        metrics.setMemoryUsedBytes(monitor.getMemoryUsed());
        metrics.setPeakMemoryBytes(monitor.getPeakMemory());

        return metrics;
    }

    public PerformanceMetrics readDataSequential(int count) {
        PerformanceMetrics metrics = new PerformanceMetrics("FileChannel - Последовательное чтение", count);
        MemoryMonitor monitor = new MemoryMonitor();
        monitor.start();

        long startTime = System.nanoTime();
        List<CurrencyRate> result = new ArrayList<>();

        try (RandomAccessFile file = new RandomAccessFile(FILE_PATH, "r");
             FileChannel channel = file.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            int recordsRead = 0;
            while (recordsRead < count && channel.read(buffer) > 0) {
                buffer.flip();

                while (buffer.remaining() >= RECORD_SIZE && recordsRead < count) {
                    CurrencyRate rate = readRecordFromBuffer(buffer);
                    result.add(rate);
                    recordsRead++;

                    if (recordsRead % 10000 == 0) {
                        monitor.updatePeak();
                    }
                }

                buffer.compact();
            }

        } catch (Exception e) {
            log.error("Ошибка при чтении данных через FileChannel", e);
        }

        long endTime = System.nanoTime();
        metrics.setDuration(endTime - startTime);
        metrics.setMemoryUsedBytes(monitor.getMemoryUsed());
        metrics.setPeakMemoryBytes(monitor.getPeakMemory());

        return metrics;
    }

    private void writeRecordToBuffer(ByteBuffer buffer, CurrencyRate rate) {
        buffer.putLong(rate.getId());
        putFixedString(buffer, rate.getCurrencyPair(), 20);
        buffer.putDouble(rate.getRate());
        buffer.putDouble(rate.getVolume());
        String timestampStr = rate.getTimestamp().format(FORMATTER);
        putFixedString(buffer, timestampStr, 64);
    }

    private CurrencyRate readRecordFromBuffer(ByteBuffer buffer) {
        long id = buffer.getLong();
        String currencyPair = getFixedString(buffer, 20);
        double rate = buffer.getDouble();
        double volume = buffer.getDouble();
        String timestampStr = getFixedString(buffer, 64);

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(timestampStr, FORMATTER);
        } catch (Exception e) {
            log.warn("Ошибка парсинга даты '{}', используем текущее время", timestampStr);
            timestamp = LocalDateTime.now();
        }

        return new CurrencyRate(id, currencyPair, rate, volume, timestamp);
    }

    private void putFixedString(ByteBuffer buffer, String str, int length) {
        byte[] bytes = new byte[length];
        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        int copyLength = Math.min(strBytes.length, length);
        System.arraycopy(strBytes, 0, bytes, 0, copyLength);
        // Заполняем остаток пробелами
        for (int i = copyLength; i < length; i++) {
            bytes[i] = ' ';
        }
        buffer.put(bytes);
    }

    private String getFixedString(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8).trim();
        return result.replace("\0", "").trim();
    }
}