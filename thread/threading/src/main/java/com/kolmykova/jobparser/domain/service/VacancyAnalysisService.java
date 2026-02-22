package com.kolmykova.jobparser.domain.service;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;
import com.kolmykova.jobparser.domain.model.VacancyDomain;
import com.kolmykova.jobparser.domain.port.in.AnalyzeVacancyUseCase;
import com.kolmykova.jobparser.domain.port.out.VacancyDataSource;
import com.kolmykova.jobparser.domain.port.out.VacancyResultPublisher;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Доменный сервис - ЯДРО бизнес-логики.
 *
 * ВАЖНО: Этот класс НЕ содержит Spring-аннотаций (@Service, @Autowired и т.д.)
 * и не зависит от инфраструктуры. Все зависимости инжектируются через конструктор
 * в виде интерфейсов (портов).
 */
public class VacancyAnalysisService implements AnalyzeVacancyUseCase {

    private final VacancyDataSource dataSource;
    private final VacancyResultPublisher resultPublisher;

    /**
     * Конструктор принимает порты (интерфейсы), а не конкретные реализации.
     * Это позволяет подменять адаптеры без изменения ядра.
     */
    public VacancyAnalysisService(VacancyDataSource dataSource,
                                  VacancyResultPublisher resultPublisher) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.resultPublisher = Objects.requireNonNull(resultPublisher, "resultPublisher must not be null");
    }

    @Override
    public VacancyAnalysisResult analyzeAll(int recentDaysThreshold) {
        List<VacancyDomain> vacancies = dataSource.fetchAll();

        if (vacancies.isEmpty()) {
            return createEmptyResult();
        }

        // Расчёт средней зарплаты
        Double averageSalary = calculateOverallAverageSalary(vacancies);

        // Минимальная и максимальная зарплата
        Integer minSalary = findMinSalary(vacancies);
        Integer maxSalary = findMaxSalary(vacancies);

        // Группировка по городам
        Map<String, Long> byCity = groupByCity(vacancies);

        // Группировка по компаниям
        Map<String, Long> byCompany = groupByCompany(vacancies);

        // Группировка по уровню
        Map<VacancyDomain.SeniorityLevel, Long> bySeniority = groupBySeniority(vacancies);

        // Количество свежих вакансий
        int recentCount = countRecent(vacancies, recentDaysThreshold);

        // Анализ тональности требований
        String sentiment = analyzeOverallSentiment(vacancies);

        VacancyAnalysisResult result = new VacancyAnalysisResult(
                vacancies.size(),
                averageSalary,
                minSalary,
                maxSalary,
                byCity,
                byCompany,
                bySeniority,
                recentCount,
                sentiment
        );

        // Публикуем результат через выходной порт
        resultPublisher.publishAnalysisResult(result);

        return result;
    }

    @Override
    public Double calculateAverageSalaryByCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }

        List<VacancyDomain> vacancies = dataSource.fetchByCity(city);
        return calculateOverallAverageSalary(vacancies);
    }

    @Override
    public String analyzeRequirementsSentiment(List<String> keywords) {
        List<VacancyDomain> vacancies = dataSource.fetchAll();

        if (vacancies == null || vacancies.isEmpty()) {
            return "NEUTRAL";
        }

        int positiveCount = 0;
        int negativeCount = 0;

        // Позитивные индикаторы
        List<String> positiveIndicators = List.of(
                "интересный", "развитие", "обучение", "карьера", "рост",
                "гибкий", "удалёнка", "бонус", "премия", "дмс"
        );

        // Негативные индикаторы
        List<String> negativeIndicators = List.of(
                "срочно", "стресс", "переработки", "ненормированный",
                "обязательно", "строго", "штраф"
        );

        for (VacancyDomain vacancy : vacancies) {
            String req = vacancy.getRequirements();
            if (req == null) continue;

            String lowerReq = req.toLowerCase();

            for (String indicator : positiveIndicators) {
                if (lowerReq.contains(indicator)) {
                    positiveCount++;
                }
            }

            for (String indicator : negativeIndicators) {
                if (lowerReq.contains(indicator)) {
                    negativeCount++;
                }
            }
        }

        if (positiveCount > negativeCount * 2) {
            return "POSITIVE - Вакансии содержат много позитивных условий";
        } else if (negativeCount > positiveCount * 2) {
            return "NEGATIVE - Вакансии содержат много негативных индикаторов";
        } else {
            return "NEUTRAL - Сбалансированные требования";
        }
    }

    // ========== Приватные методы бизнес-логики ==========

    private Double calculateOverallAverageSalary(List<VacancyDomain> vacancies) {
        return vacancies.stream()
                .map(VacancyDomain::calculateAverageSalary)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    private Integer findMinSalary(List<VacancyDomain> vacancies) {
        return vacancies.stream()
                .map(VacancyDomain::getSalaryMin)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private Integer findMaxSalary(List<VacancyDomain> vacancies) {
        return vacancies.stream()
                .map(VacancyDomain::getSalaryMax)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private Map<String, Long> groupByCity(List<VacancyDomain> vacancies) {
        return vacancies.stream()
                .filter(v -> v.getCity() != null)
                .collect(Collectors.groupingBy(
                        VacancyDomain::getCity,
                        Collectors.counting()
                ));
    }

    private Map<String, Long> groupByCompany(List<VacancyDomain> vacancies) {
        return vacancies.stream()
                .filter(v -> v.getCompany() != null)
                .collect(Collectors.groupingBy(
                        VacancyDomain::getCompany,
                        Collectors.counting()
                ));
    }

    private Map<VacancyDomain.SeniorityLevel, Long> groupBySeniority(List<VacancyDomain> vacancies) {
        return vacancies.stream()
                .collect(Collectors.groupingBy(
                        VacancyDomain::determineSeniorityLevel,
                        Collectors.counting()
                ));
    }

    private int countRecent(List<VacancyDomain> vacancies, int daysThreshold) {
        return (int) vacancies.stream()
                .filter(v -> v.isRecent(daysThreshold))
                .count();
    }

    private String analyzeOverallSentiment(List<VacancyDomain> vacancies) {
        return analyzeRequirementsSentiment(List.of());
    }

    private VacancyAnalysisResult createEmptyResult() {
        return new VacancyAnalysisResult(
                0, 0.0, null, null,
                Map.of(), Map.of(), Map.of(),
                0, "NO_DATA"
        );
    }
}