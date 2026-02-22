# Отчёт по заданию: асинхронная обработка данных с использованием `CompletableFuture`

## 1. Постановка задачи

Необходимо было реализовать метод, который по списку идентификаторов выполняет:

- асинхронное получение данных из внешних источников (API/БД/файл/и т.п.) для каждого идентификатора;
- асинхронные преобразования результатов (форматирование, фильтрация, обогащение);
- объединение двух независимых асинхронных результатов в единый итоговый объект;
- сбор итогового списка объектов параллельно.

Дополнительно требовалось:

- использовать `thenCombine`, `thenApply`, `thenAccept` (или эквивалент с side‑effect);
- обработать ошибки с помощью `handle` или `exceptionally`, чтобы сбой одной задачи не ломал всю операцию;
- использовать собственный `Executor` (не `ForkJoinPool.commonPool`) с именованными daemon‑потоками;
- тяжёлые/блокирующие преобразования выполнять через `thenApplyAsync(..., executor)`;
- корректно завершать `ExecutorService` при остановке приложения;
- добавить логирование с именем потока и ID запроса;
- имитировать ошибку в одном из источников данных и убедиться, что возвращается частичный результат;
- сравнить время выполнения асинхронной и синхронной версий.

В качестве предметной области использованы товары и курсы валют: по `id` получаем информацию о товаре (название и цену в USD) и курс USD→EUR, затем вычисляем цену в EUR и формируем итоговый объект `FinalResult`.

Ниже этапы реализованы на примере:

- «получение данных» – запросы в `MockProductApi` и `MockFxApi`;
- «преобразования» – вычисление цены в EUR и флагов ошибок;
- «сохранение заказа» в данном примере заменено на формирование итогового списка и логирование результатов.

---

## 2. Описание этапов выполнения

### 2.1. Модель итогового результата (`FinalResult`)

Класс `FinalResult` представляет агрегированный результат по каждому `id`:

- `id` – идентификатор запроса/товара;
- `productName` – имя товара;
- `priceInUsd` – исходная цена в USD;
- `priceInEur` – конвертированная цена в EUR;
- `partial` – флаг частичного результата (если один из источников упал);
- `errorMessage` – текст ошибки, если она была.

```java
package com.utmn.kolmykova;

import java.util.Objects;

public class FinalResult {
    private final String id;
    private final String productName;
    private final double priceInUsd;
    private final double priceInEur;
    private final boolean partial;
    private final String errorMessage;

    public FinalResult(String id,
                       String productName,
                       double priceInUsd,
                       double priceInEur,
                       boolean partial,
                       String errorMessage) {
        this.id = id;
        this.productName = productName;
        this.priceInUsd = priceInUsd;
        this.priceInEur = priceInEur;
        this.partial = partial;
        this.errorMessage = errorMessage;
    }

    public String getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public double getPriceInUsd() {
        return priceInUsd;
    }

    public double getPriceInEur() {
        return priceInEur;
    }

    public boolean isPartial() {
        return partial;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "FinalResult{" +
                "id='" + id + '\'' +
                ", productName='" + productName + '\'' +
                ", priceInUsd=" + priceInUsd +
                ", priceInEur=" + priceInEur +
                ", partial=" + partial +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinalResult that)) return false;
        return Double.compare(that.priceInUsd, priceInUsd) == 0
                && Double.compare(that.priceInEur, priceInEur) == 0
                && partial == that.partial
                && Objects.equals(id, that.id)
                && Objects.equals(productName, that.productName)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, productName, priceInUsd, priceInEur, partial, errorMessage);
    }
}
```

---

### 2.2. Модели и интерфейсы источников данных

Эмуляция внешних источников (например, REST‑сервисы или БД):

```java
package com.utmn.kolmykova;

public class ProductInfo {
    private final String id;
    private final String name;
    private final double priceUsd;

    public ProductInfo(String id, String name, double priceUsd) {
        this.id = id;
        this.name = name;
        this.priceUsd = priceUsd;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPriceUsd() {
        return priceUsd;
    }
}
```

```java
package com.utmn.kolmykova;

public interface ExternalProductApi {
    ProductInfo fetchProduct(String id) throws Exception;
}
```

```java
package com.utmn.kolmykova;

public interface ExternalFxApi {
    double fetchUsdToEurRate(String id) throws Exception;
}
```

---

### 2.3. Собственный `ExecutorService` с именованными daemon‑потоками

Создан отдельный класс‑holder для пула потоков:

