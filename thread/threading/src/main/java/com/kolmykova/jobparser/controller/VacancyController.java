package com.kolmykova.jobparser.controller;

import com.kolmykova.jobparser.model.dto.VacancyDto;
import com.kolmykova.jobparser.service.VacancyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// controller/VacancyController.java
@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

    private final VacancyService vacancyService;

    public VacancyController(VacancyService vacancyService) {
        this.vacancyService = vacancyService;
    }

    @GetMapping
    public List<VacancyDto> getVacancies(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String company,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return vacancyService.getVacancies(city, company, sortBy, direction, page, size);
    }
}