package com.shanaurin.jobparser.service;

import com.shanaurin.jobparser.logging.LoggingDaemon;
import com.shanaurin.jobparser.metrics.ParserMetrics;
import com.shanaurin.jobparser.model.Vacancy;
import com.shanaurin.jobparser.repository.VacancyRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import com.shanaurin.jobparser.service.client.WebFluxMockHtmlClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ParseServiceTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void parseUrls_shouldSubmitTasksAndFlushBatchAndSaveAllVacancies() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        WebFluxMockHtmlClient mockClient = mock(WebFluxMockHtmlClient.class);
        VacancyParser parser = mock(VacancyParser.class);
        VacancyRepository repository = mock(VacancyRepository.class);
        LoggingDaemon loggingDaemon = mock(LoggingDaemon.class);
        ParserMetrics metrics = mock(ParserMetrics.class);
        Tracer tracer = mock(Tracer.class);
        ParsingTaskService parsingTaskService = mock(ParsingTaskService.class);

        // metrics timers are used; return "safe" mocks so NPE не случится
        when(metrics.startBatchTimer()).thenReturn(mock(Timer.Sample.class));
        when(metrics.startUrlTimer()).thenReturn(mock(Timer.Sample.class));
        when(metrics.startFetchTimer()).thenReturn(mock(Timer.Sample.class));
        when(metrics.startParseTimer()).thenReturn(mock(Timer.Sample.class));
        when(metrics.startDbTimer()).thenReturn(mock(Timer.Sample.class));

        // tracer может быть null-совместимым: сервис везде проверяет span на null
        when(tracer.nextSpan()).thenReturn(null);
        when(tracer.currentSpan()).thenReturn(null);
        when(tracer.nextSpan(any())).thenReturn(null);

        String html = "<html><body>test</body></html>";
        when(mockClient.fetchHtml(anyString())).thenReturn(html);

        when(parser.parse(eq(html), anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(1, String.class);
            Vacancy v = new Vacancy();
            v.setUrl(url);
            return v;
        });

        ParseService service = new ParseService(
                executor,
                mockClient,
                parser,
                repository,
                loggingDaemon,
                metrics,
                tracer,
                parsingTaskService
        );

        List<String> urls = List.of("http://localhost/mock/1", "http://localhost/mock/2");

        service.parseUrls(urls);

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // Теперь сохраняется батчем через saveAll(List<Vacancy>) один раз при flushBatch()
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Vacancy>> batchCaptor = ArgumentCaptor.forClass((Class) List.class);

        verify(repository, times(1)).saveAll(batchCaptor.capture());
        List<Vacancy> saved = batchCaptor.getValue();

        assertThat(saved)
                .extracting(Vacancy::getUrl)
                .containsExactlyInAnyOrder(
                        "http://localhost/mock/1",
                        "http://localhost/mock/2"
                );

        verify(loggingDaemon, atLeastOnce()).log(contains("Saved batch of 2 vacancies"));
    }

    @Test
    void parseUrls_shouldLogErrorOnException() throws Exception {
        WebFluxMockHtmlClient mockClient = mock(WebFluxMockHtmlClient.class);
        VacancyParser parser = mock(VacancyParser.class);
        VacancyRepository repository = mock(VacancyRepository.class);
        LoggingDaemon loggingDaemon = mock(LoggingDaemon.class);
        ParserMetrics parserMetrics = mock(ParserMetrics.class);
        Tracer tracer = mock(Tracer.class);
        ParsingTaskService parsingTaskService = mock(ParsingTaskService.class);

        when(mockClient.fetchHtml(anyString())).thenThrow(new RuntimeException("boom"));

        ParseService parseService = new ParseService(
                executor, mockClient, parser, repository, loggingDaemon, parserMetrics, tracer, parsingTaskService
        );

        parseService.parseUrls(List.of("http://bad-url"));

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        verify(loggingDaemon).log(contains("Error processing url"));
        verifyNoInteractions(repository);
    }
}