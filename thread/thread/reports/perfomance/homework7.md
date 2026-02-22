## 3Аналитический отчёт

### 1. Как взаимодействуют слои Hexagonal Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ВНЕШНИЙ МИР (Клиенты)                               │
│                    (HTTP-запросы, файлы, другие системы)                    │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ВХОДНЫЕ АДАПТЕРЫ (Driving Adapters)                     │
│  ┌──────────────────────────┐  ┌────────────────────────────────────────┐   │
│  │ VacancyAnalysisController│  │     ScheduledAnalysisAdapter           │   │
│  │     (@RestController)    │  │         (@Scheduled)                   │   │
│  └────────────┬─────────────┘  └──────────────────┬─────────────────────┘   │
└───────────────┼────────────────────────────────────┼─────────────────────────┘
                │                                    │
                │ вызывает                           │ вызывает
                ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ВХОДНЫЕ ПОРТЫ (Use Cases)                           │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                     AnalyzeVacancyUseCase                            │   │
│  │  - analyzeAll(recentDaysThreshold)                                   │   │
│  │  - calculateAverageSalaryByCity(city)                                │   │
│  │  - analyzeRequirementsSentiment(keywords)                            │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                                  │ implements
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ЯДРО (Domain Core)                                    │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                   VacancyAnalysisService                             │   │
│  │  - Чистая бизнес-логика                                              │   │
│  │  - Расчёт средней зарплаты                                           │   │
│  │  - Анализ тональности                                                │   │
│  │  - Группировка по параметрам                                         │   │
│  │  - БЕЗ Spring-аннотаций!                                             │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                     VacancyDomain (Model)                            │   │
│  │  - calculateAverageSalary()                                          │   │
│  │  - determineSeniorityLevel()                                         │   │
│  │  - isRecent(days)                                                    │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                                  │ использует
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ВЫХОДНЫЕ ПОРТЫ (Interfaces)                            │
│  ┌────────────────────────────┐  ┌──────────────────────────────────────┐   │
│  │   VacancyDataSource        │  │    VacancyResultPublisher            │   │
│  │  - fetchAll()              │  │  - publishAnalysisResult(result)     │   │
│  │  - fetchByCity(city)       │  │  - saveVacancies(list)               │   │
│  │  - fetchById(id)           │  │  - getPublisherName()                │   │
│  │  - isAvailable()           │  │                                      │   │
│  └────────────┬───────────────┘  └──────────────────┬───────────────────┘   │
└───────────────┼──────────────────────────────────────┼───────────────────────┘
                │                                      │
                │ implements                           │ implements
                ▼                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ВЫХОДНЫЕ АДАПТЕРЫ (Driven Adapters)                      │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────────────┐ │
│  │JpaVacancyDataSrc │ │FileVacancyDataSrc│ │RestApiVacancyDataSource     │ │
│  │(JpaRepository)   │ │(CSV file)        │ │(WebClient)                  │ │
│  └──────────────────┘ └──────────────────┘ └──────────────────────────────┘ │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────────────┐ │
│  │JpaResultPublisher│ │ConsolePublisher  │ │FileResultPublisher          │ │
│  │(JpaRepository)   │ │(System.out)      │ │(Files API)                  │ │
│  └──────────────────┘ └──────────────────┘ └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2. Насколько легко подменяются адаптеры

Подмена адаптеров выполняется **без изменения кода ядра**:

**Способ 1: Через конфигурацию Spring (profiles)**
```java
@Bean
@Profile("production")
public VacancyDataSource productionDataSource(VacancyRepository repo) {
    return new JpaVacancyDataSource(repo);
}

@Bean
@Profile("test")
public VacancyDataSource testDataSource() {
    return new FileVacancyDataSource("test-data/vacancies.csv");
}
```

**Способ 2: Через @Primary / @Qualifier**
```java
@Autowired
@Qualifier("fileVacancyDataSource")
private VacancyDataSource fileSource;
```

**Способ 3: Программная сборка (как в тестах)**
```java
VacancyDataSource source = new FileVacancyDataSource("data.csv");
VacancyResultPublisher pub = new ConsoleResultPublisher();
AnalyzeVacancyUseCase useCase = new VacancyAnalysisService(source, pub);
```

### 3. Корректность отделения бизнес-логики

| Критерий | Статус |
|----------|--------|
| Ядро не содержит @Service, @Component, @Autowired | ✅ |
| Ядро не содержит @RestController, @Repository | ✅ |
| Ядро не импортирует Spring Framework классы | ✅ |
| Ядро не зависит от JPA (Entity, Repository) | ✅ |
| Ядро работает только с интерфейсами (портами) | ✅ |
| Бизнес-логика инкапсулирована в доменной модели | ✅ |

### 4. Подтверждение независимого тестирования

Юнит-тесты `VacancyAnalysisServiceTest` и `VacancyDomainTest`:

