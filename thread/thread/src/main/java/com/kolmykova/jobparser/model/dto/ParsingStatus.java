package com.kolmykova.jobparser.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsingStatus {
    private String taskId;
    private String status; // PENDING, IN_PROGRESS, COMPLETED, ERROR
    private int totalUrls;
    private int processedUrls;
    private int savedVacancies;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<VacancyDto> results;
    private String errorMessage;
}