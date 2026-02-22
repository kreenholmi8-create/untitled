package com.shanaurin.jobparser.service.scheduler;

import com.shanaurin.jobparser.service.ParseService;
import com.shanaurin.jobparser.service.UrlQueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VacancyScheduler {

    private final ParseService parseService;
    private final UrlQueueService urlQueueService;

    public VacancyScheduler(ParseService parseService,
                            UrlQueueService urlQueueService) {
        this.parseService = parseService;
        this.urlQueueService = urlQueueService;
    }

    // Раз в 5 секунд (пример)
    @Scheduled(fixedRateString = "${scheduler.vacancy.fixed-rate-ms:5000}")
    public void scheduledParsing() {
        // забираем пачку урлов, например, до 50 штук
        List<String> urls = urlQueueService.pollBatch(50);
        if (urls.isEmpty()) {
            return;
        }
        parseService.parseUrls(urls);
    }
}