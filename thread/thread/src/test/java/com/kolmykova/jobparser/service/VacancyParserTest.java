package com.shanaurin.jobparser.service;

import com.shanaurin.jobparser.model.Vacancy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VacancyParserTest {

    private final VacancyParser vacancyParser = new VacancyParser();

    @Test
    void parse_shouldExtractAllFieldsFromMockHtml() {
        String html = """
                <!DOCTYPE html>
                <html lang="ru">
                <head><title>Test</title></head>
                <body>
                <div class="page">
                  <div class="card">
                    <div class="meta">
                      ID: <span>42</span>
                      <span class="badge">hh.ru</span>
                    </div>
                    <h1 class="title">Java Developer</h1>
                    <div class="salary">от 150 000 до 250 000 руб. на руки</div>
                    <div class="meta">
                        <span class="label">Компания:</span>
                        <span>ООО Ромашка</span>
                    </div>
                    <div class="meta">
                        <span class="label">Город:</span>
                        <span>Москва</span>
                    </div>
                    <div class="meta">
                        <span class="label">Источник:</span>
                        <a class="link" href="https://hh.ru/vacancy/123">https://hh.ru/vacancy/123</a>
                    </div>
                    <div class="meta">
                        <span class="label">Опубликовано:</span>
                        <span>2024-10-01T10:15</span>
                    </div>
                    <div class="meta">
                        <span class="label">Загружено в систему:</span>
                        <span>2024-10-02T12:00</span>
                    </div>
                    <div class="requirements">Опыт Java от 2 лет</div>
                  </div>
                </div>
                </body>
                </html>
                """;

        String originalUrl = "http://localhost/mock/vacancy/hh.ru/123";

        Vacancy v = vacancyParser.parse(html, originalUrl);

        assertThat(v.getTitle()).isEqualTo("Java Developer");
        assertThat(v.getSalary()).contains("150 000");
        assertThat(v.getCompany()).isEqualTo("ООО Ромашка");
        assertThat(v.getCity()).isEqualTo("Москва");
        assertThat(v.getRequirements()).isEqualTo("Опыт Java от 2 лет");
        assertThat(v.getSource()).isEqualTo("hh.ru");
        // url должен быть тем, что реально парсили
        assertThat(v.getUrl()).isEqualTo(originalUrl);
        // даты спарсились
        assertThat(v.getPublishedAt()).isNotNull();
        assertThat(v.getCreatedAt()).isNotNull();
    }

    @Test
    void parse_shouldFallbackToDetermineSourceFromUrl_whenBadgeMissing() {
        String html = """
                <html>
                  <body>
                    <h1 class="title">Java Dev</h1>
                    <div class="meta">
                        <span class="label">Компания:</span><span>ООО Тест</span>
                    </div>
                    <div class="meta">
                        <span class="label">Город:</span><span>Москва</span>
                    </div>
                  </body>
                </html>
                """;

        String url = "https://superjob.ru/vacancy/1";

        Vacancy v = vacancyParser.parse(html, url);

        assertThat(v.getSource()).isEqualTo("superjob.ru");
    }

    @Test
    void parse_shouldHandleMissingDates() {
        String html = """
                <html>
                  <body>
                    <h1 class="title">Java Dev</h1>
                    <div class="requirements">Требования</div>
                  </body>
                </html>
                """;
        Vacancy v = vacancyParser.parse(html, "http://example.com");

        // publishedAt null, createdAt установится в now()
        assertThat(v.getPublishedAt()).isNull();
        assertThat(v.getCreatedAt()).isNotNull();
    }
}