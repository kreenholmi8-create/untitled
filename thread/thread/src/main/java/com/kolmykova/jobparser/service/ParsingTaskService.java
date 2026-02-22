package com.kolmykova.jobparser.service;

import com.kolmykova.jobparser.model.dto.ParsingStatus;
import com.kolmykova.jobparser.model.dto.VacancyDto;
import com.kolmykova.jobparser.websocket.ParsingWebSocketHandler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ParsingTaskService {

    private final Map<String, ParsingStatus> tasks = new ConcurrentHashMap<>();
    private final ParsingWebSocketHandler webSocketHandler;

    public ParsingTaskService(ParsingWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public String createTask(int totalUrls) {
        String taskId = UUID.randomUUID().toString();
        ParsingStatus status = ParsingStatus.builder()
                .taskId(taskId)
                .status("PENDING")
                .totalUrls(totalUrls)
                .processedUrls(0)
                .savedVacancies(0)
                .startedAt(LocalDateTime.now())
                .results(new ArrayList<>())
                .build();
        tasks.put(taskId, status);
        return taskId;
    }

    public void updateProgress(String taskId, int processedUrls, VacancyDto vacancy) {
        ParsingStatus status = tasks.get(taskId);
        if (status != null) {
            status.setStatus("IN_PROGRESS");
            status.setProcessedUrls(processedUrls);
            if (vacancy != null) {
                status.getResults().add(vacancy);
                status.setSavedVacancies(status.getResults().size());
            }
            // Отправляем обновление через WebSocket
            webSocketHandler.broadcastMessage(status);
        }
    }

    public void completeTask(String taskId, List<VacancyDto> results) {
        ParsingStatus status = tasks.get(taskId);
        if (status != null) {
            status.setStatus("COMPLETED");
            status.setCompletedAt(LocalDateTime.now());
            status.setProcessedUrls(status.getTotalUrls());
            if (results != null) {
                status.setResults(results);
                status.setSavedVacancies(results.size());
            }
            // Отправляем финальный результат через WebSocket
            webSocketHandler.broadcastMessage(status);
        }
    }

    public void failTask(String taskId, String errorMessage) {
        ParsingStatus status = tasks.get(taskId);
        if (status != null) {
            status.setStatus("ERROR");
            status.setCompletedAt(LocalDateTime.now());
            status.setErrorMessage(errorMessage);
            webSocketHandler.broadcastMessage(status);
        }
    }

    public ParsingStatus getStatus(String taskId) {
        return tasks.get(taskId);
    }

    public void removeTask(String taskId) {
        tasks.remove(taskId);
    }
}