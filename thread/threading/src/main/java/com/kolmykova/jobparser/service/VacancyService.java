package com.kolmykova.jobparser.service;

import com.kolmykova.jobparser.model.Vacancy;
import com.kolmykova.jobparser.model.dto.VacancyDto;
import com.kolmykova.jobparser.repository.VacancyRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.time.LocalDateTime;

// service/VacancyService.java
@Service
public class VacancyService {

    private final VacancyRepository vacancyRepository;

    public VacancyService(VacancyRepository vacancyRepository) {
        this.vacancyRepository = vacancyRepository;
    }

    public List<VacancyDto> getVacancies(
            String city,
            String company,
            String sortBy,
            String direction,
            int page,
            int size
    ) {
        List<Vacancy> all = vacancyRepository.findAll();

        Comparator<Vacancy> comparator = getComparator(sortBy, direction);

        return all.parallelStream()
                .filter(v -> city == null || v.getCity().equalsIgnoreCase(city))
                .filter(v -> company == null || v.getCompany().equalsIgnoreCase(company))
                .sorted(comparator)
                .skip((long) page * size)
                .limit(size)
                .map(this::toDto)
                .toList();
    }

    private Comparator<Vacancy> getComparator(String sortBy, String direction) {
        Comparator<Vacancy> comp;
        switch (sortBy) {
            case "salary" -> comp = Comparator.comparing(Vacancy::getSalary, Comparator.nullsLast(String::compareTo));
            case "publishedAt" -> comp = Comparator.comparing(Vacancy::getPublishedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            default -> comp = Comparator.comparing(Vacancy::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
        }
        if ("DESC".equalsIgnoreCase(direction)) {
            comp = comp.reversed();
        }
        return comp;
    }

    private VacancyDto toDto(Vacancy v) {
        VacancyDto dto = new VacancyDto();
        dto.setId(v.getId());
        dto.setSource(v.getSource());
        dto.setUrl(v.getUrl());
        dto.setTitle(v.getTitle());
        dto.setCompany(v.getCompany());
        dto.setCity(v.getCity());
        dto.setSalary(v.getSalary());
        dto.setRequirements(v.getRequirements());
        dto.setPublishedAt(v.getPublishedAt());
        dto.setCreatedAt(v.getCreatedAt());
        return dto;
    }
}