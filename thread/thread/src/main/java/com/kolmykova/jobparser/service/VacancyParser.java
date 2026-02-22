package com.kolmykova.jobparser.service;

import com.kolmykova.jobparser.model.Vacancy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Service
public class VacancyParser {

    public Vacancy parse(String html, String originalUrl) {
        Document doc = Jsoup.parse(html);

        String title        = textOrNull(doc.selectFirst("h1.title"));
        String salary       = textOrNull(doc.selectFirst("div.salary"));
        String company      = textOrNull(selectMetaValueAfterLabel(doc, "Компания:"));
        String city         = textOrNull(selectMetaValueAfterLabel(doc, "Город:"));
        String sourceBadge  = textOrNull(doc.selectFirst(".badge"));

        // ссылка-источник: <a class="link" ... th:href="${url}" th:text="${url}">
        Element urlEl = doc.selectFirst("a.link");
        String vacancyUrl = urlEl != null ? urlEl.attr("href") : null;
        if (vacancyUrl == null || vacancyUrl.isBlank()) {
            // на всякий случай используем текст внутри ссылки
            vacancyUrl = textOrNull(urlEl);
        }

        // даты: "Опубликовано:" и "Загружено в систему:"
        String publishedAtStr = textOrNull(selectMetaValueAfterLabel(doc, "Опубликовано:"));
        String createdAtStr   = textOrNull(selectMetaValueAfterLabel(doc, "Загружено в систему:"));

        LocalDateTime publishedAt = parseDateTimeSafe(publishedAtStr);
        LocalDateTime createdAt   = parseDateTimeSafe(createdAtStr);
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        // блок требований: <div class="requirements" th:text="${requirements}">
        String requirements = textOrNull(doc.selectFirst("div.requirements"));

        Vacancy v = new Vacancy();
        v.setTitle(title);
        v.setCompany(company);
        v.setCity(city);
        v.setSalary(salary);
        v.setRequirements(requirements);
        v.setPublishedAt(publishedAt);
        v.setCreatedAt(createdAt);

        // URL, который реально парсили (endpoint мок‑страницы)
        v.setUrl(originalUrl);

        // source: лучше всего брать из бэйджа (там, где th:text="${source}")
        v.setSource(sourceBadge != null ? sourceBadge : determineSourceFromUrl(originalUrl));

        return v;
    }

    /**
     * Ищет в блоках .meta такое:
     *
     * <div class="meta">
     *   <span class="label">Компания:</span>
     *   <span>ООО Ромашка</span>
     * </div>
     *
     * И возвращает текст второго span.
     */
    private Element selectMetaValueAfterLabel(Document doc, String labelText) {
        for (Element meta : doc.select("div.meta")) {
            Element label = meta.selectFirst("span.label");
            if (label != null && label.text().trim().equalsIgnoreCase(labelText)) {
                // берём первый span после label
                for (Element span : meta.select("span")) {
                    if (!span.hasClass("label")) {
                        return span;
                    }
                }
            }
        }
        return null;
    }

    private String textOrNull(Element el) {
        return el != null ? el.text().trim() : null;
    }

    private LocalDateTime parseDateTimeSafe(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            // в шаблоне пример: 2024-10-01T10:15
            return LocalDateTime.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String determineSourceFromUrl(String url) {
        if (url == null) return "unknown";
        String lower = url.toLowerCase();
        if (lower.contains("hh")) return "hh.ru";
        if (lower.contains("superjob")) return "superjob.ru";
        if (lower.contains("habr")) return "career.habr.com";
        if (lower.contains("rabota")) return "rabota.ru";
        if (lower.contains("linkedin")) return "linkedin.com";
        return "unknown";
    }
}