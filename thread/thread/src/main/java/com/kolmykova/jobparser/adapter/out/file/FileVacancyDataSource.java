package com.kolmykova.jobparser.adapter.out.file;

import com.kolmykova.jobparser.domain.model.VacancyDomain;
import com.kolmykova.jobparser.domain.port.out.VacancyDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Адаптер для чтения вакансий из CSV-файла.
 * Реализует выходной порт VacancyDataSource.
 */
public class FileVacancyDataSource implements VacancyDataSource {

    private static final String SOURCE_NAME = "FILE";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private List<VacancyDomain> cachedVacancies;

    public FileVacancyDataSource(Path filePath) {
        this.filePath = filePath;
    }

    public FileVacancyDataSource(String filePath) {
        this.filePath = Path.of(filePath);
    }

    @Override
    public List<VacancyDomain> fetchAll() {
        if (cachedVacancies == null) {
            cachedVacancies = loadFromFile();
        }
        return new ArrayList<>(cachedVacancies);
    }

    @Override
    public List<VacancyDomain> fetchByCity(String city) {
        return fetchAll().stream()
                .filter(v -> city.equalsIgnoreCase(v.getCity()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<VacancyDomain> fetchById(Long id) {
        return fetchAll().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst();
    }

    @Override
    public boolean isAvailable() {
        return Files.exists(filePath) && Files.isReadable(filePath);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME + ":" + filePath.getFileName();
    }

    /**
     * Загрузка вакансий из CSV файла.
     * Формат: id;source;url;title;company;city;salary;requirements;publishedAt;createdAt
     */
    private List<VacancyDomain> loadFromFile() {
        List<VacancyDomain> vacancies = new ArrayList<>();

        if (!isAvailable()) {
            return vacancies;
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue; // Пропускаем заголовок
                }

                VacancyDomain vacancy = parseLine(line);
                if (vacancy != null) {
                    vacancies.add(vacancy);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }

        return vacancies;
    }

    private VacancyDomain parseLine(String line) {
        String[] parts = line.split(";", -1);
        if (parts.length < 10) {
            return null;
        }

        try {
            VacancyDomain vacancy = new VacancyDomain();
            vacancy.setId(parseLong(parts[0]));
            vacancy.setSource(parts[1]);
            vacancy.setUrl(parts[2]);
            vacancy.setTitle(parts[3]);
            vacancy.setCompany(parts[4]);
            vacancy.setCity(parts[5]);
            vacancy.setSalaryRaw(parts[6]);
            vacancy.setRequirements(parts[7]);
            vacancy.setPublishedAt(parseDateTime(parts[8]));
            vacancy.setCreatedAt(parseDateTime(parts[9]));
            return vacancy;
        } catch (Exception e) {
            System.err.println("Error parsing line: " + line);
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Принудительно перезагрузить данные из файла
     */
    public void refresh() {
        cachedVacancies = null;
    }
}