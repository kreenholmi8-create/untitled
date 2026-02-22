package com.kolmykova.jobparser.service;

import com.kolmykova.jobparser.logging.LoggingDaemon;
import com.kolmykova.jobparser.metrics.ParserMetrics;
import com.kolmykova.jobparser.model.Vacancy;
import com.kolmykova.jobparser.model.dto.VacancyDto;
import com.kolmykova.jobparser.repository.VacancyRepository;
import com.kolmykova.jobparser.service.client.WebFluxMockHtmlClient;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ParseService {

    private static final int BATCH_SIZE = 50;

    private final ExecutorService vacancyExecutor;
    private final WebFluxMockHtmlClient mockHtmlClient;
    private final VacancyParser vacancyParser;
    private final VacancyRepository vacancyRepository;
    private final LoggingDaemon loggingDaemon;
    private final ParserMetrics metrics;
    private final Tracer tracer;
    private final ParsingTaskService parsingTaskService;

    private final Object batchLock = new Object();
    private final List<Vacancy> batch = new ArrayList<>();

    public ParseService(ExecutorService vacancyExecutor,
                        WebFluxMockHtmlClient mockHtmlClient,
                        VacancyParser vacancyParser,
                        VacancyRepository vacancyRepository,
                        LoggingDaemon loggingDaemon,
                        ParserMetrics metrics,
                        Tracer tracer,
                        ParsingTaskService parsingTaskService) {
        this.vacancyExecutor = vacancyExecutor;
        this.mockHtmlClient = mockHtmlClient;
        this.vacancyParser = vacancyParser;
        this.vacancyRepository = vacancyRepository;
        this.loggingDaemon = loggingDaemon;
        this.metrics = metrics;
        this.tracer = tracer;
        this.parsingTaskService = parsingTaskService;
    }

    /**
     * Парсинг с отслеживанием статуса для REST polling и WebSocket
     */
    public String parseUrlsWithTracking(List<String> urls, int delaySeconds) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }

        String taskId = parsingTaskService.createTask(urls.size());
        AtomicInteger processedCount = new AtomicInteger(0);
        List<VacancyDto> results = new ArrayList<>();

        vacancyExecutor.submit(() -> {
            try {
                if (delaySeconds > 0) {
                    Thread.sleep(delaySeconds * 1000L);
                }

                for (String url : urls) {
                    try {
                        String html = mockHtmlClient.fetchHtml(url);
                        Vacancy vacancy = vacancyParser.parse(html, url);
                        Vacancy saved = vacancyRepository.save(vacancy);

                        VacancyDto dto = toDto(saved);
                        synchronized (results) {
                            results.add(dto);
                        }

                        int current = processedCount.incrementAndGet();
                        parsingTaskService.updateProgress(taskId, current, dto);

                    } catch (Exception e) {
                        processedCount.incrementAndGet();
                        loggingDaemon.log("Error processing url " + url + ": " + e.getMessage());
                    }
                }

                parsingTaskService.completeTask(taskId, results);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                parsingTaskService.failTask(taskId, "Task interrupted");
            } catch (Exception e) {
                parsingTaskService.failTask(taskId, e.getMessage());
            }
        });

        return taskId;
    }

    public void parseUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }

        Span batchSpan = tracer.nextSpan();
        if (batchSpan != null) {
            batchSpan
                    .name("parseUrls.batch")
                    .tag("jobparser.urls.count", String.valueOf(urls.size()))
                    .start();
        }

        Timer.Sample batchSample = metrics.startBatchTimer();
        CountDownLatch latch = new CountDownLatch(urls.size());

        try (Tracer.SpanInScope batchScope = (batchSpan != null ? tracer.withSpan(batchSpan) : null)) {

            Span parentSpan = tracer.currentSpan();

            for (String url : urls) {
                vacancyExecutor.submit(() -> {
                    Span urlSpan = tracer.nextSpan(parentSpan);
                    if (urlSpan != null) {
                        urlSpan
                                .name("processUrl.async")
                                .tag("jobparser.url", url)
                                .start();
                    }

                    try (Tracer.SpanInScope urlScope = (urlSpan != null ? tracer.withSpan(urlSpan) : null)) {
                        processUrl(url);
                    } catch (Exception e) {
                        if (urlSpan != null) {
                            urlSpan.error(e);
                        }
                    } finally {
                        if (urlSpan != null) {
                            urlSpan.end();
                        }
                        latch.countDown();
                    }
                });
            }

            vacancyExecutor.submit(() -> {
                Span awaitSpan = tracer.nextSpan(parentSpan);
                if (awaitSpan != null) {
                    awaitSpan
                            .name("parseUrls.awaitAndFlush")
                            .start();
                }

                try (Tracer.SpanInScope awaitScope = (awaitSpan != null ? tracer.withSpan(awaitSpan) : null)) {
                    latch.await();
                    flushBatch();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    metrics.incError("unknown");
                    if (awaitSpan != null) {
                        awaitSpan.error(e);
                    }
                } catch (Exception e) {
                    if (awaitSpan != null) {
                        awaitSpan.error(e);
                    }
                    throw e;
                } finally {
                    if (awaitSpan != null) {
                        awaitSpan.end();
                    }
                    metrics.stopBatchTimer(batchSample);
                    if (batchSpan != null) {
                        batchSpan.end();
                    }
                }
            });

        } catch (Exception e) {
            if (batchSpan != null) {
                batchSpan.error(e);
            }
            metrics.stopBatchTimer(batchSample);
            if (batchSpan != null) {
                batchSpan.end();
            }
            throw e;
        }
    }

    private void processUrl(String url) {
        Span span = tracer.nextSpan();
        if (span != null) {
            span
                    .name("processUrl")
                    .tag("jobparser.url", url)
                    .start();
        }

        metrics.incProcessed();
        Timer.Sample urlSample = metrics.startUrlTimer();

        try (Tracer.SpanInScope scope = (span != null ? tracer.withSpan(span) : null)) {

            Timer.Sample fetchSample = metrics.startFetchTimer();
            String html;

            Span fetchSpan = tracer.nextSpan();
            if (fetchSpan != null) {
                fetchSpan
                        .name("fetchHtml")
                        .tag("jobparser.url", url)
                        .start();
            }

            try (Tracer.SpanInScope fetchScope = (fetchSpan != null ? tracer.withSpan(fetchSpan) : null)) {
                html = mockHtmlClient.fetchHtml(url);
            } catch (Exception e) {
                if (fetchSpan != null) {
                    fetchSpan.error(e);
                }
                throw e;
            } finally {
                if (fetchSpan != null) {
                    fetchSpan.end();
                }
                metrics.stopFetchTimer(fetchSample);
            }

            Timer.Sample parseSample = metrics.startParseTimer();
            Vacancy vacancy;

            Span parseSpan = tracer.nextSpan();
            if (parseSpan != null) {
                parseSpan
                        .name("parseHtml")
                        .tag("jobparser.url", url)
                        .start();
            }

            try (Tracer.SpanInScope parseScope = (parseSpan != null ? tracer.withSpan(parseSpan) : null)) {
                vacancy = vacancyParser.parse(html, url);
            } catch (Exception e) {
                if (parseSpan != null) {
                    parseSpan.error(e);
                }
                throw e;
            } finally {
                if (parseSpan != null) {
                    parseSpan.end();
                }
                metrics.stopParseTimer(parseSample);
            }

            List<Vacancy> toSave = null;
            synchronized (batchLock) {
                batch.add(vacancy);
                if (batch.size() >= BATCH_SIZE) {
                    toSave = new ArrayList<>(batch);
                    batch.clear();
                }
            }

            if (toSave != null) {
                saveBatch(toSave, url);
            }

        } catch (Exception e) {
            metrics.incError(classifyError(e));
            loggingDaemon.log("Error processing url " + url + ": " +
                    e.getClass().getSimpleName() + " - " + e.getMessage());
            if (span != null) {
                span.error(e);
            }
        } finally {
            metrics.stopUrlTimer(urlSample);
            if (span != null) {
                span.end();
            }
        }
    }

    private void flushBatch() {
        Span span = tracer.nextSpan();
        if (span != null) {
            span.name("flushBatch").start();
        }

        try (Tracer.SpanInScope scope = (span != null ? tracer.withSpan(span) : null)) {
            List<Vacancy> toSave;

            synchronized (batchLock) {
                if (batch.isEmpty()) {
                    if (span != null) {
                        span.tag("jobparser.batch.size", "0");
                    }
                    return;
                }
                toSave = new ArrayList<>(batch);
                batch.clear();
            }

            if (span != null) {
                span.tag("jobparser.batch.size", String.valueOf(toSave.size()));
            }
            saveBatch(toSave, "flush");

        } catch (Exception e) {
            if (span != null) {
                span.error(e);
            }
            throw e;
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }

    private void saveBatch(List<Vacancy> toSave, String context) {
        Span dbSpan = tracer.nextSpan();
        if (dbSpan != null) {
            dbSpan
                    .name("saveBatch")
                    .tag("jobparser.batch.size", String.valueOf(toSave.size()))
                    .tag("jobparser.save.context", context)
                    .start();
        }

        Timer.Sample dbSample = metrics.startDbTimer();

        try (Tracer.SpanInScope scope = (dbSpan != null ? tracer.withSpan(dbSpan) : null)) {
            vacancyRepository.saveAll(toSave);
        } catch (Exception e) {
            if (dbSpan != null) {
                dbSpan.error(e);
            }
            throw e;
        } finally {
            metrics.stopDbTimer(dbSample);
            if (dbSpan != null) {
                dbSpan.end();
            }
        }

        metrics.incSaved(toSave.size());
        loggingDaemon.log("Saved batch of " + toSave.size() + " vacancies (context: " + context + ")");
    }

    private String classifyError(Exception e) {
        if (e instanceof WebClientResponseException || e instanceof WebClientRequestException) {
            return "http";
        }
        if (e instanceof DataAccessException) {
            return "db";
        }
        if (e instanceof IllegalArgumentException || e instanceof NullPointerException) {
            return "parse";
        }
        return "unknown";
    }

    private VacancyDto toDto(Vacancy v) {
        VacancyDto dto = new VacancyDto();
        dto.setId(v.getId());
        dto.setSource(v.getSource());
        dto.setUrl(v.getUrl());
        dto.setTitle(v.getTitle());
        dto.setCompany(v.getCompany());
        dto.setCity(v.getCity());
        dto.setSalary(v.getSalary());
        dto.setRequirements(v.getRequirements());
        dto.setPublishedAt(v.getPublishedAt());
        dto.setCreatedAt(v.getCreatedAt());
        return dto;
    }
}