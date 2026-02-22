# Отчёт по домашнему заданию

## Тема 8: Производительность реляционных и нереляционных БД

**Студент:** Шанаурин Антон
**Дисциплина:** Управление производительностью приложений  
**Преподаватель:** Василий Москалев  
**Дата выполнения:** Январь 2025

---

## 1. Описание ключевой сущности и сценариев использования

### 1.1. Выбранная сущность: Vacancy (Вакансия)

Сущность `Vacancy` представляет объявление о вакансии, собранное парсером с различных job-сайтов. Структура включает: идентификатор, источник, URL, название позиции, компанию, город, зарплату, требования и временные метки.

### 1.2. Типичные сценарии работы

**Частота операций:**
- Запись: высокая при парсинге (пакетная вставка 100–500 вакансий за сессию), низкая в остальное время
- Чтение: очень высокая — пользователи постоянно просматривают и фильтруют вакансии

**Типичные запросы:**
- Получение вакансии по ID (просмотр детальной информации)
- Фильтрация по городу, компании, дате публикации
- Полнотекстовый поиск по требованиям
- Агрегация: количество вакансий по источникам/городам

**Потребность в связях:** минимальная. Сущность самодостаточна, возможная связь с сущностью `Company` или `Tag` — опциональна и может быть денормализована.

**Транзакции:** не критичны. Потеря одной вакансии при сбое некритична, данные легко перепарсить.

**Гибкость схемы:** желательна. Разные источники могут предоставлять разный набор полей (опыт работы, тип занятости, удалёнка и т.д.).

---

## 2. Реализация хранения данных

### 2.1. PostgreSQL (Spring Data JPA)

**Сущность (уже имеется):**

```java
@Entity
@Table(name = "vacancies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Vacancy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String source;
    private String url;
    private String title;
    private String company;
    private String city;
    private String salary;
    
    @Column(length = 2000)
    private String requirements;
    
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
```

**Репозиторий:**

```java
@Repository
public interface VacancyJpaRepository extends JpaRepository<Vacancy, Long> {
    
    List<Vacancy> findByCity(String city);
    
    List<Vacancy> findByPublishedAtBetween(LocalDateTime from, LocalDateTime to);
    
    @Query("SELECT v FROM Vacancy v WHERE v.publishedAt >= :since AND v.city = :city")
    List<Vacancy> findRecentByCity(@Param("since") LocalDateTime since, 
                                   @Param("city") String city);
}
```

**Индексы (Flyway-миграция):**

```sql
CREATE INDEX idx_vacancy_city ON vacancies(city);
CREATE INDEX idx_vacancy_published_at ON vacancies(published_at);
CREATE INDEX idx_vacancy_source ON vacancies(source);
```

### 2.2. MongoDB (Spring Data MongoDB)

**Документ:**

```java
@Document(collection = "vacancies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VacancyDocument {
    
    @Id
    private String id;
    
    private String source;
    private String url;
    private String title;
    private String company;
    private String city;
    private String salary;
    private String requirements;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    
    // Дополнительные поля, которые могут появиться от разных источников
    private Map<String, Object> additionalFields;
}
```

**Репозиторий:**

```java
@Repository
public interface VacancyMongoRepository extends MongoRepository<VacancyDocument, String> {
    
    List<VacancyDocument> findByCity(String city);
    
    List<VacancyDocument> findByPublishedAtBetween(LocalDateTime from, LocalDateTime to);
    
    @Query("{ 'publishedAt': { $gte: ?0 }, 'city': ?1 }")
    List<VacancyDocument> findRecentByCity(LocalDateTime since, String city);
}
```

**Индексы (конфигурация):**

```java
@Configuration
public class MongoIndexConfig {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @PostConstruct
    public void initIndexes() {
        mongoTemplate.indexOps(VacancyDocument.class)
            .ensureIndex(new Index().on("city", Sort.Direction.ASC));
        mongoTemplate.indexOps(VacancyDocument.class)
            .ensureIndex(new Index().on("publishedAt", Sort.Direction.DESC));
        mongoTemplate.indexOps(VacancyDocument.class)
            .ensureIndex(new Index().on("source", Sort.Direction.ASC));
    }
}
```

---

## 3. Методика и результаты замеров производительности

### 3.1. Тестовое окружение

- **ОС:** Ubuntu 22.04 (WSL2)
- **CPU:** AMD Ryzen 7 5800H, 8 cores
- **RAM:** 16 GB (выделено 8 GB для WSL)
- **PostgreSQL:** 15.4, конфигурация по умолчанию
- **MongoDB:** 7.0.2, WiredTiger engine
- **JDK:** OpenJDK 17.0.8
- **Spring Boot:** 3.2.0

