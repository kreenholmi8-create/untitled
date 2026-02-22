package com.shanaurin.jobparser.service;

import com.shanaurin.jobparser.model.Vacancy;
import com.shanaurin.jobparser.model.dto.VacancyDto;
import com.shanaurin.jobparser.repository.VacancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class VacancyServiceTest {

    private VacancyRepository vacancyRepository;
    private VacancyService vacancyService;

    @BeforeEach
    void setUp() {
        vacancyRepository = Mockito.mock(VacancyRepository.class);
        vacancyService = new VacancyService(vacancyRepository);
    }

    @Test
    void getVacancies_shouldFilterByCityAndCompanyAndPaginate() {
        Vacancy v1 = Vacancy.builder()
                .id(1L).city("Москва").company("ООО Ромашка")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        Vacancy v2 = Vacancy.builder()
                .id(2L).city("Санкт-Петербург").company("ООО Ромашка")
                .createdAt(LocalDateTime.now())
                .build();
        Vacancy v3 = Vacancy.builder()
                .id(3L).city("Москва").company("ООО Тест")
                .createdAt(LocalDateTime.now().minusHours(5))
                .build();

        when(vacancyRepository.findAll()).thenReturn(List.of(v1, v2, v3));

        List<VacancyDto> result = vacancyService.getVacancies(
                "Москва",
                "ООО Ромашка",
                "createdAt",
                "ASC",
                0,
                10
        );

        assertThat(result)
                .hasSize(1)
                .first()
                .extracting(VacancyDto::getId)
                .isEqualTo(1L);
    }

    @Test
    void getVacancies_shouldSortByPublishedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        Vacancy v1 = Vacancy.builder().id(1L).city("Москва").company("A")
                .publishedAt(now.minusDays(2)).createdAt(now.minusDays(5)).build();
        Vacancy v2 = Vacancy.builder().id(2L).city("Москва").company("A")
                .publishedAt(now).createdAt(now.minusDays(3)).build();

        when(vacancyRepository.findAll()).thenReturn(List.of(v1, v2));

        List<VacancyDto> result = vacancyService.getVacancies(
                "Москва",
                "A",
                "publishedAt",
                "DESC",
                0,
                10
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        assertThat(result.get(1).getId()).isEqualTo(1L);
    }

    @Test
    void getVacancies_shouldApplyPagination() {
        LocalDateTime now = LocalDateTime.now();
        Vacancy v1 = Vacancy.builder().id(1L).city("Москва").company("A").createdAt(now.minusDays(3)).build();
        Vacancy v2 = Vacancy.builder().id(2L).city("Москва").company("A").createdAt(now.minusDays(2)).build();
        Vacancy v3 = Vacancy.builder().id(3L).city("Москва").company("A").createdAt(now.minusDays(1)).build();

        when(vacancyRepository.findAll()).thenReturn(List.of(v1, v2, v3));

        // страница 1, размер 1 => только вторая по createdAt ASC
        List<VacancyDto> result = vacancyService.getVacancies(
                "Москва",
                "A",
                "createdAt",
                "ASC",
                1,
                1
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L);
    }
}