package com.kolmykova.jobparser.domain.service;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;
import com.kolmykova.jobparser.domain.model.VacancyDomain;
import com.kolmykova.jobparser.domain.port.out.VacancyDataSource;
import com.kolmykova.jobparser.domain.port.out.VacancyResultPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для доменного сервиса.
 *
 * ВАЖНО: Эти тесты НЕ используют Spring!
 * Порты мокаются с помощью Mockito, что доказывает
 * независимость ядра от инфраструктуры.
 */
class VacancyAnalysisServiceTest {

    private VacancyDataSource mockDataSource;
    private VacancyResultPublisher mockPublisher;
    private VacancyAnalysisService service;

    @BeforeEach
    void setUp() {
        // Создаём моки портов
        mockDataSource = mock(VacancyDataSource.class);
        mockPublisher = mock(VacancyResultPublisher.class);

        // Создаём сервис с моками (без Spring!)
        service = new VacancyAnalysisService(mockDataSource, mockPublisher);
    }

    @Test
    @DisplayName("Конструктор должен выбрасывать исключение при null dataSource")
    void constructor_ShouldThrowException_WhenDataSourceIsNull() {
        assertThrows(NullPointerException.class, () ->
                new VacancyAnalysisService(null, mockPublisher)
        );
    }

    @Test
    @DisplayName("Конструктор должен выбрасывать исключение при null publisher")
    void constructor_ShouldThrowException_WhenPublisherIsNull() {
        assertThrows(NullPointerException.class, () ->
                new VacancyAnalysisService(mockDataSource, null)
        );
    }

    @Test
    @DisplayName("analyzeAll должен вернуть пустой результат при отсутствии данных")
    void analyzeAll_ShouldReturnEmptyResult_WhenNoVacancies() {
        // Given
        when(mockDataSource.fetchAll()).thenReturn(List.of());

        // When
        VacancyAnalysisResult result = service.analyzeAll(30);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getTotalVacancies());
        assertEquals(0.0, result.getAverageSalary());
        assertNull(result.getMinSalary());
        assertNull(result.getMaxSalary());
        assertEquals("NO_DATA", result.getSentimentSummary());

