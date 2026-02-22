package com.shanaurin.jobparser.service.scheduler;

import com.shanaurin.jobparser.service.ParseService;
import com.shanaurin.jobparser.service.UrlQueueService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class VacancySchedulerTest {

    @Test
    void scheduledParsing_shouldCallParseServiceIfUrlsExist() {
        ParseService parseService = mock(ParseService.class);
        UrlQueueService urlQueueService = mock(UrlQueueService.class);
        VacancyScheduler scheduler = new VacancyScheduler(parseService, urlQueueService);

        when(urlQueueService.pollBatch(50)).thenReturn(List.of(
                "u1", "u2"
        ));

        scheduler.scheduledParsing();

        verify(parseService).parseUrls(List.of("u1", "u2"));
    }

    @Test
    void scheduledParsing_shouldDoNothingIfNoUrls() {
        ParseService parseService = mock(ParseService.class);
        UrlQueueService urlQueueService = mock(UrlQueueService.class);
        VacancyScheduler scheduler = new VacancyScheduler(parseService, urlQueueService);

        when(urlQueueService.pollBatch(50)).thenReturn(List.of());

        scheduler.scheduledParsing();

        verifyNoInteractions(parseService);
    }
}