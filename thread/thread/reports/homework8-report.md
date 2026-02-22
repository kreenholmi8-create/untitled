# Домашнее задание 8. Пул потоков (Thread Pool)

```java
package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AsyncHttpWithThreadPool {

    // Результат запроса
    public static class RequestResult {
        private final String url;
        private final int statusCode;
        private final long responseTimeMillis;
        private final String error;

        public RequestResult(String url, int statusCode, long responseTimeMillis, String error) {
            this.url = url;
            this.statusCode = statusCode;
            this.responseTimeMillis = responseTimeMillis;
            this.error = error;
        }

        public String getUrl() {
            return url;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public long getResponseTimeMillis() {
            return responseTimeMillis;
        }

        public String getError() {
            return error;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Пример списка URL (можно передать из аргументов или прочитать из файла)
        List<String> urls = Arrays.asList(
                "https://api.github.com",
                "https://httpbin.org/get",
                "https://jsonplaceholder.typicode.com/posts/1",
                "https://jsonplaceholder.typicode.com/posts/2",
                "https://jsonplaceholder.typicode.com/posts/3",
                "https://jsonplaceholder.typicode.com/posts/4",
                "https://jsonplaceholder.typicode.com/posts/5",
                "https://jsonplaceholder.typicode.com/posts/6",
                "https://jsonplaceholder.typicode.com/posts/7",
                "https://jsonplaceholder.typicode.com/posts/8",
                "https://jsonplaceholder.typicode.com/posts/9",
                "https://jsonplaceholder.typicode.com/posts/10",
                "https://httpbin.org/delay/1",
                "https://httpbin.org/delay/2",
                "https://httpbin.org/status/200",
                "https://httpbin.org/status/404",
                "https://httpbin.org/status/500",
                "https://example.com",
                "https://google.com",
                "https://stackoverflow.com"
        );

        // Настраиваем свой ThreadPoolExecutor
        int corePoolSize = 4;
        int maxPoolSize = 8;
        long keepAliveTime = 300L;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(urls.size());

        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r);
            t.setName("http-worker-" + t.getId());
            t.setDaemon(false);
            return t;
        };

        RejectedExecutionHandler rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                rejectedExecutionHandler
        );

        // HttpClient, который будет использовать наш executor для async-задач
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .executor(executor) // Важный момент: привязываем наш ThreadPoolExecutor
                .build();

        // Список Future с результатами
        List<Future<RequestResult>> futures = new ArrayList<>();

        for (String url : urls) {
            Callable<RequestResult> task = () -> sendRequest(httpClient, url);
            Future<RequestResult> future = executor.submit(task);
            futures.add(future);
        }

        // Ждем выполнения всех задач и собираем результаты
        List<RequestResult> results = new ArrayList<>();
        for (Future<RequestResult> future : futures) {
            try {
                RequestResult result = future.get(); // можно добавить таймаут
                results.add(result);
            } catch (ExecutionException e) {
                // Если произошла ошибка в самой задаче
                Throwable cause = e.getCause();
                results.add(new RequestResult(
                        "UNKNOWN",
                        -1,
                        0,
                        cause != null ? cause.getMessage() : e.getMessage()
                ));
            }
        }

        // Корректно останавливаем пул
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // Выводим результаты по каждому URL
        System.out.println("===== Результаты по запросам =====");
        for (RequestResult r : results) {
            System.out.printf(
                    "URL: %-50s | status: %-3d | time: %4d ms | error: %s%n",
                    r.getUrl(),
                    r.getStatusCode(),
                    r.getResponseTimeMillis(),
                    r.getError() == null ? "-" : r.getError()
            );
        }

        // Выводим сводку
        System.out.println("\n===== Сводка =====");

        long successCount = results.stream()
                .filter(r -> r.getError() == null && r.getStatusCode() >= 200 && r.getStatusCode() < 300)
                .count();

        long errorCount = results.stream()
                .filter(r -> r.getError() != null || r.getStatusCode() >= 400 || r.getStatusCode() < 0)
                .count();

        double avgTime = results.stream()
                .filter(r -> r.getError() == null)
                .mapToLong(RequestResult::getResponseTimeMillis)
                .average()
                .orElse(0.0);

        List<Integer> statusCodes = results.stream()
                .map(RequestResult::getStatusCode)
                .collect(Collectors.toList());

        System.out.println("Всего URL:          " + urls.size());
        System.out.println("Успешных (2xx):     " + successCount);
        System.out.println("С ошибкой:          " + errorCount);
        System.out.println("Среднее время (мс): " + String.format("%.2f", avgTime));
        System.out.println("Все статус-коды:    " + statusCodes);
    }

    private static RequestResult sendRequest(HttpClient httpClient, String url) {
        Instant start = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long duration = Duration.between(start, Instant.now()).toMillis();

            return new RequestResult(url, response.statusCode(), duration, null);
        } catch (Exception e) {
            long duration = Duration.between(start, Instant.now()).toMillis();
            return new RequestResult(url, -1, duration, e.getMessage());
        }
    }
}
```

В результате получили:

```
===== Результаты по запросам =====
URL: https://api.github.com                             | status: -1  | time: 30041 ms | error: HTTP connect timed out
URL: https://httpbin.org/get                            | status: -1  | time: 30041 ms | error: HTTP connect timed out
URL: https://jsonplaceholder.typicode.com/posts/1       | status: -1  | time: 30041 ms | error: HTTP connect timed out
URL: https://jsonplaceholder.typicode.com/posts/2       | status: -1  | time: 30041 ms | error: HTTP connect timed out
URL: https://jsonplaceholder.typicode.com/posts/3       | status: -1  | time: 30011 ms | error: HTTP connect timed out
URL: https://jsonplaceholder.typicode.com/posts/4       | status: -1  | time: 30012 ms | error: HTTP connect timed out
URL: https://jsonplaceholder.typicode.com/posts/5       | status: -1  | time: 30012 ms | error: HTTP connect timed out
URL: https://jsonplaceholder.typicode.com/posts/6       | status: -1  | time: 30011 ms | error: HTTP connect timed out
URL: https://jsonplaceholder.typicode.com/posts/7       | status: 200 | time: 4554 ms | error: -
URL: https://jsonplaceholder.typicode.com/posts/8       | status: 200 | time: 4202 ms | error: -
URL: https://jsonplaceholder.typicode.com/posts/9       | status: 200 | time: 4218 ms | error: -
URL: https://jsonplaceholder.typicode.com/posts/10      | status: 200 | time: 4204 ms | error: -
URL: https://httpbin.org/delay/1                        | status: 200 | time: 1815 ms | error: -
URL: https://httpbin.org/delay/2                        | status: 200 | time: 3377 ms | error: -
URL: https://httpbin.org/status/200                     | status: 200 | time:  808 ms | error: -
URL: https://httpbin.org/status/404                     | status: 404 | time: 15456 ms | error: -
URL: https://httpbin.org/status/500                     | status: 500 | time:  690 ms | error: -
URL: https://example.com                                | status: 200 | time: 2805 ms | error: -
URL: https://google.com                                 | status: 301 | time: 2320 ms | error: -
URL: https://stackoverflow.com                          | status: 302 | time:  391 ms | error: -

===== Сводка =====
Всего URL:          20
Успешных (2xx):     8
С ошибкой:          10
Среднее время (мс): 3736,67
Все статус-коды:    [-1, -1, -1, -1, -1, -1, -1, -1, 200, 200, 200, 200, 200, 200, 200, 404, 500, 200, 301, 302]

Process finished with exit code 0

```