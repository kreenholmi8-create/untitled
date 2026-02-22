package com.kolmykova.jobparser.benchmark;

import org.jsoup.Jsoup;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ParsingBenchmark {

    private List<String> htmlSamples;

    @Setup
    public void setup() {
        // Загрузите 100 примеров HTML
        htmlSamples = IntStream.range(0, 100)
                .mapToObj(i -> "<html>...</html>")
                .toList();
    }

    @Benchmark
    public void parseWithFor() {
        for (String html : htmlSamples) {
            Jsoup.parse(html).selectFirst("h1.title");
        }
    }

    @Benchmark
    public void parseWithStream() {
        htmlSamples.stream()
                .map(html -> Jsoup.parse(html).selectFirst("h1.title"))
                .toList();
    }

    @Benchmark
    public void parseWithParallelStream() {
        htmlSamples.parallelStream()
                .map(html -> Jsoup.parse(html).selectFirst("h1.title"))
                .toList();
    }
}