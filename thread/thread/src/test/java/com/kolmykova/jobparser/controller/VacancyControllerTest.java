package com.kolmykova.jobparser.controller;

import com.kolmykova.jobparser.model.dto.VacancyDto;
import com.kolmykova.jobparser.service.VacancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VacancyControllerTest {

    private MockMvc mockMvc;
    private VacancyService vacancyService;

    @BeforeEach
    void setUp() {
        vacancyService = mock(VacancyService.class);
        VacancyController controller = new VacancyController(vacancyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getVacancies_shouldReturnJsonList() throws Exception {
        VacancyDto dto = new VacancyDto(
                1L, "hh.ru", "url", "Java Dev", "ООО Ромашка",
                "Москва", "100k", "Требования",
                LocalDateTime.now(), LocalDateTime.now()
        );

        when(vacancyService.getVacancies(
                any(), any(), anyString(), anyString(), anyInt(), anyInt()
        )).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/vacancies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].title").value("Java Dev"));
    }
}