# Отчёт по домашнему заданию

**Дисциплина:** Управление производительностью приложений  
**Тема 4:** Причины деградации производительности

---

## 1. Описание проекта

**Тип приложения:** Многопоточный парсер вакансий (Vacancy Parser)

**Основная функциональность:** Приложение автоматически собирает информацию о вакансиях с различных источников (hh.ru, SuperJob, Habr Career и др.), парсит HTML-страницы, извлекает структурированные данные (название, компания, зарплата, требования, город, дата публикации) и сохраняет их во встроенную базу данных H2. Пользователь может добавлять URL вакансий в очередь, просматривать результаты с фильтрацией и сортировкой.

**Ключевые технологии:**
- Java 21, Spring Boot 3.5.7
- Spring WebFlux (WebClient для HTTP-запросов)
- Spring Data JPA + H2 (in-memory БД)
- Jsoup (парсинг HTML)
- Micrometer + Prometheus (метрики)
- OpenTelemetry + Jaeger (трассировка)
- JMH (микробенчмарки)
- Thymeleaf (шаблонизатор для UI и mock-страниц)

**Архитектура:** Монолит

**Ресурсоёмкие операции:**
- Сетевые вызовы к внешним источникам (fetch HTML)
- Парсинг HTML с помощью Jsoup
- Пакетная запись в БД
- Многопоточная обработка очереди URL

---

## 2. Анализ категорий причин деградации производительности

### 2.1. Контекстное переключение (Context Switching)

#### Как проблема может проявиться в проекте

В проекте используется кастомный `ThreadPoolExecutor` с конфигурацией 4–8 потоков (`ExecutorConfig.java`):

```java
return new ThreadPoolExecutor(
    4,                          // corePoolSize
    8,                          // maximumPoolSize
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(),
    ...
);
```

При массовой отправке URL на парсинг (например, 1000+ URL одновременно) все задачи попадают в неограниченную `LinkedBlockingQueue`. Это само по себе не вызывает избыточного context switching, однако проблема может возникнуть в следующих сценариях:

1. **Конкуренция за блокировку `batchLock`** — каждый поток после парсинга захватывает монитор для добавления вакансии в batch:
   ```java
   synchronized (batchLock) {
       batch.add(vacancy);
       if (batch.size() >= BATCH_SIZE) { ... }
   }
   ```
   При 8 активных потоках это создаёт точку contention.

2. **Блокирующий вызов `WebClient.block()`** — хотя WebClient реактивный, в проекте используется блокирующий режим:
   ```java
   return webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
   ```
   Поток простаивает в ожидании ответа, что неэффективно использует ресурсы пула.

3. **Потенциальная замена на `newCachedThreadPool()`** — если бы разработчик решил «ускорить» обработку и заменил фиксированный пул на `Executors.newCachedThreadPool()`, при всплеске нагрузки (1000 URL) было бы создано ~1000 потоков, что привело бы к:
   - Резкому росту потребления памяти (каждый поток ≈1 MB стека)
   - Интенсивному context switching (ОС вынуждена переключать тысячи потоков)
   - Деградации throughput вместо его увеличения

#### Почему я опасаюсь/не опасаюсь этой проблемы

**Не критично в текущей реализации**, потому что:
- Пул ограничен 8 потоками — разумное значение для I/O-bound задач
- `LinkedBlockingQueue` позволяет буферизировать всплески без создания новых потоков
- BATCH_SIZE = 50 снижает частоту записи в БД

**Потенциальные риски:**
- `synchronized` на `batchLock` может стать узким местом при очень высокой частоте парсинга
- Блокирующий `block()` неэффективен — потоки простаивают вместо выполнения полезной работы

#### Как обнаружить и предотвратить

**Инструменты диагностики:**

1. **VisualVM / JConsole** — вкладка Threads позволяет увидеть:
   - Количество активных потоков
   - Состояния потоков (RUNNABLE, BLOCKED, WAITING)
   - Потоки в состоянии BLOCKED на `batchLock` указывают на contention

2. **Метрики Micrometer** — в проекте уже есть:
   ```
   jvm_threads_live_threads
   jvm_threads_states_threads{state="blocked"}
   jvm_threads_states_threads{state="waiting"}
   ```

