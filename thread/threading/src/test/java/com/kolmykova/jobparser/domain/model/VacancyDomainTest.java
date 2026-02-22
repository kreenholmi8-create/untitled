package com.kolmykova.jobparser.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты доменной модели VacancyDomain.
 * Проверяют бизнес-логику внутри модели.
 */
class VacancyDomainTest {

    @Test
    @DisplayName("Парсинг зарплаты из строки 'от X до Y руб.'")
    void parseSalary_ShouldExtractMinAndMax() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();

        // When
        vacancy.setSalaryRaw("от 150 000 до 250 000 руб. на руки");

        // Then
        assertEquals(150000, vacancy.getSalaryMin());
        assertEquals(250000, vacancy.getSalaryMax());
    }

    @Test
    @DisplayName("Расчёт средней зарплаты")
    void calculateAverageSalary_ShouldReturnAverage() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setSalaryRaw("от 100 000 до 200 000 руб.");

        // When
        Integer avg = vacancy.calculateAverageSalary();

        // Then
        assertEquals(150000, avg);
    }

    @Test
    @DisplayName("Расчёт средней зарплаты при null")
    void calculateAverageSalary_ShouldReturnNull_WhenNoSalary() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();

        // When
        Integer avg = vacancy.calculateAverageSalary();

        // Then
        assertNull(avg);
    }

    @ParameterizedTest
    @CsvSource({
            "Senior Java Developer, SENIOR",
            "Lead Backend Engineer, SENIOR",
            "Старший разработчик, SENIOR",
            "Ведущий программист, SENIOR",
            "Middle Python Developer, MIDDLE",
            "Junior Frontend Developer, JUNIOR",
            "Младший разработчик, JUNIOR",
            "Стажер, JUNIOR",
            "Intern Developer, JUNIOR",
            "Java Developer, UNKNOWN",
            "Software Engineer, UNKNOWN"
    })
    @DisplayName("Определение уровня позиции по названию")
    void determineSeniorityLevel_ShouldDetectCorrectLevel(String title,
                                                          VacancyDomain.SeniorityLevel expected) {
        // Given
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setTitle(title);

        // When
        VacancyDomain.SeniorityLevel level = vacancy.determineSeniorityLevel();

        // Then
        assertEquals(expected, level);
    }

    @Test
    @DisplayName("determineSeniorityLevel возвращает UNKNOWN для null title")
    void determineSeniorityLevel_ShouldReturnUnknown_ForNullTitle() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setTitle(null);

        // When
        VacancyDomain.SeniorityLevel level = vacancy.determineSeniorityLevel();

        // Then
        assertEquals(VacancyDomain.SeniorityLevel.UNKNOWN, level);
    }

    @Test
    @DisplayName("isRecent возвращает true для свежей вакансии")
    void isRecent_ShouldReturnTrue_ForRecentVacancy() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setPublishedAt(LocalDateTime.now().minusDays(5));

        // When
        boolean recent = vacancy.isRecent(7);

        // Then
        assertTrue(recent);
    }

    @Test
    @DisplayName("isRecent возвращает false для старой вакансии")
    void isRecent_ShouldReturnFalse_ForOldVacancy() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setPublishedAt(LocalDateTime.now().minusDays(30));

        // When
        boolean recent = vacancy.isRecent(7);

        // Then
        assertFalse(recent);
    }

    @Test
    @DisplayName("isRecent возвращает false для null publishedAt")
    void isRecent_ShouldReturnFalse_WhenPublishedAtIsNull() {
        // Given
        VacancyDomain vacancy = new VacancyDomain();
        vacancy.setPublishedAt(null);

        // When
        boolean recent = vacancy.isRecent(7);

        // Then
        assertFalse(recent);
    }

    @Test
    @DisplayName("Конструктор с параметрами должен инициализировать все поля")
    void constructor_ShouldInitializeAllFields() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When
        VacancyDomain vacancy = new VacancyDomain(
                1L, "hh.ru", "http://example.com", "Java Developer",
                "Test Company", "Москва", "от 100 000 до 200 000 руб.",
                "Опыт от 1 года", now.minusDays(1), now
        );

        // Then
        assertEquals(1L, vacancy.getId());
        assertEquals("hh.ru", vacancy.getSource());
        assertEquals("Java Developer", vacancy.getTitle());
        assertEquals("Москва", vacancy.getCity());
        assertEquals(100000, vacancy.getSalaryMin());
        assertEquals(200000, vacancy.getSalaryMax());
    }
}
