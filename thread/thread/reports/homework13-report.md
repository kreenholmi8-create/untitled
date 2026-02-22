Отчёт по лабораторной работе  
Тема: Использование `BlockingQueue` для реализации модели Producer–Consumer

1. Цель работы

Изучить механизм организации обмена задачами между потоками с помощью интерфейса `BlockingQueue` в Java на примере модели «производитель–потребитель»: несколько производителей формируют задачи (URL), а несколько потребителей их обрабатывают (имитация скачивания страницы и извлечения заголовка).

2. Постановка задачи

Реализовать многопоточное приложение, которое:

- использует общую потокобезопасную очередь задач `BlockingQueue<String>`;
- содержит несколько потоков-производителей (Producer), которые периодически генерируют URL и помещают их в очередь;
- содержит несколько потоков-потребителей (Consumer), которые извлекают URL из очереди, «обрабатывают» их (скачивание и парсинг заголовка) и выводят результат;
- корректно завершает работу всех потоков с использованием специального сигнала завершения (poison pill).

3. Используемые технологии и средства

- Язык программирования: Java  
- Пакет: `java.util.concurrent`  
- Основные классы:
  - `BlockingQueue`, `LinkedBlockingQueue`
  - `TimeUnit`
  - `Thread`, интерфейс `Runnable`

4. Описание реализации

4.1. Общая структура программы

Программа состоит из:

- главного класса `BlockingQueueExample` с методом `main`;
- вложенного класса `UrlProducer` (производитель);
- вложенного класса `UrlConsumer` (потребитель).

В методе `main`:

- создаётся общая очередь:
  ```java
  BlockingQueue<String> queue = new LinkedBlockingQueue<>();
  ```
- задаётся число производителей и потребителей:
  ```java
  int producerCount = 2;
  int consumerCount = 3;
  ```
- создаются и запускаются потоки-производители и потоки-потребители;
- после некоторого времени работы (5 секунд) производители прерываются;
- в очередь помещается по одному «яду» (`POISON_PILL`) на каждого потребителя;
- ожидается завершение всех потоков.

4.2. Производитель (`UrlProducer`)

Назначение: генерировать задачи (URL) и помещать их в `BlockingQueue`.

Основные элементы:

```java
static class UrlProducer implements Runnable {
    private final BlockingQueue<String> queue;
    private final String producerName;
    private int counter = 0;

    public UrlProducer(BlockingQueue<String> queue, String producerName) {
        this.queue = queue;
        this.producerName = producerName;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String url = generateUrl();
                System.out.printf("%s: сгенерировал задачу: %s%n", producerName, url);
                queue.put(url);                 // блокирующее добавление
                TimeUnit.MILLISECONDS.sleep(300);
            }
        } catch (InterruptedException e) {
            System.out.printf("%s: прерван и завершает работу%n", producerName);
            Thread.currentThread().interrupt();
        }
    }

    private String generateUrl() {
        return "http://example.com/page/" + (counter++);
    }
}
```

Особенности:

- Используется `queue.put(url)` — метод блокируется, если очередь ограниченной ёмкости заполнена (в нашем случае очередь не ограничена, но интерфейс демонстрируется).
- Генерация URL происходит с задержкой 300 мс, что имитирует скорость появления новых задач.
- Корректно обрабатывается `InterruptedException` при остановке потока.

4.3. Потребитель (`UrlConsumer`)

Назначение: извлекать URL из очереди, «скачивать» страницу, парсить заголовок и выводить результат.

Основные элементы:

