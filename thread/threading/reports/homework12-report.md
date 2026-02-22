Ниже вариант отчёта по выполненному заданию (можете вставить в docx/odt или оформить как `REPORT.md`).

---

Отчёт по лабораторной работе  
«Многопоточная система событий на базе java.util.concurrent»

1. Цель работы

Целью работы было разработать многопоточную систему обработки событий (event system), которая может использоваться как основа для:

- системы уведомлений;
- системы логирования событий;
- обработки событий пользователей в веб‑приложениях.

Требовалось применить потокобезопасные коллекции и средства из пакета `java.util.concurrent`.

2. Постановка задачи

Необходимо реализовать:

- модель событий и обработчиков;
- диспетчер событий (event dispatcher / event bus), принимающий события от нескольких потоков‑производителей;
- асинхронную обработку событий в пуле потоков;
- хранение подписчиков (обработчиков) в потокобезопасных структурах данных;
- демонстрационную программу, показывающую работу системы.

3. Описание реализации

3.1. Модель событий и обработчиков

Был реализован базовый интерфейс события:

```java
interface Event {
    String getType();
    Instant getTimestamp();
}
```

На его основе создан конкретный тип событий пользователя:

```java
class UserEvent implements Event {
    private final String type;
    private final Instant timestamp;
    private final String userId;
    private final String payload;
    // геттеры, конструктор, toString()
}
```

Тип события (`type`) используется для маршрутизации к нужным обработчикам.

Обработчик событий описывается интерфейсом:

```java
interface EventHandler<E extends Event> {
    void handle(E event);
}
```

Реализован пример обработчика для логирования в консоль:

```java
class LoggingEventHandler implements EventHandler<UserEvent> {
    private final String name;
    @Override
    public void handle(UserEvent event) {
        System.out.printf("[%s] Обработчик %s получил событие: %s%n",
                Thread.currentThread().getName(), name, event);
    }
}
```

3.2. Диспетчер событий

Класс `EventDispatcher` реализует «шину событий» и управляет очередью и потоками.

Основные поля:

```java
private final BlockingQueue<Event> eventQueue;
// тип события -> список обработчиков
private final ConcurrentMap<String, CopyOnWriteArrayList<EventHandler<? extends Event>>> handlers;

// пул потоков, читающих из очереди
private final ExecutorService dispatchExecutor;
// пул потоков, выполняющих обработчики
private final ExecutorService handlerExecutor;

private final AtomicBoolean running = new AtomicBoolean(false);
private final int dispatchThreads;
```

Используемые структуры и классы:

- `LinkedBlockingQueue<Event>` — потокобезопасная блокирующая очередь событий;
- `ConcurrentHashMap` + `CopyOnWriteArrayList` — потокобезопасное хранение подписчиков по типу события;
- `ExecutorService` (`newFixedThreadPool`) — для диспетчеризации и обработки;
- `AtomicBoolean` — для управления состоянием (запущен/остановлен).

3.3. Регистрация обработчиков

Регистрация выполняется без внешней синхронизации, через `computeIfAbsent`:

```java
public <E extends Event> void registerHandler(String eventType, EventHandler<E> handler) {
    handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(handler);
}
```

3.4. Публикация событий

Публикация события в систему:

```java
public void publish(Event event) throws InterruptedException {
    System.out.println("Публикую событие: " + event);
    eventQueue.put(event); // блокируется, если очередь заполнена
}
```

Использование `put()` обеспечивает backpressure: при заполнении очереди поток‑производитель будет ждать.

3.5. Запуск и остановка диспетчера

```java
public void start() {
    if (!running.compareAndSet(false, true)) {
        return;
    }
    for (int i = 0; i < dispatchThreads; i++) {
        dispatchExecutor.submit(this::processEvents);
    }
}

public void stop() {
    running.set(false);
    dispatchExecutor.shutdownNow();
    handlerExecutor.shutdownNow();
}
```

3.6. Обработка событий из очереди

Метод `processEvents` реализует цикл потребителя («consumer») в паттерне Producer–Consumer:

