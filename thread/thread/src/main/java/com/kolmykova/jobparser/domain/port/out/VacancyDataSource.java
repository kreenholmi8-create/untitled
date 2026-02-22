package com.kolmykova.jobparser.domain.port.out;

import com.kolmykova.jobparser.domain.model.VacancyDomain;

import java.util.List;
import java.util.Optional;

/**
 * Выходной порт для получения данных о вакансиях.
 * Ядро не знает, откуда приходят данные (файл, БД, REST API).
 */
public interface VacancyDataSource {

    /**
     * Получить все вакансии из источника
     */
    List<VacancyDomain> fetchAll();

    /**
     * Получить вакансии по городу
     */
    List<VacancyDomain> fetchByCity(String city);

    /**
     * Получить вакансию по ID
     */
    Optional<VacancyDomain> fetchById(Long id);

    /**
     * Проверить доступность источника
     */
    boolean isAvailable();

    /**
     * Получить название источника (для логирования)
     */
    String getSourceName();
}