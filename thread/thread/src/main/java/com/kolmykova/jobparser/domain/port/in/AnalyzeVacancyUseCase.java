package com.kolmykova.jobparser.domain.port.in;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;

import java.util.List;

/**
 * Входной порт для анализа вакансий.
 * Определяет контракт use case без деталей реализации.
 */
public interface AnalyzeVacancyUseCase {

    /**
     * Выполнить полный анализ вакансий из источника данных
     * @param recentDaysThreshold количество дней для определения "свежести" вакансии
     * @return результат анализа
     */
    VacancyAnalysisResult analyzeAll(int recentDaysThreshold);

    /**
     * Рассчитать среднюю зарплату по городу
     * @param city название города
     * @return средняя зарплата или null, если данных нет
     */
    Double calculateAverageSalaryByCity(String city);

    /**
     * Определить тональность требований в вакансиях
     * @param keywords список ключевых слов для поиска
     * @return текстовое описание тональности
     */
    String analyzeRequirementsSentiment(List<String> keywords);
}