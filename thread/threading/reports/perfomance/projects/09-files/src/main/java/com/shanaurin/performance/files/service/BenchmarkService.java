package com.shanaurin.performance.files.service;

import com.shanaurin.performance.files.model.CurrencyRate;
import com.shanaurin.performance.files.util.PerformanceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final DataGeneratorService dataGenerator;
    private final RandomAccessFileService randomAccessFileService;
    private final FileChannelService fileChannelService;
    private final MemoryMappedFileService memoryMappedFileService;

    public void runBenchmark() {
        log.info("=".repeat(120));
        log.info("НАЧАЛО БЕНЧМАРКА I/O ОПЕРАЦИЙ");
        log.info("=".repeat(120));

        int[] dataSizes = {10_000, 50_000, 100_000};

        for (int size : dataSizes) {
            log.info("\n" + "=".repeat(120));
            log.info("ТЕСТИРОВАНИЕ С {} ЗАПИСЯМИ", size);
            log.info("=".repeat(120));

            // Генерация данных
            List<CurrencyRate> data = dataGenerator.generateData(size);

            List<PerformanceMetrics> allMetrics = new ArrayList<>();

            // Тест записи
            log.info("\n--- ТЕСТ ЗАПИСИ ---");
            allMetrics.add(randomAccessFileService.writeData(data));
            allMetrics.add(fileChannelService.writeData(data));
            allMetrics.add(memoryMappedFileService.writeData(data));

            // Тест последовательного чтения
            log.info("\n--- ТЕСТ ПОСЛЕДОВАТЕЛЬНОГО ЧТЕНИЯ ---");
            allMetrics.add(randomAccessFileService.readDataSequential(size));
            allMetrics.add(fileChannelService.readDataSequential(size));
            allMetrics.add(memoryMappedFileService.readDataSequential(size));

            // Тест случайного чтения (10% от общего количества)
            int randomReadCount = size / 10;
            log.info("\n--- ТЕСТ СЛУЧАЙНОГО ЧТЕНИЯ ({} записей) ---", randomReadCount);
            allMetrics.add(randomAccessFileService.readDataRandom(size, randomReadCount));
            allMetrics.add(memoryMappedFileService.readDataRandom(size, randomReadCount));

            // Вывод результатов
            printResults(allMetrics);
        }

        log.info("\n" + "=".repeat(120));
        log.info("БЕНЧМАРК ЗАВЕРШЕН");
        log.info("=".repeat(120));

        printAnalysis();
    }

    private void printResults(List<PerformanceMetrics> metrics) {
        log.info("\n" + "-".repeat(120));
        log.info("РЕЗУЛЬТАТЫ:");
        log.info("-".repeat(120));

        for (PerformanceMetrics metric : metrics) {
            log.info(metric.toString());
        }

        log.info("-".repeat(120));
    }

    private void printAnalysis() {
        log.info("\n" + "=".repeat(120));
        log.info("АНАЛИЗ РЕЗУЛЬТАТОВ");
        log.info("=".repeat(120));

        log.info("\n1. ПРОИЗВОДИТЕЛЬНОСТЬ ЗАПИСИ:");
        log.info("   - Memory-Mapped Files: обычно самый быстрый для больших объемов данных");
        log.info("   - FileChannel: хорошая производительность благодаря буферизации");
        log.info("   - RandomAccessFile: медленнее из-за отсутствия буферизации");

        log.info("\n2. ИСПОЛЬЗОВАНИЕ ПАМЯТИ:");
        log.info("   - Memory-Mapped Files: высокое потребление RAM, данные маппятся в память");
        log.info("   - FileChannel: умеренное потребление, буфер фиксированного размера");
        log.info("   - RandomAccessFile: минимальное потребление памяти");

        log.info("\n3. ПОСЛЕДОВАТЕЛЬНОЕ ЧТЕНИЕ:");
        log.info("   - Memory-Mapped Files: отличная производительность");
        log.info("   - FileChannel: хорошая производительность с буферизацией");
        log.info("   - RandomAccessFile: медленнее из-за множества системных вызовов");

        log.info("\n4. СЛУЧАЙНОЕ ЧТЕНИЕ:");
        log.info("   - Memory-Mapped Files: превосходная производительность (данные в памяти)");
        log.info("   - RandomAccessFile: приемлемая производительность для редких обращений");

        log.info("\n5. ПРИМЕНИМОСТЬ:");
        log.info("   - RandomAccessFile: небольшие файлы, редкие операции, ограниченная память");
        log.info("   - FileChannel: универсальное решение, хороший баланс");
        log.info("   - Memory-Mapped Files: большие файлы, частый доступ, достаточно RAM");

        log.info("\n6. РИСКИ И ОГРАНИЧЕНИЯ:");
        log.info("   - RandomAccessFile: медленный при больших объемах");
        log.info("   - FileChannel: требует правильного управления буферами");
        log.info("   - Memory-Mapped Files: может вызвать OutOfMemoryError, сложная очистка");

        log.info("=".repeat(120));
    }
}
