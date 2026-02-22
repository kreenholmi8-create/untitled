package com.kolmykova.jobparser.adapter.out.rest;

import com.kolmykova.jobparser.domain.model.VacancyDomain;
import com.kolmykova.jobparser.domain.port.out.VacancyDataSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Адаптер для получения вакансий из внешнего REST API.
 * Использует WebClient для HTTP-запросов.
 */
public class RestApiVacancyDataSource implements VacancyDataSource {

    private static final String SOURCE_NAME = "REST_API";

    private final WebClient webClient;
    private final String baseUrl;

    public RestApiVacancyDataSource(WebClient webClient, String baseUrl) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<VacancyDomain> fetchAll() {
        try {
            List<Map<String, Object>> response = webClient.get()
                    .uri(baseUrl + "/api/vacancies")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .cast(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .collectList()
                    .block();

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(this::mapToDomain)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error fetching from REST API: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<VacancyDomain> fetchByCity(String city) {
        try {
            List<Map<String, Object>> response = webClient.get()
                    .uri(baseUrl + "/api/vacancies?city=" + city)
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .cast(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .collectList()
                    .block();

            if (response == null) {
                return Collections.emptyList();
            }

            return response.stream()
                    .map(this::mapToDomain)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error fetching by city from REST API: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<VacancyDomain> fetchById(Long id) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri(baseUrl + "/api/vacancies/" + id)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(mapToDomain(response));

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            webClient.get()
                    .uri(baseUrl + "/actuator/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME + ":" + baseUrl;
    }

    private VacancyDomain mapToDomain(Map<String, Object> map) {
        VacancyDomain domain = new VacancyDomain();

        domain.setId(getLong(map, "id"));
        domain.setSource((String) map.get("source"));
        domain.setUrl((String) map.get("url"));
        domain.setTitle((String) map.get("title"));
        domain.setCompany((String) map.get("company"));
        domain.setCity((String) map.get("city"));
        domain.setSalaryRaw((String) map.get("salary"));
        domain.setRequirements((String) map.get("requirements"));

        String publishedAt = (String) map.get("publishedAt");
        if (publishedAt != null) {
            domain.setPublishedAt(LocalDateTime.parse(publishedAt));
        }

        String createdAt = (String) map.get("createdAt");
        if (createdAt != null) {
            domain.setCreatedAt(LocalDateTime.parse(createdAt));
        }

        return domain;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}