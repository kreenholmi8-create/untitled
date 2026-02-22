package com.kolmykova.jobparser.domain.port.out;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;
import com.kolmykova.jobparser.domain.model.VacancyDomain;

import java.util.List;

/**
 * Выходной порт для публикации результатов анализа.
 * Ядро не знает, куда сохраняются результаты (БД, файл, REST, консоль).
 */
public interface VacancyResultPublisher {

    /**
     * Опубликовать результат анализа
     */
    void publishAnalysisResult(VacancyAnalysisResult result);

    /**
     * Сохранить обработанные вакансии
     */
    void saveVacancies(List<VacancyDomain> vacancies);

    /**
     * Получить название публикатора (для логирования)
     */
    String getPublisherName();
}