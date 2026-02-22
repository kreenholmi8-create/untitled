package com.shanaurin.jobparser.service;

import com.shanaurin.jobparser.config.VacancyGeneratorConfig;
import com.shanaurin.jobparser.config.VacancyGeneratorConfig.VacancyRandomGenerator;
import com.shanaurin.jobparser.model.dto.VacancyDto;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class MockVacancyCacheServiceTest {

    @Test
    void getOrCreate_shouldReturnSameInstanceForSameKey() {
        VacancyRandomGenerator generator = new VacancyGeneratorConfig().vacancyRandomGenerator();
        MockVacancyCacheService cacheService = new MockVacancyCacheService(generator);

        VacancyDto v1 = cacheService.getOrCreate("hh.ru", 1L);
        VacancyDto v2 = cacheService.getOrCreate("hh.ru", 1L);

        assertThat(v1).isSameAs(v2);
    }

    @Test
    void getOrCreate_shouldBeThreadSafe() throws Exception {
        VacancyRandomGenerator generator = new VacancyGeneratorConfig().vacancyRandomGenerator();
        MockVacancyCacheService cacheService = new MockVacancyCacheService(generator);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        int tasks = 50;
        Callable<VacancyDto> task = () -> cacheService.getOrCreate("hh.ru", 42L);
        Future<VacancyDto>[] futures = new Future[tasks];
        for (int i = 0; i < tasks; i++) {
            futures[i] = executor.submit(task);
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        VacancyDto first = futures[0].get();
        for (int i = 1; i < tasks; i++) {
            assertThat(futures[i].get()).isSameAs(first);
        }
    }
}