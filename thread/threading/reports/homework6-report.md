### Отчёт по заданию с использованием `volatile`, `AtomicInteger` и `AtomicReference`

#### 1. Исходный код

```java
package com.utmn.kolmykova.atomic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ConcurrentTest {

    private static class StoppableWorker implements Runnable {
        private volatile boolean running = true;
        private final AtomicInteger counter;
        private final AtomicReference<String> cache;
        private final Supplier<String> supplier;

        public StoppableWorker(AtomicInteger counter,
                               AtomicReference<String> cache,
                               Supplier<String> supplier) {
            this.counter = counter;
            this.cache = cache;
            this.supplier = supplier;
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            while (running) {
                // инкремент счётчика
                counter.incrementAndGet();

                // доступ к кэшу
                String current = cache.get();
                if (current == null) {
                    String newVal = supplier.get();
                    cache.compareAndSet(null, newVal);
                }

                // чуть разгрузим CPU
                if (counter.get() % 1_000_000 == 0) {
                    Thread.yield();
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger globalCounter = new AtomicInteger();
        AtomicReference<String> globalCache = new AtomicReference<>();

        int threadsCount = 8;
        StoppableWorker[] workers = new StoppableWorker[threadsCount];
        Thread[] threads = new Thread[threadsCount];

        Supplier<String> supplier = () -> "Значение кэша: " + System.nanoTime();

        for (int i = 0; i < threadsCount; i++) {
            workers[i] = new StoppableWorker(globalCounter, globalCache, supplier);
            threads[i] = new Thread(workers[i], "Worker-" + i);
        }

        long start = System.currentTimeMillis();
        for (Thread t : threads) {
            t.start();
        }

        // Нагрузка в течение 3 секунд
        Thread.sleep(3000);

        for (StoppableWorker w : workers) {
            w.stop();
        }
        for (Thread t : threads) {
            t.join();
        }
        long end = System.currentTimeMillis();

        System.out.println("Всего инкрементов: " + globalCounter.get());
        System.out.println("Значение в кэше: " + globalCache.get());
        System.out.println("Время работы (мс): " + (end - start));
    }
}
```

---

#### 2. Этапы выполнения задания

1. Определён класс `StoppableWorker`, реализующий `Runnable`, с разделяемыми полями:
   `AtomicInteger counter`, `AtomicReference<String> cache`, `Supplier<String> supplier` и флаг `volatile boolean running`.
2. В методе `run()` организован циклический инкремент счётчика `counter`, ленивое заполнение кэша `cache` и периодический вызов `Thread.yield()` для разгрузки CPU.
3. В `main` созданы общие для всех потоков:
   `AtomicInteger globalCounter` и `AtomicReference<String> globalCache`.
4. Создано 8 рабочих потоков, каждому переданы ссылки на общие объекты.
5. Потоки запускаются, работают ~3 секунды, затем им посылается сигнал остановки (`stop()`), и происходит `join()` для корректного завершения.
6. В конце выводятся:
   общее число инкрементов, финальное значение кэша и время работы.

---

#### 3. Промежуточные результаты (типичные примеры)

Конкретные числа зависят от мощности процессора, но характер работы таков:

Через ~1 секунду работы можно ожидать, например (если добавить отладочные выводы):

- Примерное значение `counter`:
  `counter ≈ 80 000 000` (по нескольку миллионов инкрементов на поток).
- `cache` после первых миллисекунд:
  `cache = "Значение кэша: 123456789012345"`  
  и далее уже не меняется, так как `compareAndSet(null, newVal)` срабатывает только один раз, а затем `current != null`.

К финалу:

- `globalCounter.get()` — большое число (десятки или сотни миллионов), которое:
  1) не «теряет» инкременты,
  2) монотонно возрастает.
- `globalCache.get()` — одна строка, установленная одним из потоков:
  `"Значение кэша: <некоторое значение nanoTime>"`.
- `end - start` — около 3000–3100 мс (учитывая запуск/остановку потоков).

---

#### 4. Объяснение использования `volatile`, `AtomicInteger` и `AtomicReference`

Использование `volatile` для `running`:

- Поле `running` используется как флаг завершения.  
- Ключевое слово `volatile` гарантирует:
  - видимость изменений между потоками: как только один поток вызвал `stop()` и записал `false`, другие потоки в цикле `while (running)` увидят обновлённое значение без дополнительной синхронизации;
  - запрет переупорядочивания операций вокруг чтения/записи `running`.
- Без `volatile` поток-рабочий мог бы «кэшировать» значение `running` в регистре и никогда не увидеть, что его поменяли, что потенциально ведёт к бесконечному циклу.

Использование `AtomicInteger` для `counter`:

- Операция `counter.incrementAndGet()` — атомарная.  
  Модель памяти Java гарантирует, что инкремент:
  - читается,
  - изменяется,
  - записывается как единая неделимая операция.
- Если бы использовался обычный `int` с `counter++`, при одновременном доступе из нескольких потоков происходила бы потеря инкрементов (классическая гонка данных).
- `get()` также потокобезопасен и даёт согласованное значение на момент вызова.

Использование `AtomicReference<String>` для `cache`:

- Кэш заполняется лениво: при первом обращении одним из потоков.
- `cache.get()` получает текущее значение ссылки.
- `compareAndSet(null, newVal)` делает атомарную проверку и установку:
  - если значение было `null`, оно станет `newVal`,
  - если другой поток уже успел записать значение, операция вернёт `false`, и кэш не будет перезаписан.
- Тем самым достигается потокобезопасная инициализация «одиночного» значения без использования `synchronized` и без риска состояния гонки.

---

#### 5. Примеры корректных и некорректных ситуаций в многопоточности

Корректные ситуации (в данном коде):

- Атомарный счётчик:
  `counter.incrementAndGet()` безопасен при одновременном вызове из множества потоков: итоговое значение соответствует сумме всех инкрементов.
- Ленивый потокобезопасный кэш:
  несколько потоков могут одновременно обнаружить, что `cache == null`, но только один реально установит значение благодаря `compareAndSet(null, newVal)`.
- Остановка потоков:
  флаг `volatile running` корректно сигнализирует всем потокам о завершении без блокировок.

Некорректные ситуации (так делать нельзя):

1. Заменить `AtomicInteger` на обычный `int` и использовать `counter++`:
   - это не атомарная операция (чтение–инкремент–запись),
   - при гонках данных часть инкрементов «потеряется»,
   - итоговый `counter` будет меньше ожидаемого.

2. Убрать `volatile` у `running`:
   - возможна ситуация, когда рабочий поток «застрянет» в `while (running)`, не увидев изменение флага;
   - программа не будет корректно завершаться.

3. Не использовать `compareAndSet` для кэша, а писать:

   ```java
   if (cache.get() == null) {
       cache.set(supplier.get()); // некорректно в многопоточной среде
   }
   ```

   - два потока могут одновременно увидеть `null` и оба вызвать `set(...)`;
   - кэш будет установливать значение несколько раз, нарушая ожидаемую семантику «инициализация один раз».

---

#### 6. Итоговые выводы о применении `volatile` и Atomic-переменных

1. `volatile` полезен для:
   - флагов, по которым потоки координируют своё поведение (остановка/запуск, состояние),
   - случаев, где нужно обеспечить только видимость и упорядочивание, но не атомарность сложных операций.

2. `AtomicInteger`, `AtomicReference` и другие классы `java.util.concurrent.atomic` подходят для:
   - реализации безблокирующих (lock-free) алгоритмов,
   - счётчиков, флагов и простых структур, требующих атомарных изменений,
   - ленивой инициализации разделяемых объектов через `compareAndSet`.

3. В многозадачных приложениях:
   - `volatile` решает проблему видимости, но не решает проблему атомарности операций;
   - Atomic-переменные обеспечивают и атомарность отдельных операций, и необходимый уровень видимости и упорядочивания;
   - для более сложных инвариантов (несколько связанных полей, транзакционные изменения) обычно требуется либо явная синхронизация (`synchronized`, `ReentrantLock`), либо более сложные структуры (STM, высокоуровневые concurrent-коллекции).

В данном примере сочетание `volatile` и Atomic-классов позволяет реализовать простой и эффективный по производительности многопоточный тест без явных блокировок и с корректным поведением при параллельном доступе.