```java
private void processEvents() {
    while (running.get() && !Thread.currentThread().isInterrupted()) {
        try {
            System.out.println(Thread.currentThread().getName() + " ждет событие...");
            Event event = eventQueue.take(); // блокируется при пустой очереди
            System.out.println(Thread.currentThread().getName()
                    + " получил событие из очереди: " + event);
            dispatch(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

3.7. Рассылка события обработчикам

Для каждого события находятся все зарегистрированные обработчики, и каждый обработчик запускается в отдельной задаче во втором пуле потоков:

```java
@SuppressWarnings("unchecked")
private void dispatch(Event event) {
    List<EventHandler<? extends Event>> eventHandlers = handlers.get(event.getType());
    if (eventHandlers == null || eventHandlers.isEmpty()) {
        System.out.println("Нет обработчиков для типа: " + event.getType());
        return;
    }

    for (EventHandler<? extends Event> handler : eventHandlers) {
        handlerExecutor.submit(() -> {
            try {
                System.out.println(Thread.currentThread().getName()
                        + " запускает обработчик для: " + event);
                ((EventHandler<Event>) handler).handle(event);
            } catch (Exception e) {
                System.err.println("Ошибка в обработчике: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
```

Важно: здесь используется отдельный пул `handlerExecutor`, чтобы потоки, читающие из очереди (`processEvents`), не блокировали выполнение самих обработчиков.

4. Демонстрационная программа

В методе `main` показана имитация загрузки:

- создаётся диспетчер с очередью и пулами потоков;
- регистрируются несколько обработчиков для типов `USER_LOGIN` и `USER_ACTION`;
- запускается несколько потоков‑производителей, которые публикуют события.

Фрагмент:

```java
EventDispatcher dispatcher = new EventDispatcher(
        100,  // ёмкость очереди
        4,    // потоки для чтения из очереди
        4     // потоки для обработчиков
);

LoggingEventHandler handler1 = new LoggingEventHandler("Logger-1");
LoggingEventHandler handler2 = new LoggingEventHandler("Logger-2");

dispatcher.registerHandler("USER_LOGIN", handler1);
dispatcher.registerHandler("USER_LOGIN", handler2);
dispatcher.registerHandler("USER_ACTION", new LoggingEventHandler("Actions-Logger"));

dispatcher.start();

// несколько продюсеров в пуле
ExecutorService producers = Executors.newFixedThreadPool(3);
// каждый продюсер публикует несколько событий USER_LOGIN / USER_ACTION
...
```

Вывод программы показывает:

- потоки `pool-1-thread-*` ждут события и получают их из очереди;
- потоки `pool-2-thread-*` запускают обработчики и логируют получение событий (`Обработчик Logger-1 получил событие: ...` и т.д.);
- все опубликованные события были распределены по соответствующим обработчикам.

5. Использованные средства java.util.concurrent

В ходе работы были применены следующие элементы пакета `java.util.concurrent`:

- `BlockingQueue` (`LinkedBlockingQueue`) — реализация очереди задач между потоками‑производителями и потоками‑потребителями;
- `ConcurrentHashMap` — потокобезопасное отображение типа события в список обработчиков;
- `CopyOnWriteArrayList` — потокобезопасный список обработчиков, позволяющий безопасно итерироваться по подписчикам одновременно с добавлением/удалением;
- `ExecutorService`, `Executors.newFixedThreadPool` — пулы потоков для диспетчеризации и обработки;
- `AtomicBoolean` — примитив синхронизации для безопасного управления состоянием «запущен/остановлен»;
- `TimeUnit`, `ExecutorService.shutdown()/awaitTermination()` — упорядоченное завершение работы потоков.

6. Особенности и отладка

В процессе выполнения задания были выявлены и устранены следующие особенности:

- Изначально использовался один пул потоков для чтения очереди и выполнения обработчиков. Это приводило к тому, что все потоки пула постоянно были заняты «вечными» задачами `processEvents`, а задачи обработчиков не начинали выполняться. Решение — разделить ответственность на два пула (`dispatchExecutor` и `handlerExecutor`).
- При отладке активно использовались лог‑сообщения в методах `publish`, `processEvents` и `dispatch`, что позволило визуально отследить жизненный цикл события: публикация → попадание в очередь → чтение воркером → запуск обработчика.
- Очередь `eventQueue` на момент просмотра её размера часто оказывается пустой, так как обработка идёт быстро: воркеры почти мгновенно выбирают элементы из очереди после публикации. Это нормальное поведение для правильно спроектированной системы.

7. Выводы

В ходе работы была реализована многопоточная система событий, удовлетворяющая требованиям:

- поддерживается несколько параллельных потоков‑производителей событий;
- события асинхронно обрабатываются в пуле потоков‑потребителей;
- использованы потокобезопасные структуры данных (`BlockingQueue`, `ConcurrentHashMap`, `CopyOnWriteArrayList`);
- логика может быть легко адаптирована под:
  - систему уведомлений (обработчики отправляют письма/уведомления),
  - систему логирования (обработчики пишут в файлы, БД, внешнее хранилище),
  - обработку пользовательских действий в веб‑приложениях.

Реализация продемонстрировала корректную работу с многопоточностью, отсутствие явных гонок данных и возможность масштабировать решение за счёт настройки числа потоков и параметров очереди.