3. **Thread dump** через Actuator endpoint `/actuator/threaddump`:
   - Искать потоки `vacancy-parser-*` в состоянии BLOCKED
   - Анализировать, на каком мониторе они заблокированы

4. **Метрика очереди** (уже реализована):
   ```java
   registry.gauge("jobparser.url.queue.size", urlQueue, BlockingQueue::size);
   ```
   Постоянно растущая очередь — признак того, что пул не справляется.

**Меры предотвращения:**

1. **Заменить `synchronized` на `ReentrantLock` с таймаутом** или использовать `ConcurrentLinkedQueue` для batch с периодическим flush.

2. **Перейти на неблокирующую модель** — вместо `block()` использовать реактивные цепочки:
   ```java
   webClient.get().uri(url)
       .retrieve()
       .bodyToMono(String.class)
       .map(html -> vacancyParser.parse(html, url))
       .subscribe(vacancy -> addToBatch(vacancy));
   ```

3. **Мониторинг пула** — добавить метрики:
   ```java
   Gauge.builder("jobparser.executor.active", executor, ThreadPoolExecutor::getActiveCount).register(registry);
   Gauge.builder("jobparser.executor.queue", executor, e -> e.getQueue().size()).register(registry);
   ```

---

### 2.2. Архитектурный выбор

#### Почему выбран монолит

Проект реализован как **монолитное приложение** по следующим причинам:

1. **Учебный характер проекта** — монолит проще в разработке, отладке и развёртывании. Нет накладных расходов на межсервисное взаимодействие, service discovery, распределённые транзакции.

2. **Единый домен данных** — все компоненты работают с одной сущностью `Vacancy`:
   - `ParseService` создаёт вакансии
   - `VacancyRepository` сохраняет их
   - `VacancyService` читает с фильтрацией
   
   Нет естественных границ для разделения на микросервисы.

3. **Общие ресурсы** — компоненты разделяют:
   - Пул потоков `vacancyExecutor`
   - Кэш mock-вакансий `MockVacancyCacheService`
   - Очередь URL `UrlQueueService`
   
   В микросервисной архитектуре это потребовало бы внешних систем (Redis, Kafka).

4. **In-memory БД H2** — требование задания. В микросервисах каждый сервис имел бы свою БД, что усложнило бы агрегацию данных.

#### Тесно связанные данные

**Связь парсинга и хранения:** В текущей архитектуре `ParseService` напрямую вызывает `VacancyRepository.saveAll()`. Если бы парсинг и хранение были разными сервисами:

```
[Parser Service] --HTTP/Kafka--> [Storage Service] --JPA--> [DB]
```

Возникли бы проблемы:
- **Latency** — дополнительный сетевой hop
- **Согласованность** — что делать, если Storage Service недоступен? Нужна очередь, retry-логика
- **Дублирование** — Parser должен знать структуру `VacancyDto`, Storage — тоже

**Mock-система и парсер:** `MockHtmlController` генерирует HTML, который парсит `VacancyParser`. В микросервисах это были бы разные сервисы, но тогда:
- Mock-сервис должен точно соответствовать ожиданиям парсера
- Изменение формата требует синхронного деплоя обоих сервисов

#### Как обеспечивается согласованность

1. **Batch-запись в транзакции** — `saveAll()` Spring Data JPA выполняется в одной транзакции. Либо все вакансии batch сохраняются, либо ни одна.

2. **Идемпотентность mock-кэша**:
   ```java
   cache.computeIfAbsent(key, k -> vacancyRandomGenerator.generateRandomVacancy(type, id));
   ```
   Повторный запрос того же URL вернёт те же данные.

3. **Потокобезопасные структуры**:
   - `ConcurrentHashMap` для кэша
   - `LinkedBlockingQueue` для очереди URL
   - `synchronized` для batch-накопления

#### Почему я опасаюсь/не опасаюсь

**Не критично**, потому что:
- Масштаб проекта не требует распределённой архитектуры
- Все данные помещаются в память одного процесса
- Нет требований к горизонтальному масштабированию