```java
static class UrlConsumer implements Runnable {
    private final BlockingQueue<String> queue;
    private final String consumerName;

    public UrlConsumer(BlockingQueue<String> queue, String consumerName) {
        this.queue = queue;
        this.consumerName = consumerName;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String url = queue.take();  // блокирующее получение
                if (POISON_PILL.equals(url)) {
                    System.out.printf("%s: получил сигнал остановки%n", consumerName);
                    break;
                }

                System.out.printf("%s: получил задачу: %s%n", consumerName, url);

                String html = downloadPage(url);
                String title = parseTitle(html);

                System.out.printf("%s: обработал %s, заголовок: \"%s\"%n",
                        consumerName, url, title);
            }
        } catch (InterruptedException e) {
            System.out.printf("%s: прерван и завершает работу%n", consumerName);
            Thread.currentThread().interrupt();
        }
    }

    private String downloadPage(String url) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(500);
        return "<html><head><title>Title for " + url + "</title></head><body>...</body></html>";
    }

    private String parseTitle(String html) {
        String startTag = "<title>";
        String endTag = "</title>";
        int start = html.indexOf(startTag);
        int end = html.indexOf(endTag);
        if (start == -1 || end == -1 || start >= end) {
            return "No title";
        }
        return html.substring(start + startTag.length(), end);
    }
}
```

Особенности:

- Используется `queue.take()` — метод блокируется, если очередь пуста, что автоматически синхронизирует производителей и потребителей.
- Для завершения работы используется специальная строка `POISON_PILL`. Получив её, потребитель выводит сообщение и выходит из цикла.
- Имитация скачивания страницы — задержка 500 мс.
- «Парсинг» заголовка — поиск текста между тегами `<title>` и `</title>`.

4.4. Механизм завершения работы (poison pill)

В классе определена константа:

```java
private static final String POISON_PILL = "__STOP__";
```

После остановки производителей (через `interrupt` и `join`) в очередь помещается по одному такому элементу на каждый поток-потребитель:

```java
for (int i = 0; i < consumerCount; i++) {
    queue.put(POISON_PILL);
}
```

Каждый потребитель при получении `POISON_PILL` завершает свой цикл и корректно выходит.

Это гарантирует:

- отсутствие зависания потребителей в ожидании задач;
- контролируемое завершение всей системы.

5. Анализ полученного вывода

Фрагмент вывода:

- Производители регулярно генерируют задачи:
  - `Producer-0: сгенерировал задачу: http://example.com/page/0`
  - `Producer-1: сгенерировал задачу: http://example.com/page/0`
- Потребители берут задачи из очереди и обрабатывают их параллельно:
  - `Consumer-0: получил задачу: http://example.com/page/0`
  - `Consumer-1: получил задачу: http://example.com/page/0`
  - ...
  - `Consumer-0: обработал http://example.com/page/7, заголовок: "Title for http://example.com/page/7"`
- После прерывания производителей:
  - `Producer-0: прерван и завершает работу`
  - `Producer-1: прерван и завершает работу`
- Потребители продолжают обрабатывать оставшиеся в очереди задачи, затем получают сигнал остановки:
  - `Consumer-1: получил сигнал остановки`
  - `Consumer-2: получил сигнал остановки`
  - `Consumer-0: получил сигнал остановки`
- В конце:
  - `Все производители и потребители завершили работу.`

Из вывода видно:

- задачи распределяются между потребителями примерно равномерно (в зависимости от планировщика потоков);
- порядок обработки задач может отличаться от порядка их генерации разных производителями, но для каждого конкретного потока-потребителя относительный порядок сохраняется (FIFO в пределах очереди);
- система корректно завершает работу без зависаний и потерь задач.

6. Выводы

- Интерфейс `BlockingQueue` (в данном случае `LinkedBlockingQueue`) позволяет просто и надёжно организовать обмен задачами между потоками без явной ручной синхронизации (`wait/notify`, `synchronized`).
- Модель Producer–Consumer естественно реализуется с помощью блокирующих операций `put` и `take`:
  - производители блокируются при переполнении очереди (для ограниченной очереди),
  - потребители блокируются при пустой очереди.
- Использование специального маркера (poison pill) — простой и распространённый способ корректно завершать работу множества потребителей.
- Пример демонстрирует базовую архитектуру очередей задач, используемую в серверах, системах обработки запросов, конвейерах обработки данных и других многопоточных приложениях.

Если нужно, могу сократить отчёт до 1–2 страниц по ГОСТ/методичке (с титульным листом/целью/ходом работы/выводами) или адаптировать под конкретный шаблон вашего вуза.