package com.kolmykova.jobparser.adapter.out.persistence;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;
import com.kolmykova.jobparser.domain.model.VacancyDomain;
import com.kolmykova.jobparser.domain.port.out.VacancyResultPublisher;
import com.kolmykova.jobparser.model.Vacancy;
import com.kolmykova.jobparser.repository.VacancyRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Адаптер для сохранения результатов в базу данных через JPA.
 */
public class JpaVacancyResultPublisher implements VacancyResultPublisher {

    private static final String PUBLISHER_NAME = "JPA_DATABASE";

    private final VacancyRepository vacancyRepository;

    public JpaVacancyResultPublisher(VacancyRepository vacancyRepository) {
        this.vacancyRepository = vacancyRepository;
    }

    @Override
    public void publishAnalysisResult(VacancyAnalysisResult result) {
        // Для демонстрации: логируем результат
        // В реальном приложении можно сохранить в отдельную таблицу AnalysisResult
        System.out.println("[" + PUBLISHER_NAME + "] Analysis result published: " + result);
    }

    @Override
    public void saveVacancies(List<VacancyDomain> vacancies) {
        List<Vacancy> entities = vacancies.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());

        vacancyRepository.saveAll(entities);
        System.out.println("[" + PUBLISHER_NAME + "] Saved " + entities.size() + " vacancies");
    }

    @Override
    public String getPublisherName() {
        return PUBLISHER_NAME;
    }

    /**
     * Конвертация доменной модели в JPA-сущность
     */
    private Vacancy toEntity(VacancyDomain domain) {
        Vacancy entity = new Vacancy();
        entity.setId(domain.getId());
        entity.setSource(domain.getSource());
        entity.setUrl(domain.getUrl());
        entity.setTitle(domain.getTitle());
        entity.setCompany(domain.getCompany());
        entity.setCity(domain.getCity());
        entity.setSalary(domain.getSalaryRaw());
        entity.setRequirements(domain.getRequirements());
        entity.setPublishedAt(domain.getPublishedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}