**Потенциальные риски при росте:**
- Невозможность масштабировать парсинг отдельно от API
- Single point of failure — падение приложения = потеря всей функциональности
- При переходе на внешнюю БД — потенциальные проблемы с connection pool

#### Как обнаружить проблемы и улучшить

**Инструменты:**

1. **Jaeger (уже интегрирован)** — трассировка показывает:
   - Время каждого этапа (fetch → parse → save)
   - Узкие места в цепочке обработки
   
2. **Метрики Micrometer** — в проекте реализованы таймеры по стадиям:
   ```
   jobparser.stage.fetch.time
   jobparser.stage.parse.time
   jobparser.stage.db.time
   ```
   Если `db.time` доминирует, это сигнал к оптимизации БД или выделению в отдельный сервис.

3. **Анализ зависимостей** — если со временем появятся:
   - Разные требования к масштабированию (парсинг нужно масштабировать, API — нет)
   - Разные команды, работающие над компонентами
   - Разные циклы релизов
   
   — это сигнал к переходу на микросервисы.

**Возможные улучшения без перехода на микросервисы:**

1. **Модульный монолит** — чёткое разделение на пакеты с минимальными зависимостями:
   ```
   com.shanaurin.jobparser.parsing   (ParseService, VacancyParser)
   com.shanaurin.jobparser.storage   (VacancyRepository, VacancyService)
   com.shanaurin.jobparser.api       (Controllers)
   ```

2. **Event-driven внутри монолита** — использовать Spring Events:
   ```java
   applicationEventPublisher.publish(new VacancyParsedEvent(vacancy));
   ```
   Это упростит будущий переход на Kafka.

---

### 2.3. Среда выполнения (JVM и контейнеризация)

#### Как проблема может проявиться

**Настройки JVM по умолчанию:**

Проект не содержит явных настроек JVM. При запуске через `java -jar app.jar` используются значения по умолчанию:
- **Heap size** — зависит от доступной памяти (обычно 1/4 RAM, но не более 32 GB)
- **GC** — в Java 21 по умолчанию G1GC
- **Metaspace** — не ограничен

Потенциальные проблемы:

1. **Недостаточный heap при массовом парсинге:**
   - Каждый HTML-документ занимает ~50-500 KB в памяти
   - Jsoup создаёт DOM-дерево, увеличивая потребление в 3-5 раз
   - При 1000 одновременных парсингов: 1000 × 500KB × 5 = 2.5 GB только на DOM
   - Если heap < 2 GB, начнутся частые GC-паузы или OutOfMemoryError

2. **Неоптимальный GC для данной нагрузки:**
   - G1GC хорош для latency-sensitive приложений
   - Для batch-обработки (парсинг очереди) лучше подошёл бы Parallel GC (выше throughput)

3. **Утечки памяти:**
   - `MockVacancyCacheService` хранит все сгенерированные вакансии в `ConcurrentHashMap` без eviction
   - При генерации миллионов уникальных ID кэш будет расти бесконечно

**Контейнеризация (Docker):**

В `docker-compose.yml` определены контейнеры для Prometheus, Grafana, Jaeger, но **само приложение запускается вне Docker**. Однако если добавить контейнер приложения:

```yaml
services:
  app:
    build: .
    mem_limit: 512m
    cpus: 1.0
```

Возникнут проблемы:

1. **JVM не учитывает лимиты cgroups (в старых версиях):**
   - Java 21 корректно определяет лимиты контейнера
   - Но без явных флагов может выделить слишком много heap для 512 MB лимита

2. **CPU throttling:**
   - При `cpus: 1.0` и 8 потоках в пуле — все потоки конкурируют за 1 CPU
   - Это приводит к context switching и деградации

3. **Нехватка памяти для off-heap:**
   - Netty (используется WebClient) выделяет direct buffers вне heap
   - Metaspace также вне heap
   - При `mem_limit: 512m` и `-Xmx400m` остаётся мало места для native memory

#### Почему я опасаюсь/не опасаюсь

**Текущие риски низкие**, потому что:
- Приложение запускается локально без контейнерных ограничений
- H2 in-memory не создаёт большой нагрузки на GC
- Объём данных в демо-режиме небольшой

