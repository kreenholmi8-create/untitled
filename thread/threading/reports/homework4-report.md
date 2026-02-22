# Отчёт по заданию: синхронизированный и несинхронизированный сбор данных в многопоточном приложении

## Этапы выполнения задания

1. Анализ задачи.  
   Требовалось реализовать потокобезопасный сборщик данных `DataCollector`, который:
    - принимает элементы от нескольких потоков-производителей;
    - отдаёт их нескольким потокам-потребителям;
    - ведёт учёт уже обработанных ключей, чтобы не допускать повторной обработки;
    - подсчитывает количество обработанных элементов;
    - корректно завершает работу, когда данные больше не поступают.

2. Реализация синхронизированной версии `DataCollector`.  
   В этой версии были синхронизированы:
    - методы работы с очередью (`collectItem`, `takeItem`);
    - методы работы с общими разделяемыми данными (`incrementProcessed`, `isAlreadyProcessed`, `finish`, `getProcessedCount`).

   Вся логика многопоточного взаимодействия основана на:
    - мониторе экземпляра `DataCollector` (ключевое слово `synchronized`);
    - механизмах `wait()` / `notifyAll()` для ожидания и пробуждения потоков.

3. Реализация тестового класса `DataCollectorTest`.  
   Логика теста:
    - создаются 10 потоков-производителей и 10 потоков-потребителей;
    - каждый производитель генерирует по 10000 элементов, всего 100000 элементов;
    - потребители:
        - берут элементы из очереди через `takeItem`;
        - проверяют, не обрабатывался ли уже элемент (`isAlreadyProcessed`);
        - если не обрабатывался — увеличивают счётчик обработанных (`incrementProcessed`);
    - после завершения всех производителей вызывается `collector.finish()`, чтобы уведомить потребителей, что новых данных не будет;
    - в конце измеряется время выполнения и выводится `Processed count` и `Time`.

4. Реализация упрощённой (частично несинхронизированной) версии `DataCollectorNotSync`.  
   В этой версии:
    - методы `collectItem`, `takeItem`, `finish`, `getProcessedCount` оставлены синхронизированными (для работы с очередью и признаком завершения);
    - методы `incrementProcessed` и `isAlreadyProcessed` сделаны НЕ синхронизированными.

   Цель — показать последствия отсутствия синхронизации при доступе к общим данным (`processedCount`, `processedKeys`).

5. Реализация тестового класса `DataCollectorNotSyncTest`.  
   Логическая схема полностью аналогична первому тесту, но используется `DataCollectorNotSync`.

6. Запуск тестов и снятие замеров.  
   Были получены следующие результаты:

   Для синхронизированной версии:
    - `Processed count = 100000`
    - `Time = 187 ms`

   Для частично несинхронизированной версии:
    - `Processed count = 98916`
    - `Time = 142 ms`

## Промежуточные результаты и выводы по производительности
-----------------------------------------------------------

### Корректность результатов:
    - В синхронизированной версии:
        - обработано 100000 элементов;
        - это совпадает с ожидаемым количеством (10 производителей * 10000 элементов).
        - Потерь данных и двойной обработки нет.

    - В несинхронизированной версии:
        - обработано 98916 элементов;
        - ожидается 100000, значит потеряно 1084 обработки (по счётчику).
        - Это свидетельствует о гонках данных при увеличении счётчика и/или при проверке множества уже обработанных ключей.

   Важно: количество элементов в очереди (100000) само по себе не гарантирует, что счётчик дойдёт до 100000, если операции над ним не атомарные и не защищены.

### Производительность:

    - Синхронизированная версия: 187 ms
    - Несинхронизированная версия: 142 ms

   Вывод:
    - Снятие синхронизации действительно уменьшает накладные расходы и ускоряет выполнение (в данном измерении на ~45 ms).
    - Однако эта «экономия» достигается ценой некорректного результата:
        - ошибки в счётчике;
        - потенциально некорректные операции с множеством ключей (в реальном приложении это может привести к дублирующей обработке, нарушению бизнес-логики, исключениям и т.д.).

   Итоговый промежуточный вывод:
    - Синхронизация добавляет накладные расходы и уменьшает производительность, но обеспечивает корректность.
    - Отказ от синхронизации увеличивает скорость, но ведёт к гонкам данных и ошибкам в результатах.


## Итоговые выводы о применении синхронизации

1. Необходимость синхронизации:
    - При наличии общих разделяемых данных (счётчики, коллекции, очереди, флаги состояния), к которым обращаются несколько потоков, синхронизация критически важна.
    - Отказ от неё приводит к гонкам данных, несогласованному состоянию объектов и некорректным вычислениям.

