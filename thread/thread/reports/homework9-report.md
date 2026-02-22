# Домашнее задание 9. Callable и Future

## Использование Future и отмена задач

```java
package com.utmn.kolmykova;

import java.util.concurrent.*;

public class LongTaskCancelDemo {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<String> longTask = () -> {
            System.out.println("Долгая задача стартовала...");
            try {
                // имитация долгой обработки 10 секунд
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(1000);
                    System.out.println("Обработка... секунда " + (i + 1));
                    // проверяем, не попросили ли нас прерваться
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Задача была прервана (isInterrupted = true)");
                        throw new InterruptedException("Прерывание из тела задачи");
                    }
                }
                return "Задача завершилась успешно";
            } catch (InterruptedException e) {
                System.out.println("Задача остановлена по InterruptedException");
                return "Задача отменена";
            }
        };

        Future<String> future = executor.submit(longTask);

        // ждём 3 секунды и пытаемся отменить
        Thread.sleep(3000);
        System.out.println("Пытаемся отменить задачу...");
        boolean cancelled = future.cancel(true); // true → прерываем, если выполняется
        System.out.println("Метод cancel() вернул: " + cancelled);

        // Попытка получения результата после отмены бросит CancellationException
        try {
            String result = future.get();
            System.out.println("Результат: " + result);
        } catch (CancellationException e) {
            System.out.println("Получена CancellationException: задача была отменена");
        } catch (ExecutionException e) {
            System.out.println("ExecutionException: " + e.getCause());
        }

        executor.shutdown();
    }
}
```

Результат:

```
Долгая задача стартовала...
Обработка... секунда 1
Обработка... секунда 2
Пытаемся отменить задачу...
Задача остановлена по InterruptedException
Метод cancel() вернул: true
Получена CancellationException: задача была отменена

Process finished with exit code 0
```

- Использован ExecutorService (newSingleThreadExecutor()).
- Задача длится около 10 секунд, но через ~3 секунды вызывается future.cancel(true).
- Внутри задачи проверка Thread.currentThread().isInterrupted() и обработка InterruptedException.
- future.get() после отмены выбрасывает CancellationException.

## Список из 3-5 задач, которые имитируют выполнение в течение случайного времени и возвращают результат

```java
package com.utmn.kolmykova;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class InvokeAllDemo {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        Random random = new Random();

        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            int taskId = i;
            tasks.add(() -> {
                int sleepTime = 1000 + random.nextInt(2000); // 1–3 секунды
                System.out.println("Задача " + taskId + " стартовала, время: " + sleepTime + " мс");
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    System.out.println("Задача " + taskId + " прервана");
                    return -1;
                }
                System.out.println("Задача " + taskId + " завершилась");
                // возвращаем, например, время выполнения
                return sleepTime;
            });
        }

        // Запуск всех задач одновременно
        List<Future<Integer>> results = executor.invokeAll(tasks);

        // Обработка результатов
        for (int i = 0; i < results.size(); i++) {
            Future<Integer> f = results.get(i);
            try {
                Integer result = f.get();
                System.out.println("Результат задачи " + (i + 1) + ": " + result + " мс");
            } catch (ExecutionException e) {
                System.out.println("Ошибка в задаче " + (i + 1) + ": " + e.getCause());
            }
        }

        executor.shutdown();
    }
}
```

Результат:

```
Задача 3 стартовала, время: 1927 мс
Задача 1 стартовала, время: 2987 мс
Задача 2 стартовала, время: 1717 мс
Задача 2 завершилась
Задача 4 стартовала, время: 1415 мс
Задача 3 завершилась
Задача 5 стартовала, время: 1273 мс
Задача 1 завершилась
Задача 4 завершилась
Задача 5 завершилась
Результат задачи 1: 2987 мс
Результат задачи 2: 1717 мс
Результат задачи 3: 1927 мс
Результат задачи 4: 1415 мс
Результат задачи 5: 1273 мс

Process finished with exit code 0
```

- Создан список из 5 задач.
- Каждая спит случайное время 1–3 секунды и возвращает числовой результат.
- invokeAll() запускает задачи «пакетом» и возвращает список Future.
- Все задачи завершаются до возврата из invokeAll() (метод блокирующий).

## ScheduledExecutorService и периодическая задача