**Реальные риски при production-развёртывании:**
- Без настройки heap приложение может упасть при первом всплеске нагрузки
- Утечка в `MockVacancyCacheService` проявится через часы/дни работы
- В Kubernetes/Docker без правильных лимитов — OOM kill

#### Как обнаружить и предотвратить

**Инструменты диагностики:**

1. **GC-логи** — добавить в параметры запуска:
   ```bash
   java -Xlog:gc*:file=gc.log:time,level,tags -jar app.jar
   ```
   Анализировать:
   - Частоту GC-пауз
   - Длительность пауз (target < 200ms для G1)
   - Соотношение Young/Old GC

2. **VisualVM:**
   - Вкладка Monitor — графики Heap, CPU, Threads
   - Вкладка Sampler — CPU/Memory profiling
   - Плагин VisualGC — визуализация поколений heap

3. **Метрики JVM (уже экспортируются в Prometheus):**
   ```
   jvm_memory_used_bytes{area="heap"}
   jvm_memory_max_bytes{area="heap"}
   jvm_gc_pause_seconds_sum
   jvm_gc_pause_seconds_count
   jvm_gc_memory_allocated_bytes_total
   ```

4. **Grafana dashboard** — создать панель с:
   - Heap utilization (%)
   - GC pause duration (p99)
   - Allocation rate (bytes/sec)
   - Thread count

**Рекомендации по настройке:**

1. **Явные параметры heap:**
   ```bash
   java -Xms512m -Xmx1g -XX:MaxMetaspaceSize=256m -jar app.jar
   ```
   - `-Xms = -Xmx` — избегаем resize heap
   - MaxMetaspaceSize — защита от утечек в classloaders

2. **Выбор GC в зависимости от задачи:**
   ```bash
   # Для latency (API-ответы)
   -XX:+UseG1GC -XX:MaxGCPauseMillis=100
   
   # Для throughput (batch-парсинг)
   -XX:+UseParallelGC
   
   # Для low-latency с большим heap (Java 21)
   -XX:+UseZGC
   ```

3. **Для Docker:**
   ```dockerfile
   ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
   CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
   ```
   - `UseContainerSupport` — JVM учитывает cgroups (по умолчанию в Java 21)
   - `MaxRAMPercentage=75` — оставляем 25% для off-heap и OS

4. **Исправление утечки кэша:**
   ```java
   // Использовать Caffeine с eviction
   private final Cache<String, VacancyDto> cache = Caffeine.newBuilder()
       .maximumSize(10_000)
       .expireAfterAccess(Duration.ofHours(1))
       .build();
   ```

5. **Мониторинг в production:**
   ```yaml
   # docker-compose.yml для приложения
   services:
     app:
       image: vacancy-parser:latest
       mem_limit: 1g
       mem_reservation: 512m
       cpus: 2.0
       environment:
         JAVA_OPTS: "-Xms512m -Xmx768m -XX:+UseG1GC"
       healthcheck:
         test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
         interval: 30s
         timeout: 10s
         retries: 3
   ```

---

## 3. Краткий вывод

В проекте Vacancy Parser выявлены три потенциальные области деградации производительности:

**Контекстное переключение** — текущая конфигурация пула (4-8 потоков) адекватна, но использование `synchronized` для batch-накопления и блокирующий `WebClient.block()` создают точки неэффективности. Рекомендуется переход на неблокирующую модель и lock-free структуры данных.

**Архитектурный выбор** — монолитная архитектура оправдана для учебного проекта и текущего масштаба. Интеграция трассировки (Jaeger) и метрик (Prometheus) позволяет идентифицировать компоненты-кандидаты на выделение при будущем росте.

**Среда выполнения** — отсутствие явных JVM-настроек и потенциальная утечка памяти в кэше mock-вакансий представляют риски при production-эксплуатации. Добавление параметров heap, выбор подходящего GC и ограничение размера кэша критичны для стабильной работы.

Все три категории покрываются существующей инструментацией (Micrometer, Jaeger, Actuator endpoints), что позволяет своевременно обнаруживать деградацию и принимать корректирующие меры.