2. Компромисс «скорость vs корректность»:
    - Ваш эксперимент показал:
        - синхронизированный код выполняется медленнее (187 ms против 142 ms);
        - но при этом даёт гарантированно правильный результат (100000 вместо 98916).
    - Для большинства реальных задач финансового учёта, обработки заказов, логирования, обработки данных корректность критичнее, чем небольшая прибавка в скорости.

3. `wait()` / `notifyAll()` и управление жизненным циклом потоков:
    - Эти методы позволяют реализовать эффективное ожидание ресурсов без активного опроса (busy-waiting);
    - правильное использование `while` и флага `finished` позволяет:
        - избежать ситуации, когда поток навсегда «зависает» в ожидании;
        - обеспечить корректное завершение всех потребителей после окончания работы производителей.

4. Предотвращение deadlock:
    - В текущем дизайне deadlock предотвращён за счёт:
        - использования одного монитора;
        - отсутствия вложенных блокировок;
        - корректного применения `wait()` / `notifyAll()` на одном и том же объекте.
    - В более сложных системах важно:
        - вырабатывать строгие правила порядка захвата локов;
        - минимизировать время удержания блокировок;
        - избегать блокирующих операций внутри синхронизированных участков (особенно операций ввода-вывода и сетевых вызовов).

5. Общая рекомендация по синхронизации:
    - Синхронизировать нужно все операции, которые:
        - либо изменяют общие разделяемые данные;
        - либо зависят от их текущего состояния;
    - использовать высокоуровневые средства из `java.util.concurrent`, когда это возможно (`ConcurrentHashMap`, `BlockingQueue`, `AtomicLong` и т.д.), чтобы уменьшить вероятность ошибок и улучшить масштабируемость;
    - тестировать многопоточный код под нагрузкой, так как проблемы гонок и deadlock-ов часто проявляются только при больших объёмах и многократных запусках.

Заключение:  
Представленный пример наглядно демонстрирует, что корректно реализованная синхронизация (пусть и с накладными расходами) является необходимым условием для безопасной работы многопоточных приложений. Несинхронизированный доступ к общим ресурсам приводит к трудноуловимым и потенциально критичным ошибкам, что подтверждается экспериментальными результатами тестов.

Исходный код:

DataCollector.java
```java
package com.utmn.kolmykova.syncronized;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class DataCollector {

    // Количество обработанных элементов
    private long processedCount = 0;

    // Множество уже обработанных ключей (для isAlreadyProcessed)
    private final Set<String> processedKeys = new HashSet<>();

    // Очередь элементов на обработку
    private final Queue<Item> queue = new LinkedList<>();

    // Признак, что больше новых данных не будет
    private boolean finished = false;

    /**
     * Добавляет элемент в очередь.
     * Потоки-потребители будут ждать, пока очередь пуста.
     */
    public synchronized void collectItem(Item item) {
        queue.add(item);
        // Сообщаем ожидающим потокам, что появился новый элемент
        notifyAll();
    }

    /**
     * Извлечь элемент из очереди на обработку.
     * Если элементов нет, поток ожидает появления данных или завершения.
     */
    public synchronized Item takeItem() throws InterruptedException {
        while (queue.isEmpty() && !finished) {
            wait(); // ждём, пока появятся данные или будет вызвано finish()
        }
        return queue.poll(); // может вернуть null, если finished и очередь пуста
    }

    /**
     * Увеличить счётчик обработанных элементов.
     */
    public synchronized void incrementProcessed() {
        processedCount++;
    }

    /**
     * Проверяет, обрабатывался ли элемент с данным ключом.
     * Если не обрабатывался — помечает как обработанный и возвращает false.
     * Если уже был — возвращает true.
     */
    public synchronized boolean isAlreadyProcessed(String key) {
        if (processedKeys.contains(key)) {
            return true;
        }
        processedKeys.add(key);
        return false;
    }

    /**
     * Сообщить, что новых данных больше не будет.
     * Будим все ожидающие потоки.
     */
    public synchronized void finish() {
        finished = true;
        notifyAll();
    }

    public synchronized long getProcessedCount() {
        return processedCount;
    }
}
```

