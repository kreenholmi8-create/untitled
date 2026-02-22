package com.shanaurin.performance.files.runner;

import com.shanaurin.performance.files.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkRunner implements CommandLineRunner {

    private final BenchmarkService benchmarkService;

    @Override
    public void run(String... args) {
        log.info("Запуск бенчмарка производительности файловых операций...");
        benchmarkService.runBenchmark();
        log.info("Бенчмарк завершен успешно");
    }
}