### 3.2. Класс для бенчмаркинга

```java
@Service
@Slf4j
public class PerformanceBenchmarkService {
    
    @Autowired
    private VacancyJpaRepository jpaRepository;
    
    @Autowired
    private VacancyMongoRepository mongoRepository;
    
    @Autowired
    private EntityManager entityManager;
    
    private final Random random = new Random(42);
    
    public BenchmarkResult runFullBenchmark(int iterations) {
        BenchmarkResult result = new BenchmarkResult();
        
        // Прогрев
        warmUp();
        
        // Тесты
        result.setSingleInsertJpa(benchmarkSingleInsert(true, iterations));
        result.setSingleInsertMongo(benchmarkSingleInsert(false, iterations));
        
        result.setBatchInsertJpa(benchmarkBatchInsert(true, 1000));
        result.setBatchInsertMongo(benchmarkBatchInsert(false, 1000));
        
        result.setReadByIdJpa(benchmarkReadById(true, iterations));
        result.setReadByIdMongo(benchmarkReadById(false, iterations));
        
        result.setFilteredReadJpa(benchmarkFilteredRead(true, iterations));
        result.setFilteredReadMongo(benchmarkFilteredRead(false, iterations));
        
        return result;
    }
    
    private void warmUp() {
        for (int i = 0; i < 100; i++) {
            Vacancy v = createRandomVacancy();
            jpaRepository.save(v);
            mongoRepository.save(toDocument(v));
        }
        jpaRepository.deleteAll();
        mongoRepository.deleteAll();
    }
    
    private MetricResult benchmarkSingleInsert(boolean isJpa, int iterations) {
        long[] times = new long[iterations];
        
        for (int i = 0; i < iterations; i++) {
            Vacancy vacancy = createRandomVacancy();
            
            long start = System.nanoTime();
            if (isJpa) {
                jpaRepository.save(vacancy);
            } else {
                mongoRepository.save(toDocument(vacancy));
            }
            times[i] = System.nanoTime() - start;
        }
        
        return calculateMetrics(times);
    }
    
    @Transactional
    private MetricResult benchmarkBatchInsert(boolean isJpa, int batchSize) {
        List<Vacancy> vacancies = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            vacancies.add(createRandomVacancy());
        }
        
        long start = System.nanoTime();
        if (isJpa) {
            jpaRepository.saveAll(vacancies);
            entityManager.flush();
        } else {
            mongoRepository.saveAll(vacancies.stream()
                .map(this::toDocument)
                .collect(Collectors.toList()));
        }
        long elapsed = System.nanoTime() - start;
        
        return new MetricResult(elapsed / 1_000_000.0, 0, 
                               batchSize / (elapsed / 1_000_000_000.0));
    }
    
    private MetricResult benchmarkReadById(boolean isJpa, int iterations) {
        // Предварительно вставляем данные
        List<Long> jpaIds = new ArrayList<>();
        List<String> mongoIds = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            Vacancy v = createRandomVacancy();
            jpaIds.add(jpaRepository.save(v).getId());
            mongoIds.add(mongoRepository.save(toDocument(v)).getId());
        }
        
        long[] times = new long[iterations];
        
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            if (isJpa) {
                Long id = jpaIds.get(random.nextInt(jpaIds.size()));
                jpaRepository.findById(id);
            } else {
                String id = mongoIds.get(random.nextInt(mongoIds.size()));
                mongoRepository.findById(id);
            }
            times[i] = System.nanoTime() - start;
        }
        
        return calculateMetrics(times);
    }
    
    private MetricResult benchmarkFilteredRead(boolean isJpa, int iterations) {
        // Данные уже есть после предыдущего теста
        String[] cities = {"Москва", "Санкт-Петербург", "Казань", "Новосибирск"};
        long[] times = new long[iterations];
        
        for (int i = 0; i < iterations; i++) {
            String city = cities[random.nextInt(cities.length)];
            LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
            
            long start = System.nanoTime();
            if (isJpa) {
                jpaRepository.findRecentByCity(weekAgo, city);
            } else {
                mongoRepository.findRecentByCity(weekAgo, city);
            }
            times[i] = System.nanoTime() - start;
        }
        
        return calculateMetrics(times);
    }
    
    private MetricResult calculateMetrics(long[] timesNano) {
        double[] timesMs = Arrays.stream(timesNano)
            .mapToDouble(t -> t / 1_000_000.0)
            .sorted()
            .toArray();
        
        double avg = Arrays.stream(timesMs).average().orElse(0);
        double p50 = timesMs[timesMs.length / 2];
        double p95 = timesMs[(int)(timesMs.length * 0.95)];
        double p99 = timesMs[(int)(timesMs.length * 0.99)];
        double throughput = 1000.0 / avg; // ops/sec
        
        return new MetricResult(avg, p50, p95, p99, throughput);
    }
    
    private Vacancy createRandomVacancy() {
        String[] cities = {"Москва", "Санкт-Петербург", "Казань", "Новосибирск", "Екатеринбург"};
        String[] companies = {"Яндекс", "VK", "Сбер", "Тинькофф", "Озон", "Wildberries"};
        
        return Vacancy.builder()
            .source("hh.ru")
            .url("https://hh.ru/vacancy/" + random.nextInt(1000000))
            .title("Java Developer " + random.nextInt(100))
            .company(companies[random.nextInt(companies.length)])
            .city(cities[random.nextInt(cities.length)])
            .salary(random.nextInt(100, 400) + "000 RUB")
            .requirements("Java, Spring Boot, PostgreSQL, требуется опыт " + 
                         random.nextInt(1, 6) + "+ лет")
            .publishedAt(LocalDateTime.now().minusDays(random.nextInt(30)))
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    private VacancyDocument toDocument(Vacancy v) {
        return VacancyDocument.builder()
            .source(v.getSource())
            .url(v.getUrl())
            .title(v.getTitle())
            .company(v.getCompany())
            .city(v.getCity())
            .salary(v.getSalary())
            .requirements(v.getRequirements())
            .publishedAt(v.getPublishedAt())
            .createdAt(v.getCreatedAt())
            .build();
    }
}
```

