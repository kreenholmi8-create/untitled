// src/main/java/com/kolmykova/jobparser/controller/ParseController.java
package com.kolmykova.jobparser.controller;

import com.kolmykova.jobparser.model.dto.ParseRequest;
import com.kolmykova.jobparser.model.dto.ParsingStatus;
import com.kolmykova.jobparser.service.ParsingTaskService;
import com.kolmykova.jobparser.service.UrlQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.shanaurin.jobparser.service.ParseService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ParseController {

    private final UrlQueueService urlQueueService;
    private final ParseService parseService;
    private final ParsingTaskService parsingTaskService;

    public ParseController(UrlQueueService urlQueueService,
                           ParseService parseService,
                           ParsingTaskService parsingTaskService) {
        this.urlQueueService = urlQueueService;
        this.parseService = parseService;
        this.parsingTaskService = parsingTaskService;
    }

    @PostMapping("/parse")
    public ResponseEntity<String> parse(@RequestBody ParseRequest request) {
        urlQueueService.addAll(request.getUrls());
        return ResponseEntity.accepted().body("URLs added to queue");
    }

    @PostMapping("/parse/force")
    public ResponseEntity<String> forceParse(@RequestBody ParseRequest request) {
        parseService.parseUrls(request.getUrls());
        return ResponseEntity.accepted().body("URLs parsed");
    }

    /**
     * REST + Polling: запуск парсинга с получением taskId
     */
    @PostMapping("/parse/start")
    public ResponseEntity<Map<String, String>> startParsing(@RequestBody ParseRequest request,
                                                            @RequestParam(defaultValue = "0") int delaySeconds) {
        String taskId = parseService.parseUrlsWithTracking(request.getUrls(), delaySeconds);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }

    /**
     * REST + Polling: получение статуса задачи
     */
    @GetMapping("/parse/status/{taskId}")
    public ResponseEntity<ParsingStatus> getStatus(@PathVariable String taskId) {
        ParsingStatus status = parsingTaskService.getStatus(taskId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * WebSocket: запуск парсинга (результат придёт через WebSocket)
     */
    @PostMapping("/parse/ws")
    public ResponseEntity<Map<String, String>> startParsingWs(@RequestBody ParseRequest request,
                                                              @RequestParam(defaultValue = "0") int delaySeconds) {
        String taskId = parseService.parseUrlsWithTracking(request.getUrls(), delaySeconds);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId, "wsEndpoint", "/ws/parsing"));
    }
}