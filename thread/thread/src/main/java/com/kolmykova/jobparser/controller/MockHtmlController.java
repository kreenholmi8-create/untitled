package com.kolmykova.jobparser.controller;

import com.kolmykova.jobparser.model.dto.VacancyDto;
import com.kolmykova.jobparser.service.MockVacancyCacheService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@RestController
@RequestMapping("/mock")
public class MockHtmlController {

    private final MockVacancyCacheService mockVacancyCacheService;
    private final TemplateEngine templateEngine;

    public MockHtmlController(MockVacancyCacheService mockVacancyCacheService,
                              TemplateEngine templateEngine) {
        this.mockVacancyCacheService = mockVacancyCacheService;
        this.templateEngine = templateEngine;
    }

    @GetMapping(
            value = "/vacancy/{type}/{id}",
            produces = MediaType.TEXT_HTML_VALUE
    )
    public String getMockVacancy(
            @PathVariable("type") String templateType,
            @PathVariable("id") Long id
    ) {
        // 1. Идемпотентно получаем VacancyDto из кэша / генератора
        VacancyDto vacancy = mockVacancyCacheService.getOrCreate(templateType, id);

        // 2. Готовим контекст Thymeleaf
        Context ctx = new Context();
        ctx.setVariable("id", vacancy.getId());
        ctx.setVariable("source", vacancy.getSource());
        ctx.setVariable("title", vacancy.getTitle());
        ctx.setVariable("salary", vacancy.getSalary());
        ctx.setVariable("company", vacancy.getCompany());
        ctx.setVariable("city", vacancy.getCity());
        ctx.setVariable("url", vacancy.getUrl());
        ctx.setVariable("publishedAt", vacancy.getPublishedAt());
        ctx.setVariable("createdAt", vacancy.getCreatedAt());
        ctx.setVariable("requirements", vacancy.getRequirements());

        // 3. Рендерим HTML из шаблона на диске
        return templateEngine.process("vacancy-mock", ctx);
    }
}