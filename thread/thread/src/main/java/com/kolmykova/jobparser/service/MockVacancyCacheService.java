package com.kolmykova.jobparser.service;

import com.kolmykova.jobparser.config.VacancyGeneratorConfig.VacancyRandomGenerator;
import com.kolmykova.jobparser.model.dto.VacancyDto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockVacancyCacheService {

    private final VacancyRandomGenerator vacancyRandomGenerator;

    // key = type + ":" + id
    private final Map<String, VacancyDto> cache = new ConcurrentHashMap<>();

    public MockVacancyCacheService(VacancyRandomGenerator vacancyRandomGenerator) {
        this.vacancyRandomGenerator = vacancyRandomGenerator;
    }

    public VacancyDto getOrCreate(String type, Long id) {
        String key = type + ":" + id;
        return cache.computeIfAbsent(key, k -> vacancyRandomGenerator.generateRandomVacancy(type, id));
    }
}