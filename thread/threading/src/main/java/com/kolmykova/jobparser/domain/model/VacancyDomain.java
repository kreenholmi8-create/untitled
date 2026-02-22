package com.kolmykova.jobparser.domain.model;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Доменная модель вакансии.
 * Чистый POJO без Spring/JPA зависимостей.
 */
public class VacancyDomain {

    private Long id;
    private String source;
    private String url;
    private String title;
    private String company;
    private String city;
    private String salaryRaw;
    private Integer salaryMin;
    private Integer salaryMax;
    private String requirements;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    public VacancyDomain() {}

    public VacancyDomain(Long id, String source, String url, String title,
                         String company, String city, String salaryRaw,
                         String requirements, LocalDateTime publishedAt,
                         LocalDateTime createdAt) {
        this.id = id;
        this.source = source;
        this.url = url;
        this.title = title;
        this.company = company;
        this.city = city;
        this.salaryRaw = salaryRaw;
        this.requirements = requirements;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        parseSalary();
    }

    /**
     * Бизнес-логика: парсинг зарплаты из строки
     */
    private void parseSalary() {
        if (salaryRaw == null || salaryRaw.isBlank()) {
            return;
        }

        // Берём только первые ДВА числовых блока (цифры + пробелы/NBSP внутри),
        // чтобы "150 000" стало одним числом, а остальные числа в строке игнорировались.
        Pattern p = Pattern.compile("(\\d[\\d\\s\\u00A0]*)\\D+(\\d[\\d\\s\\u00A0]*)");
        Matcher m = p.matcher(salaryRaw);

        if (!m.find()) {
            return; // нет двух чисел — ничего не заполняем
        }

        salaryMin = parseIntCompact(m.group(1));
        salaryMax = parseIntCompact(m.group(2));
    }

    private static int parseIntCompact(String s) {
        // удаляем пробелы и NBSP между разрядами: "150 000" -> "150000"
        String digits = s.replaceAll("[\\s\\u00A0]+", "");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0; // или можно выбросить исключение — зависит от вашей логики
        }
    }

    /**
     * Бизнес-логика: расчёт средней зарплаты
     */
    public Integer calculateAverageSalary() {
        if (salaryMin == null && salaryMax == null) {
            return null;
        }
        if (salaryMin != null && salaryMax != null) {
            return (salaryMin + salaryMax) / 2;
        }
        return salaryMin != null ? salaryMin : salaryMax;
    }

    /**
     * Бизнес-логика: определение уровня позиции по названию
     */
    public SeniorityLevel determineSeniorityLevel() {
        if (title == null) {
            return SeniorityLevel.UNKNOWN;
        }
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("senior") || lowerTitle.contains("lead") ||
                lowerTitle.contains("старший") || lowerTitle.contains("ведущий")) {
            return SeniorityLevel.SENIOR;
        }
        if (lowerTitle.contains("middle") || lowerTitle.contains("средний")) {
            return SeniorityLevel.MIDDLE;
        }
        if (lowerTitle.contains("junior") || lowerTitle.contains("младший") ||
                lowerTitle.contains("стажер") || lowerTitle.contains("intern")) {
            return SeniorityLevel.JUNIOR;
        }
        return SeniorityLevel.UNKNOWN;
    }

    /**
     * Бизнес-логика: проверка актуальности вакансии
     */
    public boolean isRecent(int daysThreshold) {
        if (publishedAt == null) {
            return false;
        }
        return publishedAt.isAfter(LocalDateTime.now().minusDays(daysThreshold));
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getSalaryRaw() { return salaryRaw; }
    public void setSalaryRaw(String salaryRaw) {
        this.salaryRaw = salaryRaw;
        parseSalary();
    }

    public Integer getSalaryMin() { return salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public enum SeniorityLevel {
        JUNIOR, MIDDLE, SENIOR, UNKNOWN
    }
}