- **Не используют** `@SpringBootTest`, `@MockBean`, `@Autowired`
- **Не загружают** Spring Application Context
- **Используют** только Mockito для создания моков портов
- **Выполняются** за миллисекунды (без времени на запуск Spring)
- **Демонстрируют** полную изоляцию ядра от инфраструктуры

### 5. Итоговая структура проекта

```
src/main/java/com/shanaurin/jobparser/
├── domain/
│   ├── model/
│   │   ├── VacancyDomain.java           # Доменная модель
│   │   └── VacancyAnalysisResult.java   # Результат анализа
│   ├── port/
│   │   ├── in/
│   │   │   └── AnalyzeVacancyUseCase.java   # Входной порт
│   │   └── out/
│   │       ├── VacancyDataSource.java       # Порт получения данных
│   │       └── VacancyResultPublisher.java  # Порт публикации
│   └── service/
│       └── VacancyAnalysisService.java      # ЯДРО бизнес-логики
├── adapter/
│   ├── in/
│   │   └── rest/
│   │       └── VacancyAnalysisController.java
│   └── out/
│       ├── persistence/
│       │   ├── JpaVacancyDataSource.java
│       │   └── JpaVacancyResultPublisher.java
│       ├── file/
│       │   ├── FileVacancyDataSource.java
│       │   └── FileResultPublisher.java
│       ├── rest/
│       │   └── RestApiVacancyDataSource.java
│       └── console/
│           └── ConsoleResultPublisher.java
└── config/
    └── HexagonalConfig.java
```

### 6. Результаты тестов