        // Verify взаимодействие с портами
        verify(mockDataSource, times(1)).fetchAll();
        verify(mockPublisher, never()).publishAnalysisResult(any());
    }

    @Test
    @DisplayName("analyzeAll должен корректно рассчитать среднюю зарплату")
    void analyzeAll_ShouldCalculateAverageSalary_Correctly() {
        // Given
        List<VacancyDomain> vacancies = createTestVacancies();
        when(mockDataSource.fetchAll()).thenReturn(vacancies);

        // When
        VacancyAnalysisResult result = service.analyzeAll(30);

        // Then
        assertEquals(3, result.getTotalVacancies());
        assertNotNull(result.getAverageSalary());
        assertTrue(result.getAverageSalary() > 0);

        // Публикация должна быть вызвана
        verify(mockPublisher, times(1)).publishAnalysisResult(result);
    }

    @Test
    @DisplayName("analyzeAll должен корректно группировать по городам")
    void analyzeAll_ShouldGroupByCity_Correctly() {
        // Given
        List<VacancyDomain> vacancies = createTestVacancies();
        when(mockDataSource.fetchAll()).thenReturn(vacancies);

        // When
        VacancyAnalysisResult result = service.analyzeAll(30);

        // Then
        assertTrue(result.getVacanciesByCity().containsKey("Москва"));
        assertTrue(result.getVacanciesByCity().containsKey("Санкт-Петербург"));
        assertEquals(2L, result.getVacanciesByCity().get("Москва"));
        assertEquals(1L, result.getVacanciesByCity().get("Санкт-Петербург"));
    }

    @Test
    @DisplayName("analyzeAll должен корректно группировать по уровню позиции")
    void analyzeAll_ShouldGroupBySeniority_Correctly() {
        // Given
        List<VacancyDomain> vacancies = createTestVacancies();
        when(mockDataSource.fetchAll()).thenReturn(vacancies);

        // When
        VacancyAnalysisResult result = service.analyzeAll(30);

        // Then
        assertNotNull(result.getVacanciesBySeniority());
        assertTrue(result.getVacanciesBySeniority().containsKey(VacancyDomain.SeniorityLevel.SENIOR));
        assertTrue(result.getVacanciesBySeniority().containsKey(VacancyDomain.SeniorityLevel.JUNIOR));
    }

    @Test
    @DisplayName("calculateAverageSalaryByCity должен вернуть null для пустого города")
    void calculateAverageSalaryByCity_ShouldReturnNull_ForEmptyCity() {
        // When
        Double result = service.calculateAverageSalaryByCity("");

        // Then
        assertNull(result);
        verify(mockDataSource, never()).fetchByCity(any());
    }

    @Test
    @DisplayName("calculateAverageSalaryByCity должен вернуть null для null города")
    void calculateAverageSalaryByCity_ShouldReturnNull_ForNullCity() {
        // When
        Double result = service.calculateAverageSalaryByCity(null);

        // Then
        assertNull(result);
        verify(mockDataSource, never()).fetchByCity(any());
    }

    @Test
    @DisplayName("calculateAverageSalaryByCity должен вызывать dataSource.fetchByCity")
    void calculateAverageSalaryByCity_ShouldCallDataSource() {
        // Given
        String city = "Москва";
        VacancyDomain vacancy = createVacancy(1L, "Java Developer", "Москва",
                "от 200 000 до 300 000 руб.");
        when(mockDataSource.fetchByCity(city)).thenReturn(List.of(vacancy));

        // When
        Double result = service.calculateAverageSalaryByCity(city);

        // Then
        assertNotNull(result);
        assertEquals(250000.0, result, 0.01);
        verify(mockDataSource, times(1)).fetchByCity(city);
    }

    @Test
    @DisplayName("analyzeRequirementsSentiment должен вернуть NEUTRAL для пустого списка")
    void analyzeRequirementsSentiment_ShouldReturnNeutral_ForEmptyKeywords() {
        // Given
        when(mockDataSource.fetchAll()).thenReturn(List.of());

        // When
        String result = service.analyzeRequirementsSentiment(List.of());

        // Then
        assertEquals("NEUTRAL", result);
    }

    @Test
    @DisplayName("analyzeRequirementsSentiment должен определять позитивную тональность")
    void analyzeRequirementsSentiment_ShouldDetectPositive() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setRequirements("Интересный проект, возможность обучения и карьерного роста, " +
                "гибкий график, ДМС, бонусы");
        when(mockDataSource.fetchAll()).thenReturn(List.of(vacancy));

        // When
        String result = service.analyzeRequirementsSentiment(List.of());

        // Then
        assertTrue(result.contains("POSITIVE"));
    }

    @Test
    @DisplayName("Результат анализа должен публиковаться через publisher")
    void analyzeAll_ShouldPublishResult() {
        // Given
        List<VacancyDomain> vacancies = createTestVacancies();
        when(mockDataSource.fetchAll()).thenReturn(vacancies);

        ArgumentCaptor<VacancyAnalysisResult> captor =
                ArgumentCaptor.forClass(VacancyAnalysisResult.class);

        // When
        service.analyzeAll(30);

        // Then
        verify(mockPublisher).publishAnalysisResult(captor.capture());
        VacancyAnalysisResult captured = captor.getValue();

        assertNotNull(captured);
        assertEquals(3, captured.getTotalVacancies());
    }

    // ========== Вспомогательные методы ==========

    private List<VacancyDomain> createTestVacancies() {
        return List.of(
                createVacancy(1L, "Senior Java Developer", "Москва",
                        "от 300 000 до 400 000 руб."),
                createVacancy(2L, "Junior Python Developer", "Москва",
                        "от 80 000 до 120 000 руб."),
                createVacancy(3L, "Middle Frontend Developer", "Санкт-Петербург",
                        "от 150 000 до 200 000 руб.")
        );
    }

    private VacancyDomain createVacancy(Long id, String title, String city, String salary) {
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setId(id);
        vacancy.setTitle(title);
        vacancy.setCity(city);
        vacancy.setSalaryRaw(salary);
        vacancy.setCompany("Test Company");
        vacancy.setSource("test");
        vacancy.setPublishedAt(LocalDateTime.now().minusDays(5));
        vacancy.setCreatedAt(LocalDateTime.now());
        vacancy.setRequirements("Опыт работы от 1 года");
        return vacancy;
    }
}
