package com.kolmykova.jobparser.config;

import com.kolmykova.jobparser.adapter.out.console.ConsoleResultPublisher;
import com.kolmykova.jobparser.adapter.out.file.FileResultPublisher;
import com.kolmykova.jobparser.adapter.out.file.FileVacancyDataSource;
import com.kolmykova.jobparser.adapter.out.persistence.JpaVacancyDataSource;
import com.kolmykova.jobparser.adapter.out.persistence.JpaVacancyResultPublisher;
import com.kolmykova.jobparser.adapter.out.rest.RestApiVacancyDataSource;
import com.kolmykova.jobparser.domain.port.in.AnalyzeVacancyUseCase;
import com.kolmykova.jobparser.domain.port.out.VacancyDataSource;
import com.kolmykova.jobparser.domain.port.out.VacancyResultPublisher;
import com.kolmykova.jobparser.domain.service.VacancyAnalysisService;
import com.kolmykova.jobparser.repository.VacancyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Path;

/**
 * Конфигурация Hexagonal Architecture.
 *
 * Здесь происходит "сборка" приложения: связывание портов с адаптерами.
 * Ядро (VacancyAnalysisService) не знает о конкретных реализациях.
 */
@Configuration
public class HexagonalConfig {

    // ========== DATA SOURCE ADAPTERS (Выходные порты для получения данных) ==========

    /**
     * Адаптер для чтения из базы данных - PRIMARY (основной)
     */
    @Bean
    @Primary
    public VacancyDataSource jpaVacancyDataSource(VacancyRepository vacancyRepository) {
        return new JpaVacancyDataSource(vacancyRepository);
    }

    /**
     * Адаптер для чтения из файла
     */
    @Bean
    public VacancyDataSource fileVacancyDataSource(
            @Value("${hexagonal.file.input:data/vacancies.csv}") String filePath) {
        return new FileVacancyDataSource(filePath);
    }

    /**
     * Адаптер для чтения из REST API
     */
    @Bean
    public VacancyDataSource restApiVacancyDataSource(
            WebClient webClient,
            @Value("${hexagonal.rest.baseUrl:http://localhost:8080}") String baseUrl) {
        return new RestApiVacancyDataSource(webClient, baseUrl);
    }

    // ========== RESULT PUBLISHER ADAPTERS (Выходные порты для публикации) ==========

    /**
     * Адаптер для сохранения в БД - PRIMARY (основной)
     */
    @Bean
    @Primary
    public VacancyResultPublisher jpaResultPublisher(VacancyRepository vacancyRepository) {
        return new JpaVacancyResultPublisher(vacancyRepository);
    }

    /**
     * Адаптер для вывода в консоль
     */
    @Bean
    public VacancyResultPublisher consoleResultPublisher() {
        return new ConsoleResultPublisher();
    }

    /**
     * Адаптер для сохранения в файл
     */
    @Bean
    public VacancyResultPublisher fileResultPublisher(
            @Value("${hexagonal.file.output:data/output}") String outputPath) {
        return new FileResultPublisher(Path.of(outputPath));
    }

    // ========== DOMAIN SERVICE (Ядро) ==========

    /**
     * Доменный сервис - ядро бизнес-логики.
     * Получает зависимости через порты (интерфейсы).
     *
     * Обратите внимание: здесь используются @Primary бины,
     * но можно легко переключиться на другие адаптеры.
     */
    @Bean
    public AnalyzeVacancyUseCase analyzeVacancyUseCase(
            VacancyDataSource dataSource,           // @Primary -> JpaVacancyDataSource
            VacancyResultPublisher resultPublisher  // @Primary -> JpaVacancyResultPublisher
    ) {
        return new VacancyAnalysisService(dataSource, resultPublisher);
    }

    // ========== АЛЬТЕРНАТИВНЫЕ КОНФИГУРАЦИИ ==========

    /**
     * Use case с файловыми адаптерами (для демонстрации подмены)
     */
    @Bean
    public AnalyzeVacancyUseCase fileBasedAnalyzeUseCase(
            @Value("${hexagonal.file.input:data/vacancies.csv}") String inputPath,
            @Value("${hexagonal.file.output:data/output}") String outputPath) {

        VacancyDataSource fileSource = new FileVacancyDataSource(inputPath);
        VacancyResultPublisher filePublisher = new FileResultPublisher(Path.of(outputPath));

        return new VacancyAnalysisService(fileSource, filePublisher);
    }

    /**
     * Use case с консольным выводом (для отладки)
     */
    @Bean
    public AnalyzeVacancyUseCase consoleAnalyzeUseCase(
            VacancyDataSource dataSource) {

        VacancyResultPublisher consolePublisher = new ConsoleResultPublisher();
        return new VacancyAnalysisService(dataSource, consolePublisher);
    }
}