DataCollectorTest.java
```java
package com.utmn.shanaurin.syncronized;

public class DataCollectorTest {

    public static void main(String[] args) throws InterruptedException {
        DataCollector collector = new DataCollector();

        int producersCount = 10;
        int consumersCount = 10;
        int itemsPerProducer = 10000;

        // Потоки-производители
        Thread[] producers = new Thread[producersCount];
        for (int i = 0; i < producersCount; i++) {
            final int producerId = i;
            producers[i] = new Thread(() -> {
                for (int j = 0; j < itemsPerProducer; j++) {
                    String key = "P" + producerId + "-item-" + j;
                    Item item = new Item(key, j);
                    collector.collectItem(item);
                }
            }, "Producer-" + i);
        }

        // Потоки-потребители
        Thread[] consumers = new Thread[consumersCount];
        for (int i = 0; i < consumersCount; i++) {
            consumers[i] = new Thread(() -> {
                try {
                    while (true) {
                        Item item = collector.takeItem();
                        if (item == null) {
                            // данных больше нет
                            break;
                        }
                        // Проверка на дубли
                        if (!collector.isAlreadyProcessed(item.getKey())) {
                            // "Обработка"
                            collector.incrementProcessed();
                            // Можно добавить имитацию работы:
                            // Thread.sleep(1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i);
        }

        long start = System.currentTimeMillis();

        // Старт всех потоков
        for (Thread t : producers) t.start();
        for (Thread t : consumers) t.start();

        // Ждём, пока все производители закончат
        for (Thread t : producers) t.join();

        // Сообщаем, что новых данных не будет
        collector.finish();

        // Ждём потребителей
        for (Thread t : consumers) t.join();

        long end = System.currentTimeMillis();

        System.out.println("Processed count = " + collector.getProcessedCount());
        System.out.println("Time = " + (end - start) + " ms");
    }
}
```

DataCollectorNotSync.java
```java
package com.utmn.kolmykova.syncronized;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class DataCollectorNotSync {

    // Количество обработанных элементов
    private long processedCount = 0;

    // Множество уже обработанных ключей (для isAlreadyProcessed)
    private final Set<String> processedKeys = new HashSet<>();

    // Очередь элементов на обработку
    private final Queue<Item> queue = new LinkedList<>();

    // Признак, что больше новых данных не будет
    private boolean finished = false;

    /**
     * Добавляет элемент в очередь.
     * Потоки-потребители будут ждать, пока очередь пуста.
     */
    public synchronized void collectItem(Item item) {
        queue.add(item);
        // Сообщаем ожидающим потокам, что появился новый элемент
        notifyAll();
    }

    /**
     * Извлечь элемент из очереди на обработку.
     * Если элементов нет, поток ожидает появления данных или завершения.
     */
    public synchronized Item takeItem() throws InterruptedException {
        while (queue.isEmpty() && !finished) {
            wait(); // ждём, пока появятся данные или будет вызвано finish()
        }
        return queue.poll(); // может вернуть null, если finished и очередь пуста
    }

    /**
     * Увеличить счётчик обработанных элементов.
     */
    public void incrementProcessed() {
        processedCount++;
    }

    /**
     * Проверяет, обрабатывался ли элемент с данным ключом.
     * Если не обрабатывался — помечает как обработанный и возвращает false.
     * Если уже был — возвращает true.
     */
    public boolean isAlreadyProcessed(String key) {
        if (processedKeys.contains(key)) {
            return true;
        }
        processedKeys.add(key);
        return false;
    }

    /**
     * Сообщить, что новых данных больше не будет.
     * Будим все ожидающие потоки.
     */
    public synchronized void finish() {
        finished = true;
        notifyAll();
    }

    public synchronized long getProcessedCount() {
        return processedCount;
    }
}
```

DataCollectorNotSyncTest.java
```java
package com.utmn.kolmykova.syncronized;

public class DataCollectorNotSyncTest {

    public static void main(String[] args) throws InterruptedException {
        DataCollectorNotSync collector = new DataCollectorNotSync();

        int producersCount = 10;
        int consumersCount = 10;
        int itemsPerProducer = 10000;

        // Потоки-производители
        Thread[] producers = new Thread[producersCount];
        for (int i = 0; i < producersCount; i++) {
            final int producerId = i;
            producers[i] = new Thread(() -> {
                for (int j = 0; j < itemsPerProducer; j++) {
                    String key = "P" + producerId + "-item-" + j;
                    Item item = new Item(key, j);
                    collector.collectItem(item);
                }
            }, "Producer-" + i);
        }

        // Потоки-потребители
        Thread[] consumers = new Thread[consumersCount];
        for (int i = 0; i < consumersCount; i++) {
            consumers[i] = new Thread(() -> {
                try {
                    while (true) {
                        Item item = collector.takeItem();
                        if (item == null) {
                            // данных больше нет
                            break;
                        }
                        // Проверка на дубли
                        if (!collector.isAlreadyProcessed(item.getKey())) {
                            // "Обработка"
                            collector.incrementProcessed();
                            // Можно добавить имитацию работы:
                            // Thread.sleep(1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i);
        }

        long start = System.currentTimeMillis();

        // Старт всех потоков
        for (Thread t : producers) t.start();
        for (Thread t : consumers) t.start();

        // Ждём, пока все производители закончат
        for (Thread t : producers) t.join();

        // Сообщаем, что новых данных не будет
        collector.finish();

        // Ждём потребителей
        for (Thread t : consumers) t.join();

        long end = System.currentTimeMillis();

        System.out.println("Processed count = " + collector.getProcessedCount());
        System.out.println("Time = " + (end - start) + " ms");
    }
}
```