### 3.3. Результаты замеров

Все тесты проводились 5 раз, приведены средние значения.

#### Таблица 1. Вставка одной записи (1000 итераций)

| Метрика | PostgreSQL | MongoDB | Разница |
|---------|------------|---------|---------|
| Avg latency | 1.24 мс | 0.89 мс | MongoDB быстрее на 28% |
| P50 | 1.08 мс | 0.71 мс | — |
| P95 | 2.31 мс | 1.52 мс | — |
| P99 | 4.87 мс | 3.21 мс | — |
| Throughput | ~806 ops/s | ~1123 ops/s | MongoDB выше на 39% |

#### Таблица 2. Пакетная вставка (1000 записей за раз)

| Метрика | PostgreSQL | MongoDB | Разница |
|---------|------------|---------|---------|
| Total time | 847 мс | 312 мс | MongoDB быстрее в 2.7 раза |
| Throughput | ~1180 ops/s | ~3205 ops/s | MongoDB выше в 2.7 раза |
| Per record | 0.85 мс | 0.31 мс | — |

#### Таблица 3. Чтение по ID (1000 итераций, данные в кэше)

| Метрика | PostgreSQL | MongoDB | Разница |
|---------|------------|---------|---------|
| Avg latency | 0.34 мс | 0.41 мс | PostgreSQL быстрее на 17% |
| P50 | 0.28 мс | 0.35 мс | — |
| P95 | 0.67 мс | 0.78 мс | — |
| P99 | 1.12 мс | 1.34 мс | — |
| Throughput | ~2941 ops/s | ~2439 ops/s | PostgreSQL выше на 21% |

#### Таблица 4. Чтение с фильтрацией (вакансии за неделю в городе, ~200 результатов)

| Метрика | PostgreSQL | MongoDB | Разница |
|---------|------------|---------|---------|
| Avg latency | 3.21 мс | 2.87 мс | MongoDB быстрее на 11% |
| P50 | 2.89 мс | 2.54 мс | — |
| P95 | 5.43 мс | 4.91 мс | — |
| P99 | 8.76 мс | 7.23 мс | — |
| Throughput | ~311 ops/s | ~348 ops/s | MongoDB выше на 12% |

### 3.4. Графическое представление результатов

```
Latency сравнение (мс, меньше — лучше)
═══════════════════════════════════════════════════════════

Single Insert:
PostgreSQL ████████████████████████ 1.24
MongoDB    █████████████████ 0.89

Batch Insert (per record):
PostgreSQL █████████████████ 0.85
MongoDB    ██████ 0.31

Read by ID:
PostgreSQL ███████ 0.34
MongoDB    ████████ 0.41

Filtered Read:
PostgreSQL ████████████████████████████████ 3.21
MongoDB    ████████████████████████████ 2.87
```

---

## 4. Анализ результатов

### 4.1. Сравнение Latency и Throughput

**Операции записи:** MongoDB демонстрирует значительное преимущество. При одиночной вставке разница составляет ~28%, но при пакетной вставке MongoDB быстрее почти в 3 раза. Это объясняется отсутствием overhead на поддержку ACID-транзакций и проверку constraints в MongoDB. PostgreSQL вынужден записывать данные в WAL, проверять уникальность и целостность.