```java
package com.utmn.kolmykova;

import java.util.concurrent.*;

public class ScheduledTaskDemo {

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Runnable backgroundTask = () -> {
            System.out.println(
                    "[" + Thread.currentThread().getName() + "] Проверка статуса системы: "
                            + System.currentTimeMillis()
            );
        };

        // Запускаем через 1 секунду, затем каждые 3 секунды
        ScheduledFuture<?> scheduledFuture =
                scheduler.scheduleAtFixedRate(backgroundTask, 1, 3, TimeUnit.SECONDS);

        // Даём поработать 10–12 секунд
        Thread.sleep(12000);

        System.out.println("Отменяем периодическую задачу...");
        scheduledFuture.cancel(true);
        scheduler.shutdown();
    }
}
```

Результат

```
[pool-1-thread-1] Проверка статуса системы: 1763314591724
[pool-1-thread-1] Проверка статуса системы: 1763314594738
[pool-1-thread-1] Проверка статуса системы: 1763314597735
[pool-1-thread-1] Проверка статуса системы: 1763314600730
Отменяем периодическую задачу...

Process finished with exit code 0
```

- Использован ScheduledExecutorService.
- Настроена периодическая задача (например, «проверка статуса системы») с фиксированным интервалом.
- Показано завершение работы через cancel() и shutdown().

## PeriodicDataAggregator


```java
import java.util.*;
import java.util.concurrent.*;

public class PeriodicDataAggregator {

    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerPool;
    private final long periodSeconds;

    public PeriodicDataAggregator(long periodSeconds, int workerThreads) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.workerPool = Executors.newFixedThreadPool(workerThreads);
        this.periodSeconds = periodSeconds;
    }

    public void start() {
        System.out.println("Запуск PeriodicDataAggregator, период = " + periodSeconds + " секунд");

        scheduler.scheduleAtFixedRate(this::aggregationCycle,
                0, periodSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        System.out.println("Остановка PeriodicDataAggregator...");
        scheduler.shutdown();
        workerPool.shutdown();
    }

    // Один цикл агрегации
    private void aggregationCycle() {
        System.out.println("=== Новый цикл агрегации: " + new Date() + " ===");

        List<String> entityIds = fetchEntityIds();
        System.out.println("Получены идентификаторы: " + entityIds);

        // Для каждой сущности отправляем асинхронную задачу в workerPool
        List<Future<EntityData>> futures = new ArrayList<>();
        for (String id : entityIds) {
            futures.add(workerPool.submit(() -> fetchDataForEntity(id)));
        }

        // Обрабатываем успешные результаты
        for (int i = 0; i < futures.size(); i++) {
            String id = entityIds.get(i);
            Future<EntityData> f = futures.get(i);
            try {
                EntityData data = f.get();
                processData(id, data);
            } catch (ExecutionException e) {
                System.out.println("Ошибка при получении данных для " + id + ": " + e.getCause());
            } catch (InterruptedException e) {
                System.out.println("Поток был прерван при ожидании результата для " + id);
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println("=== Цикл агрегации завершён ===");
    }

    // Имитация получения списка сущностей
    private List<String> fetchEntityIds() {
        // Например, список валют
        return Arrays.asList("USD", "EUR", "JPY", "GBP");
    }

    // Имитация внешнего источника данных
    private EntityData fetchDataForEntity(String id) throws Exception {
        // Имитация задержки 0.5–2 секунды
        long delay = 500 + new Random().nextInt(1500);
        Thread.sleep(delay);

        // Иногда бросаем исключение, чтобы проверить обработку ошибок
        if (new Random().nextInt(5) == 0) { // ~20% вероятность ошибки
            throw new RuntimeException("Ошибка при запросе данных для " + id);
        }

        // Возвращаем «данные»
        double value = 50 + new Random().nextDouble() * 50;
        return new EntityData(id, value, System.currentTimeMillis());
    }

    // Обработка успешных данных
    private void processData(String id, EntityData data) {
        System.out.println("Обработка данных для " + id + ": " + data);
        // Здесь может быть сохранение в БД, логирование, отправка в очередь и т.п.
    }

    // Простая модель данных
    private static class EntityData {
        private final String id;
        private final double value;
        private final long timestamp;

        public EntityData(String id, double value, long timestamp) {
            this.id = id;
            this.value = value;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "EntityData{" +
                    "id='" + id + '\'' +
                    ", value=" + value +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    // Пример запуска сервиса
    public static void main(String[] args) throws InterruptedException {
        PeriodicDataAggregator aggregator = new PeriodicDataAggregator(5, 4);
        aggregator.start();

        // Даем поработать, например, 30 секунд
        Thread.sleep(30000);
        aggregator.stop();
    }
}
```

Результат

