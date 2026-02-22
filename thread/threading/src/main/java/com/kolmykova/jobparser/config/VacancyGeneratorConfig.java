package com.kolmykova.jobparser.config;

import com.kolmykova.jobparser.model.dto.VacancyDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
public class VacancyGeneratorConfig {

    private final Random random = new Random();

    @Bean
    public VacancyRandomGenerator vacancyRandomGenerator() {
        return new VacancyRandomGenerator();
    }

    public class VacancyRandomGenerator {

        // Переиспользуем StringBuilder, чтобы меньше создавать временных объектов
        private static final ThreadLocal<StringBuilder> STRING_BUILDER =
                ThreadLocal.withInitial(() -> new StringBuilder(256));

        public VacancyDto generateRandomVacancy(String source, Long id) {
            String sourceValue = "unknown.tld";
            if (VacancyConfig.SOURCES.contains(source)) {
                sourceValue = source;
            }
            String title = randomFrom(VacancyConfig.VACANCY_TITLES);
            String city = randomFrom(VacancyConfig.CITIES);
            String company = randomFrom(VacancyConfig.COMPANIES);

            String salary = randomSalary();
            String requirements = randomRequirements();

            LocalDateTime createdAt = LocalDateTime.now();
            LocalDateTime publishedAt = createdAt.minusDays(random.nextInt(30));

            String url = buildUrl(source, id);

            return new VacancyDto(
                    id,
                    sourceValue,
                    url,
                    title,
                    company,
                    city,
                    salary,
                    requirements,
                    publishedAt,
                    createdAt
            );
        }

        private String randomFrom(List<String> list) {
            return list.get(random.nextInt(list.size()));
        }

        private String randomSalary() {
            // Пример: "от 150 000 до 250 000 руб. на руки"
            int min = 80_000 + random.nextInt(120_000);        // 80k–200k
            int max = min + 20_000 + random.nextInt(100_000);  // +20k–120k
            return String.format("от %,d до %,d руб. на руки", min, max)
                    .replace('\u00A0', ' ');
        }

        private String randomRequirements() {
            // Выбираем 5–10 случайных требований и собираем в один абзац
            int count = 5 + random.nextInt(6); // 5–10

            StringBuilder sb = STRING_BUILDER.get();
            sb.setLength(0); // очистить перед использованием

            for (int i = 0; i < count; i++) {
                String req = randomFrom(VacancyConfig.REQUIREMENTS);
                if (sb.indexOf(req) >= 0) {
                    // простая защита от дублей: пробуем еще раз
                    i--;
                    continue;
                }
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(req);
            }

            return sb.toString();
        }

        private String buildUrl(String source, Long id) {
            // Очень грубая генерация URL для примера
            String base;
            if (source != null && source.contains("hh")) {
                base = "https://hh.ru/vacancy/";
            } else if (source != null && source.contains("superjob")) {
                base = "https://www.superjob.ru/vacancy/";
            } else if (source != null && source.contains("rabota")) {
                base = "https://www.rabota.ru/vacancy/";
            } else if (source != null && source.contains("habr")) {
                base = "https://career.habr.com/vacancies/";
            } else if (source != null && source.contains("linkedin")) {
                base = "https://www.linkedin.com/jobs/view/";
            } else {
                base = "https://example.com/vacancy/";
            }
            long randomPart = ThreadLocalRandom.current().nextLong(1_000_000, 9_999_999);
            return base + (id != null ? id : randomPart);
        }
    }
}