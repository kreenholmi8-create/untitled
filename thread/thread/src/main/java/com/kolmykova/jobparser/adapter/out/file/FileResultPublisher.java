package com.kolmykova.jobparser.adapter.out.file;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;
import com.kolmykova.jobparser.domain.model.VacancyDomain;
import com.kolmykova.jobparser.domain.port.out.VacancyResultPublisher;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Адаптер для сохранения результатов в файл.
 */
public class FileResultPublisher implements VacancyResultPublisher {

    private static final String PUBLISHER_NAME = "FILE";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path outputPath;

    public FileResultPublisher(Path outputPath) {
        this.outputPath = outputPath;
    }

    public FileResultPublisher(String outputPath) {
        this.outputPath = Path.of(outputPath);
    }

    @Override
    public void publishAnalysisResult(VacancyAnalysisResult result) {
        Path analysisFile = outputPath.resolve("analysis_" +
                System.currentTimeMillis() + ".txt");

        try {
            Files.createDirectories(outputPath);

            try (BufferedWriter writer = Files.newBufferedWriter(analysisFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

                writer.write("=== VACANCY ANALYSIS RESULT ===\n");
                writer.write("Analyzed at: " + result.getAnalyzedAt() + "\n");
                writer.write("Total vacancies: " + result.getTotalVacancies() + "\n");
                writer.write("Average salary: " + result.getAverageSalary() + "\n");
                writer.write("Min salary: " + result.getMinSalary() + "\n");
                writer.write("Max salary: " + result.getMaxSalary() + "\n");
                writer.write("Recent vacancies: " + result.getRecentVacanciesCount() + "\n");
                writer.write("Sentiment: " + result.getSentimentSummary() + "\n");
                writer.write("\nBy city:\n");
                result.getVacanciesByCity().forEach((city, count) -> {
                    try {
                        writer.write("  " + city + ": " + count + "\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                writer.write("\nBy seniority:\n");
                result.getVacanciesBySeniority().forEach((level, count) -> {
                    try {
                        writer.write("  " + level + ": " + count + "\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            System.out.println("[" + PUBLISHER_NAME + "] Analysis saved to: " + analysisFile);

        } catch (IOException e) {
            System.err.println("Error writing analysis to file: " + e.getMessage());
        }
    }

    @Override
    public void saveVacancies(List<VacancyDomain> vacancies) {
        Path vacanciesFile = outputPath.resolve("vacancies.csv");

        try {
            Files.createDirectories(outputPath);

            try (BufferedWriter writer = Files.newBufferedWriter(vacanciesFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                // Заголовок CSV
                writer.write("id;source;url;title;company;city;salary;requirements;publishedAt;createdAt\n");

                for (VacancyDomain v : vacancies) {
                    writer.write(String.format("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s%n",
                            v.getId() != null ? v.getId() : "",
                            v.getSource() != null ? v.getSource() : "",
                            v.getUrl() != null ? v.getUrl() : "",
                            v.getTitle() != null ? v.getTitle() : "",
                            v.getCompany() != null ? v.getCompany() : "",
                            v.getCity() != null ? v.getCity() : "",
                            v.getSalaryRaw() != null ? v.getSalaryRaw() : "",
                            v.getRequirements() != null ?
                                    v.getRequirements().replace(";", ",") : "",
                            v.getPublishedAt() != null ?
                                    v.getPublishedAt().format(DATE_FORMATTER) : "",
                            v.getCreatedAt() != null ?
                                    v.getCreatedAt().format(DATE_FORMATTER) : ""
                    ));
                }
            }

            System.out.println("[" + PUBLISHER_NAME + "] Vacancies saved to: " + vacanciesFile);

        } catch (IOException e) {
            System.err.println("Error writing vacancies to file: " + e.getMessage());
        }
    }

    @Override
    public String getPublisherName() {
        return PUBLISHER_NAME + ":" + outputPath;
    }
}