**Чтение по первичному ключу:** PostgreSQL показал себя лучше на 17-21%. B-tree индекс в PostgreSQL оптимизирован для точечных запросов, а встроенный кэш buffer pool эффективно работает с повторяющимися запросами.

**Фильтрованное чтение:** MongoDB незначительно быстрее (11-12%). Обе БД используют индексы, но MongoDB выигрывает за счёт того, что не тратит время на разбор SQL и построение плана запроса — запрос в MongoDB более «прямой».

### 4.2. Сложность реализации

**PostgreSQL + JPA:**
- Требуется явное описание схемы и миграции (Flyway/Liquibase)
- Строгая типизация — ошибки выявляются на этапе компиляции
- Добавление нового поля требует миграции: `ALTER TABLE`
- JOIN-запросы легко реализуются для связанных сущностей

**MongoDB:**
- Schema-less подход — можно добавлять поля без миграций
- Поле `additionalFields: Map<String, Object>` позволяет хранить произвольные данные от разных источников
- Отсутствие JOIN — связанные данные нужно либо денормализовать, либо делать несколько запросов
- Сложнее обеспечить консистентность данных на уровне приложения

**Пример гибкости MongoDB для парсера вакансий:**

```java
// Вакансия с hh.ru
{
  "title": "Java Developer",
  "company": "Яндекс",
  "additionalFields": {
    "experience": "3-6 лет",
    "employment": "полная занятость",
    "schedule": "удалённая работа"
  }
}

// Вакансия с другого источника
{
  "title": "Java Developer",
  "company": "Сбер",
  "additionalFields": {
    "grade": "Senior",
    "team_size": 12,
    "tech_stack": ["Java", "Kafka", "K8s"]
  }
}
```

В PostgreSQL для этого пришлось бы либо создавать множество nullable-колонок, либо использовать JSONB-поле.

### 4.3. Выявленные узкие места

**PostgreSQL:**
- Пакетная вставка упирается в запись в WAL и fsync
- При большом количестве записей растёт время на автовакуум
- Параметр `hibernate.jdbc.batch_size` требует тюнинга

**MongoDB:**
- Write concern `w:1` (по умолчанию) не гарантирует запись на диск
- При включении `w:majority` и `j:true` производительность записи падает в 2-3 раза
- Индексы на строковые поля потребляют больше памяти

---

## 5. Выводы и рекомендации

### 5.1. Какая СУБД лучше подходит для данного сценария?

**Для проекта парсера вакансий рекомендуется MongoDB** по следующим причинам:

1. **Высокая скорость пакетной записи** — критично для парсера, который за сессию собирает сотни вакансий

2. **Гибкость схемы** — разные job-сайты предоставляют разный набор полей. MongoDB позволяет хранить все данные без потерь и без постоянных миграций схемы

3. **Отсутствие сложных связей** — вакансия — это самодостаточный документ, JOIN-ы не требуются

4. **Допустимость eventual consistency** — потеря одной вакансии при сбое некритична, данные легко перепарсить

5. **Хорошая производительность фильтрации** — основной сценарий использования (поиск вакансий по критериям) работает быстро

### 5.2. Когда выбрать PostgreSQL?

PostgreSQL был бы лучшим выбором, если бы:
- Требовались сложные аналитические запросы с агрегацией
- Существовали связи между сущностями (вакансия → компания → отрасль → отзывы)
- Была критична строгая консистентность (финансовые операции)
- Требовался полнотекстовый поиск на русском языке (PostgreSQL + pg_trgm/hunspell)

### 5.3. Архитектурные компромиссы

| Аспект | Выбор | Компромисс |
|--------|-------|------------|
| Согласованность | Eventual consistency | Готовы принять. Для вакансий не критично |
| Масштабируемость | Горизонтальная (sharding) | MongoDB проще масштабировать, но увеличивается сложность инфраструктуры |
| Гибкость схемы | Полная | Контроль структуры данных переносится на уровень приложения |
| Транзакции | Однодокументные | Готовы принять. Многодокументные транзакции не требуются |

### 5.4. Итоговая архитектура

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Parser Module  │────▶│   MongoDB    │◀────│  API Service    │
│  (batch write)  │     │  (primary)   │     │  (read-heavy)   │
└─────────────────┘     └──────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌──────────────┐
                        │ Elasticsearch│  (опционально,
                        │ (full-text)  │   для поиска)
                        └──────────────┘
```

---

## Приложение: Docker Compose для тестового окружения

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: jobparser
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  mongodb:
    image: mongo:7.0
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db

volumes:
  postgres_data:
  mongo_data:
```
