package com.kolmykova.jobparser.adapter.in.rest;

import com.kolmykova.jobparser.domain.model.VacancyAnalysisResult;
import com.kolmykova.jobparser.domain.port.in.AnalyzeVacancyUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Входной адаптер - REST Controller.
 * Преобразует HTTP-запросы в вызовы Use Case (входного порта).
 */
@RestController
@RequestMapping("/api/analysis")
public class VacancyAnalysisController {

    private final AnalyzeVacancyUseCase analyzeVacancyUseCase;

    public VacancyAnalysisController(AnalyzeVacancyUseCase analyzeVacancyUseCase) {
        this.analyzeVacancyUseCase = analyzeVacancyUseCase;
    }

    /**
     * Полный анализ всех вакансий
     */
    @GetMapping("/full")
    public ResponseEntity<VacancyAnalysisResult> analyzeAll(
            @RequestParam(defaultValue = "30") int recentDays) {

        VacancyAnalysisResult result = analyzeVacancyUseCase.analyzeAll(recentDays);
        return ResponseEntity.ok(result);
    }

    /**
     * Средняя зарплата по городу
     */
    @GetMapping("/salary/city/{city}")
    public ResponseEntity<Map<String, Object>> averageSalaryByCity(
            @PathVariable String city) {

        Double avgSalary = analyzeVacancyUseCase.calculateAverageSalaryByCity(city);

        return ResponseEntity.ok(Map.of(
                "city", city,
                "averageSalary", avgSalary != null ? avgSalary : 0,
                "formatted", avgSalary != null ?
                        String.format("%.0f руб.", avgSalary) : "Нет данных"
        ));
    }

    /**
     * Анализ тональности требований
     */
    @PostMapping("/sentiment")
    public ResponseEntity<Map<String, String>> analyzeSentiment(
            @RequestBody(required = false) List<String> keywords) {

        String sentiment = analyzeVacancyUseCase.analyzeRequirementsSentiment(
                keywords != null ? keywords : List.of());

        return ResponseEntity.ok(Map.of("sentiment", sentiment));
    }
}
