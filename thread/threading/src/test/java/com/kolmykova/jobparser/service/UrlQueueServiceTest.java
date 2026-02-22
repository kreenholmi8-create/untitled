package com.shanaurin.jobparser.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UrlQueueServiceTest {

    @Test
    void addAllAndPollBatch_shouldWorkCorrectly() {
        UrlQueueService service = new UrlQueueService(mock(MeterRegistry.class));

        service.addAll(List.of("u1", "u2", "u3"));

        assertThat(service.size()).isEqualTo(3);

        List<String> firstBatch = service.pollBatch(2);
        assertThat(firstBatch).containsExactly("u1", "u2");
        assertThat(service.size()).isEqualTo(1);

        List<String> secondBatch = service.pollBatch(10);
        assertThat(secondBatch).containsExactly("u3");
        assertThat(service.size()).isZero();
    }

    @Test
    void addAll_nullOrEmptyShouldBeIgnored() {
        UrlQueueService service = new UrlQueueService(mock(MeterRegistry.class));
        service.addAll(null);
        service.addAll(List.of());

        assertThat(service.size()).isZero();
    }
}