```bash
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.shanaurin.jobparser.controller.ParseControllerTest
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
WARNING: A Java agent has been loaded dynamically (C:\Users\grspe\.m2\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
19:27:35.540 [main] INFO org.springframework.mock.web.MockServletContext -- Initializing Spring TestDispatcherServlet ''
19:27:35.543 [main] INFO org.springframework.test.web.servlet.TestDispatcherServlet -- Initializing Servlet ''
19:27:35.552 [main] INFO org.springframework.test.web.servlet.TestDispatcherServlet -- Completed initialization in 5 ms
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.779 s -- in com.shanaurin.jobparser.controller.ParseControllerTest
[INFO] Running com.shanaurin.jobparser.controller.VacancyControllerTest
19:27:36.184 [main] INFO org.springframework.mock.web.MockServletContext -- Initializing Spring TestDispatcherServlet ''
19:27:36.184 [main] INFO org.springframework.test.web.servlet.TestDispatcherServlet -- Initializing Servlet ''
19:27:36.184 [main] INFO org.springframework.test.web.servlet.TestDispatcherServlet -- Completed initialization in 0 ms
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.224 s -- in com.shanaurin.jobparser.controller.VacancyControllerTest
[INFO] Running com.shanaurin.jobparser.DemoApplicationTests
19:27:36.443 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.shanaurin.jobparser.DemoApplicationTests]: DemoApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
19:27:36.645 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.shanaurin.jobparser.JobParserApplication for test class com.shanaurin.jobparser.DemoApplicationTests

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.5.7)

2026-01-05T19:27:38.721+03:00  INFO 9580 --- [vacancy-parser] [           main] c.s.jobparser.DemoApplicationTests       : Starting DemoApplicationTests using Java 21.0.9 with PID 9580 (started by grspe in C:\work\source\java\s5-netology-threading)
2026-01-05T19:27:38.724+03:00  INFO 9580 --- [vacancy-parser] [           main] c.s.jobparser.DemoApplicationTests       : No active profile set, falling back to 1 default profile: "default"
2026-01-05T19:27:40.720+03:00  INFO 9580 --- [vacancy-parser] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-01-05T19:27:40.874+03:00  INFO 9580 --- [vacancy-parser] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 130 ms. Found 1 JPA repository interface.
2026-01-05T19:27:41.414+03:00  INFO 9580 --- [vacancy-parser] [           main] o.s.cloud.context.scope.GenericScope     : BeanFactory id=2668aa87-1cbe-34b6-b87d-523a3dfc9619
2026-01-05T19:27:42.170+03:00  INFO 9580 --- [vacancy-parser] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-01-05T19:27:42.559+03:00  INFO 9580 --- [vacancy-parser] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:job-parser-db user=SA
2026-01-05T19:27:42.563+03:00  INFO 9580 --- [vacancy-parser] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-01-05T19:27:42.812+03:00  INFO 9580 --- [vacancy-parser] [           main] o.hibernate.jpa.internal.util.LogHelper  : HHH000204: Processing PersistenceUnitInfo [name: default]
2026-01-05T19:27:42.952+03:00  INFO 9580 --- [vacancy-parser] [           main] org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.6.33.Final
2026-01-05T19:27:43.025+03:00  INFO 9580 --- [vacancy-parser] [           main] o.h.c.internal.RegionFactoryInitiator    : HHH000026: Second-level cache disabled
2026-01-05T19:27:43.313+03:00  INFO 9580 --- [vacancy-parser] [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-01-05T19:27:43.452+03:00  WARN 9580 --- [vacancy-parser] [           main] org.hibernate.orm.deprecation            : HHH90000025: H2Dialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-01-05T19:27:43.487+03:00  INFO 9580 --- [vacancy-parser] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
        Database JDBC URL [Connecting through datasource 'HikariDataSource (HikariPool-1)']
        Database driver: undefined/unknown
        Database version: 2.3.232
        Autocommit mode: undefined/unknown
        Isolation level: undefined/unknown
        Minimum pool size: undefined/unknown
        Maximum pool size: undefined/unknown
2026-01-05T19:27:44.827+03:00  INFO 9580 --- [vacancy-parser] [           main] o.h.e.t.j.p.i.JtaPlatformInitiator       : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-01-05T19:27:44.914+03:00 DEBUG 9580 --- [vacancy-parser] [           main] org.hibernate.SQL                        : alter table if exists vacancies alter column salary set data type varchar(255)
Hibernate: alter table if exists vacancies alter column salary set data type varchar(255)
2026-01-05T19:27:44.916+03:00 DEBUG 9580 --- [vacancy-parser] [           main] org.hibernate.SQL                        : alter table if exists vacancies alter column source set data type varchar(255)
Hibernate: alter table if exists vacancies alter column source set data type varchar(255)
2026-01-05T19:27:44.916+03:00 DEBUG 9580 --- [vacancy-parser] [           main] org.hibernate.SQL                        : alter table if exists vacancies alter column url set data type varchar(255)
Hibernate: alter table if exists vacancies alter column url set data type varchar(255)
2026-01-05T19:27:44.936+03:00  INFO 9580 --- [vacancy-parser] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-01-05T19:27:47.018+03:00  WARN 9580 --- [vacancy-parser] [           main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-01-05T19:27:47.058+03:00  INFO 9580 --- [vacancy-parser] [           main] o.s.b.a.w.s.WelcomePageHandlerMapping    : Adding welcome page template: index
2026-01-05T19:27:48.377+03:00  INFO 9580 --- [vacancy-parser] [           main] o.s.cloud.commons.util.InetUtils         : Cannot determine local hostname
2026-01-05T19:27:48.797+03:00  INFO 9580 --- [vacancy-parser] [           main] o.s.b.a.h2.H2ConsoleAutoConfiguration    : H2 console available at '/h2-console'. Database available at 'jdbc:h2:mem:job-parser-db'
2026-01-05T19:27:49.862+03:00  INFO 9580 --- [vacancy-parser] [           main] o.s.cloud.commons.util.InetUtils         : Cannot determine local hostname
2026-01-05T19:27:49.919+03:00  INFO 9580 --- [vacancy-parser] [           main] o.s.b.a.e.web.EndpointLinksResolver      : Exposing 4 endpoints beneath base path '/actuator'
2026-01-05T19:27:50.041+03:00  INFO 9580 --- [vacancy-parser] [           main] c.s.jobparser.DemoApplicationTests       : Started DemoApplicationTests in 12.916 seconds (process running for 18.988)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.91 s -- in com.shanaurin.jobparser.DemoApplicationTests
[INFO] Running com.shanaurin.jobparser.domain.model.VacancyDomainTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.229 s -- in com.shanaurin.jobparser.domain.model.VacancyDomainTest
[INFO] Running com.shanaurin.jobparser.domain.service.VacancyAnalysisServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.213 s -- in com.shanaurin.jobparser.domain.service.VacancyAnalysisServiceTest
[INFO] Running com.shanaurin.jobparser.service.MockVacancyCacheServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.016 s -- in com.shanaurin.jobparser.service.MockVacancyCacheServiceTest
[INFO] Running com.shanaurin.jobparser.service.ParseServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.523 s -- in com.shanaurin.jobparser.service.ParseServiceTest
[INFO] Running com.shanaurin.jobparser.service.scheduler.VacancySchedulerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.006 s -- in com.shanaurin.jobparser.service.scheduler.VacancySchedulerTest
[INFO] Running com.shanaurin.jobparser.service.UrlQueueServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.182 s -- in com.shanaurin.jobparser.service.UrlQueueServiceTest
[INFO] Running com.shanaurin.jobparser.service.VacancyParserTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.157 s -- in com.shanaurin.jobparser.service.VacancyParserTest
[INFO] Running com.shanaurin.jobparser.service.VacancyServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in com.shanaurin.jobparser.service.VacancyServiceTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  36.912 s
[INFO] Finished at: 2026-01-05T19:27:54+03:00
[INFO] ------------------------------------------------------------------------
```
