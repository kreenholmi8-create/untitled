package com.kolmykova.jobparser.adapter.out.console;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;
import com.kolmykova.jobparser.domain.model.VacancyDomain;
import com.kolmykova.jobparser.domain.port.out.VacancyResultPublisher;

import java.util.List;

/**
 * Адаптер для вывода результатов в консоль.
 * Полезен для тестирования и отладки.
 */
public class ConsoleResultPublisher implements VacancyResultPublisher {

    private static final String PUBLISHER_NAME = "CONSOLE";

    @Override
    public void publishAnalysisResult(VacancyAnalysisResult result) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║          РЕЗУЛЬТАТ АНАЛИЗА ВАКАНСИЙ                        ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Дата анализа:      %s%n", result.getAnalyzedAt());
        System.out.printf("║ Всего вакансий:    %d%n", result.getTotalVacancies());
        System.out.printf("║ Средняя зарплата:  %.0f руб.%n",
                result.getAverageSalary() != null ? result.getAverageSalary() : 0.0);
        System.out.printf("║ Мин. зарплата:     %s руб.%n",
                result.getMinSalary() != null ? result.getMinSalary() : "N/A");
        System.out.printf("║ Макс. зарплата:    %s руб.%n",
                result.getMaxSalary() != null ? result.getMaxSalary() : "N/A");
        System.out.printf("║ Свежих вакансий:   %d%n", result.getRecentVacanciesCount());
        System.out.printf("║ Тональность:       %s%n", result.getSentimentSummary());
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║ Распределение по городам:");
        result.getVacanciesByCity().forEach((city, count) ->
                System.out.printf("║   • %s: %d%n", city, count));
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║ Распределение по уровням:");
        result.getVacanciesBySeniority().forEach((level, count) ->
                System.out.printf("║   • %s: %d%n", level, count));
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    @Override
    public void saveVacancies(List<VacancyDomain> vacancies) {
        System.out.println("[" + PUBLISHER_NAME + "] Saving " + vacancies.size() + " vacancies:");
        for (VacancyDomain v : vacancies) {
            System.out.printf("  - [%d] %s @ %s (%s)%n",
                    v.getId(), v.getTitle(), v.getCompany(), v.getCity());
        }
    }

    @Override
    public String getPublisherName() {
        return PUBLISHER_NAME;
    }
}