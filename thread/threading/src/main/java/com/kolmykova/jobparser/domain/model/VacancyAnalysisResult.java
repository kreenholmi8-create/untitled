package com.kolmykova.jobparser.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Результат анализа вакансий.
 * Чистая доменная модель без инфраструктурных зависимостей.
 */
public class VacancyAnalysisResult {

    private final LocalDateTime analyzedAt;
    private final int totalVacancies;
    private final Double averageSalary;
    private final Integer minSalary;
    private final Integer maxSalary;
    private final Map<String, Long> vacanciesByCity;
    private final Map<String, Long> vacanciesByCompany;
    private final Map<VacancyDomain.SeniorityLevel, Long> vacanciesBySeniority;
    private final int recentVacanciesCount;
    private final String sentimentSummary;

    public VacancyAnalysisResult(
            int totalVacancies,
            Double averageSalary,
            Integer minSalary,
            Integer maxSalary,
            Map<String, Long> vacanciesByCity,
            Map<String, Long> vacanciesByCompany,
            Map<VacancyDomain.SeniorityLevel, Long> vacanciesBySeniority,
            int recentVacanciesCount,
            String sentimentSummary) {
        this.analyzedAt = LocalDateTime.now();
        this.totalVacancies = totalVacancies;
        this.averageSalary = averageSalary;
        this.minSalary = minSalary;
        this.maxSalary = maxSalary;
        this.vacanciesByCity = vacanciesByCity;
        this.vacanciesByCompany = vacanciesByCompany;
        this.vacanciesBySeniority = vacanciesBySeniority;
        this.recentVacanciesCount = recentVacanciesCount;
        this.sentimentSummary = sentimentSummary;
    }

    // Геттеры
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public int getTotalVacancies() { return totalVacancies; }
    public Double getAverageSalary() { return averageSalary; }
    public Integer getMinSalary() { return minSalary; }
    public Integer getMaxSalary() { return maxSalary; }
    public Map<String, Long> getVacanciesByCity() { return vacanciesByCity; }
    public Map<String, Long> getVacanciesByCompany() { return vacanciesByCompany; }
    public Map<VacancyDomain.SeniorityLevel, Long> getVacanciesBySeniority() { return vacanciesBySeniority; }
    public int getRecentVacanciesCount() { return recentVacanciesCount; }
    public String getSentimentSummary() { return sentimentSummary; }

    @Override
    public String toString() {
        return String.format(
                "VacancyAnalysisResult{analyzedAt=%s, total=%d, avgSalary=%.0f, cities=%s, sentiment='%s'}",
                analyzedAt, totalVacancies, averageSalary, vacanciesByCity.keySet(), sentimentSummary
        );
    }
}