```sh
Запуск PeriodicDataAggregator, период = 5 секунд
=== Новый цикл агрегации: Sun Nov 16 20:38:39 MSK 2025 ===
Получены идентификаторы: [USD, EUR, JPY, GBP]
Обработка данных для USD: EntityData{id='USD', value=54.39185887459442, timestamp=1763314720159}
Обработка данных для EUR: EntityData{id='EUR', value=96.79103545966481, timestamp=1763314720606}
Обработка данных для JPY: EntityData{id='JPY', value=51.387944447259024, timestamp=1763314720272}
Обработка данных для GBP: EntityData{id='GBP', value=97.59114926537404, timestamp=1763314720419}
=== Цикл агрегации завершён ===
=== Новый цикл агрегации: Sun Nov 16 20:38:44 MSK 2025 ===
Получены идентификаторы: [USD, EUR, JPY, GBP]
Обработка данных для USD: EntityData{id='USD', value=89.3056909199892, timestamp=1763314725807}
Ошибка при получении данных для EUR: java.lang.RuntimeException: Ошибка при запросе данных для EUR
Обработка данных для JPY: EntityData{id='JPY', value=99.57246550895623, timestamp=1763314725169}
Обработка данных для GBP: EntityData{id='GBP', value=53.234094970085685, timestamp=1763314725059}
=== Цикл агрегации завершён ===
=== Новый цикл агрегации: Sun Nov 16 20:38:49 MSK 2025 ===
Получены идентификаторы: [USD, EUR, JPY, GBP]
Ошибка при получении данных для USD: java.lang.RuntimeException: Ошибка при запросе данных для USD
Обработка данных для EUR: EntityData{id='EUR', value=77.65465494455093, timestamp=1763314730665}
Обработка данных для JPY: EntityData{id='JPY', value=51.226812323300756, timestamp=1763314730386}
Ошибка при получении данных для GBP: java.lang.RuntimeException: Ошибка при запросе данных для GBP
=== Цикл агрегации завершён ===
=== Новый цикл агрегации: Sun Nov 16 20:38:54 MSK 2025 ===
Получены идентификаторы: [USD, EUR, JPY, GBP]
Обработка данных для USD: EntityData{id='USD', value=87.5489138451992, timestamp=1763314735788}
Обработка данных для EUR: EntityData{id='EUR', value=71.69230276290895, timestamp=1763314734890}
Обработка данных для JPY: EntityData{id='JPY', value=61.89345249526749, timestamp=1763314734976}
Обработка данных для GBP: EntityData{id='GBP', value=73.14895861293319, timestamp=1763314735348}
=== Цикл агрегации завершён ===
=== Новый цикл агрегации: Sun Nov 16 20:38:59 MSK 2025 ===
Получены идентификаторы: [USD, EUR, JPY, GBP]
Обработка данных для USD: EntityData{id='USD', value=50.31358113164981, timestamp=1763314741121}
Обработка данных для EUR: EntityData{id='EUR', value=57.5967155565619, timestamp=1763314739713}
Обработка данных для JPY: EntityData{id='JPY', value=66.28776314008223, timestamp=1763314740999}
Обработка данных для GBP: EntityData{id='GBP', value=68.87390812071118, timestamp=1763314740463}
=== Цикл агрегации завершён ===
=== Новый цикл агрегации: Sun Nov 16 20:39:04 MSK 2025 ===
Получены идентификаторы: [USD, EUR, JPY, GBP]
Обработка данных для USD: EntityData{id='USD', value=53.575824634165706, timestamp=1763314745264}
Обработка данных для EUR: EntityData{id='EUR', value=75.94768865516153, timestamp=1763314745310}
Обработка данных для JPY: EntityData{id='JPY', value=69.93544885307524, timestamp=1763314745776}
Обработка данных для GBP: EntityData{id='GBP', value=97.24215651684838, timestamp=1763314745501}
=== Цикл агрегации завершён ===
Остановка PeriodicDataAggregator...

Process finished with exit code 0
```

### Что важно подчеркнуть в отчёте:

PeriodicDataAggregator использует два пула:
- ScheduledExecutorService для периодического триггера (scheduleAtFixedRate).
- ExecutorService (fixed thread pool) для параллельной обработки запросов по сущностям.

В каждом цикле:
- Получаем список ID (fetchEntityIds()).
- Для каждого ID создаём Callable, который имитирует внешний запрос и может бросать исключение.
- Сохраняем Future и через get() получаем результат.
- Успешные результаты передаём в processData.
- Ошибки логируются, цикл продолжается для остальных сущностей.
- Обеспечена корректная остановка через stop() (shutdown обоих пулов).