```java
package com.utmn.kolmykova;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncExecutorHolder {

    private static final int N_THREADS =
            Math.max(4, Runtime.getRuntime().availableProcessors());

    private static final ThreadFactory DAEMON_NAMED_FACTORY = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true); // демон-потоки
            t.setName("async-worker-" + counter.getAndIncrement());
            return t;
        }
    };

    public static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(N_THREADS, DAEMON_NAMED_FACTORY);

    public static void shutdownGracefully() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

Особенности:

- используется отдельный пул, не `ForkJoinPool.commonPool`;
- потокам задаются имена вида `async-worker-N`, что хорошо видно в логах;
- потоки помечены как daemon, чтобы приложение могло корректно завершиться;
- есть метод `shutdownGracefully()` для корректного завершения работы.

---

### 2.4. Имитация внешних источников с ошибками

Реализованы две реализации API: `MockProductApi` и `MockFxApi`. Для части ID сознательно генерируются ошибки.

```java
package com.utmn.kolmykova;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MockProductApi implements ExternalProductApi {
    private final Random random = new Random();

    @Override
    public ProductInfo fetchProduct(String id) throws Exception {
        // имитация задержки сети/БД
        TimeUnit.MILLISECONDS.sleep(300 + random.nextInt(300));

        // имитация ошибки для определённого ID
        if ("ERROR_PRODUCT".equals(id)) {
            throw new RuntimeException("Product service failed for id=" + id);
        }

        return new ProductInfo(id, "Product-" + id, 10.0 + random.nextInt(90));
    }
}
```

```java
package com.utmn.kolmykova;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MockFxApi implements ExternalFxApi {
    private final Random random = new Random();

    @Override
    public double fetchUsdToEurRate(String id) throws Exception {
        // имитация задержки
        TimeUnit.MILLISECONDS.sleep(200 + random.nextInt(300));

        // имитация ошибки для определённого ID
        if ("ERROR_FX".equals(id)) {
            throw new RuntimeException("FX service failed for id=" + id);
        }

        // условно: курс 0.9…1.1
        return 0.9 + random.nextDouble() * 0.2;
    }
}
```

Таким образом:

- для `id = "ERROR_PRODUCT"` падает сервис товаров;
- для `id = "ERROR_FX"` падает сервис курса валют;
- для прочих ID возвращаются корректные данные.

Это позволяет протестировать частичные результаты и обработку ошибок.

---

### 2.5. Асинхронный сервис (`AsyncDataService`)

Основной класс, реализующий требуемый метод `fetchAllDataAsync`:

```java
package com.utmn.kolmykova;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class AsyncDataService {

    private static final Logger log = Logger.getLogger(AsyncDataService.class.getName());

    private final ExternalProductApi productApi;
    private final ExternalFxApi fxApi;
    private final Executor executor;

    public AsyncDataService(ExternalProductApi productApi,
                            ExternalFxApi fxApi,
                            Executor executor) {
        this.productApi = productApi;
        this.fxApi = fxApi;
        this.executor = executor;
    }

    public List<FinalResult> fetchAllDataAsync(List<String> ids) {
        String requestId = "REQ-" + System.nanoTime();
        log.info(() -> String.format("[%s][%s] Starting fetchAllDataAsync for %d ids",
                Thread.currentThread().getName(), requestId, ids.size()));

        List<CompletableFuture<FinalResult>> futures = new ArrayList<>();

        for (String id : ids) {
            CompletableFuture<FinalResult> future = fetchSingleAsync(id, requestId);
            futures.add(future);
        }

        // Ждём завершения всех задач и собираем частичные/полные результаты.
        List<FinalResult> results = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warning(() -> String.format(
                                "[%s][%s] Interrupted while waiting for id",
                                Thread.currentThread().getName(), requestId));
                        return new FinalResult(
                                "UNKNOWN",
                                null,
                                0.0,
                                0.0,
                                true,
                                "Interrupted while waiting for result"
                        );
                    } catch (ExecutionException e) {
                        // если не обработали exception внутри handle/exceptionally
                        log.warning(() -> String.format(
                                "[%s][%s] Unhandled exception: %s",
                                Thread.currentThread().getName(), requestId, e.getCause()));
                        return new FinalResult(
                                "UNKNOWN",
                                null,
                                0.0,
                                0.0,
                                true,
                                "Unhandled exception: " + e.getCause()
                        );
                    }
                })
                .toList();

        log.info(() -> String.format("[%s][%s] Completed fetchAllDataAsync",
                Thread.currentThread().getName(), requestId));

        return results;
    }

    private CompletableFuture<FinalResult> fetchSingleAsync(String id, String requestId) {
        log.info(() -> String.format("[%s][%s] Start fetch for id=%s",
                Thread.currentThread().getName(), requestId, id));

        // 1. Асинхронно тянем продукт
        CompletableFuture<ProductInfo> productFuture =
                CompletableFuture.supplyAsync(() -> {
                    log.info(() -> String.format("[%s][%s] Fetch product for id=%s",
                            Thread.currentThread().getName(), requestId, id));
                    try {
                        return productApi.fetchProduct(id);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor)
                .handle((product, ex) -> {
                    if (ex != null) {
                        log.warning(() -> String.format(
                                "[%s][%s] Product fetch failed for id=%s: %s",
                                Thread.currentThread().getName(), requestId, id, ex.getCause()));
                        return null; // null -> источник не сработал
                    }
                    return product;
                });

        // 2. Асинхронно тянем FX-курс
        CompletableFuture<Double> fxFuture =
                CompletableFuture.supplyAsync(() -> {
                    log.info(() -> String.format("[%s][%s] Fetch FX for id=%s",
                            Thread.currentThread().getName(), requestId, id));
                    try {
                        return fxApi.fetchUsdToEurRate(id);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor)
                .handle((rate, ex) -> {
                    if (ex != null) {
                        log.warning(() -> String.format(
                                "[%s][%s] FX fetch failed for id=%s: %s",
                                Thread.currentThread().getName(), requestId, id, ex.getCause()));
                        return null;
                    }
                    return rate;
                });

        // Вспомогательный класс для объединения
        class Intermediate {
            final String _id;
            final ProductInfo product;
            final Double rate;

            Intermediate(String _id, ProductInfo product, Double rate) {
                this._id = _id;
                this.product = product;
                this.rate = rate;
            }
        }

        // 3. Комбинируем два независимых результата
        CompletableFuture<FinalResult> combined =
                productFuture.thenCombine(fxFuture, (product, rate) ->
                                new Intermediate(id, product, rate)
                )
                // тяжёлое преобразование – через thenApplyAsync(..., executor)
                .thenApplyAsync(intermediate -> {
                    ProductInfo product = intermediate.product;
                    Double rate = intermediate.rate;

                    boolean partial = false;
                    String errorMessage = null;

                    if (product == null && rate == null) {
                        partial = true;
                        errorMessage = "Both product and FX failed";
                        return new FinalResult(
                                intermediate._id,
                                null,
                                0.0,
                                0.0,
                                partial,
                                errorMessage
                        );
                    }

                    if (product == null) {
                        partial = true;
                        errorMessage = "Product source failed";
                    } else if (rate == null) {
                        partial = true;
                        errorMessage = "FX source failed";
                    }

                    String productName = product != null ? product.getName() : null;
                    double priceUsd = product != null ? product.getPriceUsd() : 0.0;
                    double priceEur = (product != null && rate != null)
                            ? priceUsd * rate
                            : 0.0;

                    return new FinalResult(
                            intermediate._id,
                            productName,
                            priceUsd,
                            priceEur,
                            partial,
                            errorMessage
                    );
                }, executor)
                .exceptionally(ex -> {
                    log.warning(() -> String.format(
                            "[%s][%s] Unexpected error for id=%s: %s",
                            Thread.currentThread().getName(), requestId, id, ex));
                    return new FinalResult(
                            id,
                            null,
                            0.0,
                            0.0,
                            true,
                            "Unexpected error: " + ex.getMessage()
                    );
                })
                // thenAccept / whenComplete для side‑effect (логирование результата)
                .whenComplete((res, ex) -> {
                    if (ex == null) {
                        log.info(() -> String.format(
                                "[%s][%s] Completed id=%s; partial=%s; priceUsd=%.2f; priceEur=%.2f",
                                Thread.currentThread().getName(), requestId,
                                id, res.isPartial(), res.getPriceInUsd(), res.getPriceInEur()));
                    } else {
                        log.warning(() -> String.format(
                                "[%s][%s] Completed id=%s with error in whenComplete: %s",
                                Thread.currentThread().getName(), requestId, id, ex));
                    }
                });

        return combined;
    }
}
```

Использование ключевых методов:

- `thenCombine` – для объединения результатов `productFuture` и `fxFuture` в объект `Intermediate`.
- `thenApplyAsync(..., executor)` – для тяжёлого преобразования `Intermediate → FinalResult` (расчёт цены в EUR, формирование флагов и сообщений об ошибках).
- `handle` – локальная обработка исключений при запросе в каждый источник, чтобы сбой не прерывал всю цепочку.
- `exceptionally` – финальный fallback для любых необработанных исключений.
- `whenComplete` – side‑effect (логирование итогов) – по смыслу эквивалентно использованию отдельного `thenAccept` после `combined`.

---

### 2.6. Синхронный сервис (`SyncDataService`)

Для сравнения времени выполнения реализована синхронная версия:

```java
package com.utmn.kolmykova;

import java.util.ArrayList;
import java.util.List;

public class SyncDataService {

    private final ExternalProductApi productApi;
    private final ExternalFxApi fxApi;

    public SyncDataService(ExternalProductApi productApi,
                           ExternalFxApi fxApi) {
        this.productApi = productApi;
        this.fxApi = fxApi;
    }

    public List<FinalResult> fetchAllDataSync(List<String> ids) {
        String requestId = "REQ-SYNC-" + System.nanoTime();
        List<FinalResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                ProductInfo product = null;
                Double rate = null;
                String errorMessage = null;
                boolean partial = false;

                try {
                    product = productApi.fetchProduct(id);
                } catch (Exception e) {
                    partial = true;
                    errorMessage = "Product source failed: " + e.getMessage();
                }

                try {
                    rate = fxApi.fetchUsdToEurRate(id);
                } catch (Exception e) {
                    partial = true;
                    if (errorMessage == null) {
                        errorMessage = "FX source failed: " + e.getMessage();
                    } else {
                        errorMessage += "; FX source failed: " + e.getMessage();
                    }
                }

                double priceUsd = product != null ? product.getPriceUsd() : 0.0;
                String productName = product != null ? product.getName() : null;
                double priceEur = (product != null && rate != null)
                        ? priceUsd * rate
                        : 0.0;

                results.add(new FinalResult(
                        id,
                        productName,
                        priceUsd,
                        priceEur,
                        partial,
                        errorMessage
                ));
            } catch (Exception e) {
                results.add(new FinalResult(
                        id,
                        null,
                        0.0,
                        0.0,
                        true,
                        "Unexpected sync error: " + e.getMessage()
                ));
            }
        }

        return results;
    }
}
```

---

### 2.7. Точка входа и сравнение времени выполнения (`DemoApp`)

```java
package com.utmn.kolmykova;

import java.util.List;

public class DemoApp {

    public static void main(String[] args) {
        ExternalProductApi productApi = new MockProductApi();
        ExternalFxApi fxApi = new MockFxApi();

        AsyncDataService asyncService =
                new AsyncDataService(productApi, fxApi, AsyncExecutorHolder.EXECUTOR);
        SyncDataService syncService =
                new SyncDataService(productApi, fxApi);

        List<String> ids = List.of(
                "ID1", "ID2", "ERROR_PRODUCT", "ID3", "ERROR_FX", "ID4", "ID5"
        );

        long t1 = System.currentTimeMillis();
        List<FinalResult> asyncResults = asyncService.fetchAllDataAsync(ids);
        long t2 = System.currentTimeMillis();

        long t3 = System.currentTimeMillis();
        List<FinalResult> syncResults = syncService.fetchAllDataSync(ids);
        long t4 = System.currentTimeMillis();

        System.out.println("Async results (" + (t2 - t1) + " ms):");
        asyncResults.forEach(System.out::println);

        System.out.println("Sync results (" + (t4 - t3) + " ms):");
        syncResults.forEach(System.out::println);

        // корректно останавливаем executor
        AsyncExecutorHolder.shutdownGracefully();
    }
}
```

---

## 3. Промежуточные результаты (по логам и по коду)

### 3.1. Получение данных

По логу видно:

- для каждого `id` в `main` вызывается `fetchSingleAsync`:
  - `Start fetch for id=ID1`, `ID2`, `ERROR_PRODUCT`, `ID3`, `ERROR_FX`, `ID4`, `ID5`;
- отдельные задачи в пуле потоков (потоки `async-worker-N`) параллельно выполняют:
  - `Fetch product for id=...`;
  - `Fetch FX for id=...`.

Параллелизм хорошо заметен: работа идёт сразу в нескольких потоках, а не последовательно.

### 3.2. Обработка ошибок

В логах присутствуют строки:

```text
WARNING: ... Product fetch failed for id=ERROR_PRODUCT: java.lang.RuntimeException: Product service failed for id=ERROR_PRODUCT
WARNING: ... FX fetch failed for id=ERROR_FX: java.lang.RuntimeException: FX service failed for id=ERROR_FX
```

Это результат работы `.handle` на каждом из `CompletableFuture`:

- в случае `ERROR_PRODUCT`:
  - `productFuture` получает исключение, логирует предупреждение и возвращает `null`, что далее трактуется как частичный результат и приводит к `partial=true`, `errorMessage="Product source failed"`;
- в случае `ERROR_FX`:
  - `fxFuture` получает исключение, логирует предупреждение и возвращает `null`, что трактуется как `partial=true`, `errorMessage="FX source failed"`.

Таким образом, ошибка одного источника не ломает весь пайплайн: результат всё равно формируется, просто помечается частичным и получает нулевую цену в EUR (или в USD, если упал источник товара).

### 3.3. Формирование итоговых объектов

В логах:

```text
INFO: ... Completed id=ID4; partial=false; priceUsd=99,00; priceEur=98,14
INFO: ... Completed id=ID2; partial=false; priceUsd=66,00; priceEur=59,52
INFO: ... Completed id=ID3; partial=false; priceUsd=86,00; priceEur=79,27
INFO: ... Completed id=ERROR_PRODUCT; partial=true; priceUsd=0,00; priceEur=0,00
INFO: ... Completed id=ERROR_FX; partial=true; priceUsd=24,00; priceEur=0,00
INFO: ... Completed id=ID1; partial=false; priceUsd=93,00; priceEur=87,90
INFO: ... Completed id=ID5; partial=false; priceUsd=95,00; priceEur=92,29
```

Это результат работы блока `thenApplyAsync` + `whenComplete`:

- нормальные ID (`ID1`, `ID2`, `ID3`, `ID4`, `ID5`):
  - `partial=false`, есть обе цены: USD и EUR;
- `ERROR_PRODUCT`:
  - товара нет (`product=null`) → `priceUsd=0`, `priceEur=0`, `partial=true`, сообщение об ошибке;
- `ERROR_FX`:
  - товар есть (`priceUsd=24`), но курс не получен → `priceEur=0`, `partial=true`.

---

## 4. Итоговые выводы

1. Асинхронная реализация с использованием `CompletableFuture`:

   - корректно выполняет параллельные запросы к двум независимым источникам данных (по всем ID одновременно);
   - использует собственный пул потоков с понятными именами и daemon‑режимом;
   - разделяет этапы:
     - получение данных о товаре и курсе валют (`supplyAsync`);
     - объединение результатов (`thenCombine`);
     - вычисление итоговой цены и формирование итогового объекта (`thenApplyAsync`);
   - реализует обработку ошибок на уровне отдельных задач (`handle`), а также финальный fallback (`exceptionally`);
   - возвращает частичные результаты при сбое одного из источников, что видно по полю `partial` и `errorMessage`.

2. По времени выполнения:

   - асинхронный вариант (по логам): `Async results (859 ms)`
   - синхронный вариант: `Sync results (5920 ms)`
   - ускорение примерно в 6–7 раз на указанном наборе ID за счёт параллельного выполнения ввода‑вывода.

3. С точки зрения архитектуры:

   - применение `CompletableFuture` и собственного `ExecutorService` позволяет:
     - разгрузить главный поток;
     - эффективно использовать ресурсы CPU и IO;
     - реализовать нечувствительность всего процесса к падению отдельных внешних источников;
   - логирование с ID запроса и именем потока облегчает трассировку и отладку.

4. Аналогия с «получением данных о пользователе, товаре, вычислением цены и сохранением заказа» в контексте задания:

   - «получение данных о пользователе/товаре» – это `MockProductApi.fetchProduct(id)` и `MockFxApi.fetchUsdToEurRate(id)`;
   - «вычисление цены» – это асинхронный этап `thenApplyAsync`, где считаются `priceInUsd`, `priceInEur`, определяются флаги `partial` и сообщения об ошибках;
   - «сохранение заказа» – это формирование итогового списка `List<FinalResult>` и его дальнейший вывод/использование (в реальном приложении на этом месте была бы запись в БД или отправка сообщения).

Таким образом, цель задания выполнена: реализован асинхронный конвейер обработки данных с демонстрацией параллельного выполнения, корректной обработки ошибок, частичных результатов и сравнением с синхронной реализацией по времени.
