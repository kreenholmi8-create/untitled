Отчёт по реализации и исследованию производительности блокирующей очереди  
на основе `synchronized` и `ReentrantLock`

---

## 1. Этапы выполнения задания

### 1.1. Определение общего контракта: интерфейс `BoundedBuffer<T>`

Сначала был определён интерфейс, задающий контракт для всех реализаций ограниченного буфера.

```java
package com.utmn.kolmykova.reentrant;

public interface BoundedBuffer<T> {
    void put(T item) throws InterruptedException;
    T take() throws InterruptedException;
    int size();
}
```

Интерфейс описывает три операции:

- `put(T item)` — добавить элемент в буфер, блокируется, если буфер заполнен.
- `take()` — взять элемент из буфера, блокируется, если буфер пуст.
- `size()` — получить текущее количество элементов в буфере.

Это позволяет легко сравнивать разные реализации, не меняя код продьюсеров/консьюмеров.

---

### 1.2. Реализация на базе `ReentrantLock` и `Condition`: `LockBasedBoundedBuffer<T>`

Реализация использует:

- массив `items` как кольцевой буфер;
- индексы `putIndex` и `takeIndex`;
- счётчик `count`;
- `ReentrantLock` и две `Condition`: `notFull` и `notEmpty`.

```java
package com.utmn.kolmykova.reentrant;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LockBasedBoundedBuffer<T> implements BoundedBuffer<T> {
    private final T[] items;
    private int putIndex = 0;
    private int takeIndex = 0;
    private int count = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    @SuppressWarnings("unchecked")
    public LockBasedBoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        this.items = (T[]) new Object[capacity];
    }

    @Override
    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) {
                notFull.await();
            }

            items[putIndex] = item;
            putIndex = (putIndex + 1) % items.length;
            count++;

            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }

            T item = items[takeIndex];
            items[takeIndex] = null;
            takeIndex = (takeIndex + 1) % items.length;
            count--;

            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}
```

Ключевые моменты:

- `lock.lock()` / `lock.unlock()` защищают все операции над общим состоянием (`items`, `putIndex`, `takeIndex`, `count`).
- `notFull.await()` — продьюсер ждёт, пока появится место.
- `notEmpty.await()` — консюмер ждёт, пока появится элемент.
- `notEmpty.signal()` — будит один ожидающий поток, когда буфер перестал быть пустым.
- `notFull.signal()` — будит один ожидающий поток, когда буфер перестал быть полным.

---

### 1.3. Реализация на базе `synchronized`, `wait()` и `notifyAll()`: `SynchronizedBoundedBuffer<T>`

Вторая реализация использует встроенную мониторы Java (`synchronized`) и механизмы `wait()`/`notifyAll()`.

```java
package com.utmn.kolmykova.reentrant;

public class SynchronizedBoundedBuffer<T> implements BoundedBuffer<T> {
    private final T[] items;
    private int putIndex = 0;
    private int takeIndex = 0;
    private int count = 0;

    @SuppressWarnings("unchecked")
    public SynchronizedBoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        this.items = (T[]) new Object[capacity];
    }

    @Override
    public synchronized void put(T item) throws InterruptedException {
        while (count == items.length) {
            wait();
        }

        items[putIndex] = item;
        putIndex = (putIndex + 1) % items.length;
        count++;

        notifyAll();
    }

    @Override
    public synchronized T take() throws InterruptedException {
        while (count == 0) {
            wait();
        }

        T item = items[takeIndex];
        items[takeIndex] = null;
        takeIndex = (takeIndex + 1) % items.length;
        count--;

        notifyAll();
        return item;
    }

    @Override
    public synchronized int size() {
        return count;
    }
}
```

Ключевые моменты:

- Методы `put`, `take`, `size` синхронизированы по `this`.
- Потоки, которые не могут продолжать выполнение, вызывают `wait()` (освобождая монитор объекта).
- После изменения состояния буфера вызывается `notifyAll()`, чтобы разбудить все ожидающие потоки (и продьюсеров, и консюмеров).

---

### 1.4. Потоки-производители: `Producer`

```java
package com.utmn.kolmykova.reentrant;

public class Producer implements Runnable {
    private final BoundedBuffer<Integer> buffer;
    private final int id;
    private final int produceCount;

    public Producer(int id, BoundedBuffer<Integer> buffer, int produceCount) {
        this.id = id;
        this.buffer = buffer;
        this.produceCount = produceCount;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < produceCount; i++) {
                int item = id * 1000000 + i; // уникальное значение
                buffer.put(item);
                System.out.println("Producer " + id + " put: " + item);
                // Thread.sleep(1); // имитация работы (опционально)
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Каждый продьюсер:

- генерирует `produceCount` уникальных элементов;
- кладёт их в буфер (`put`);
- выводит в консоль факт добавления.

---

### 1.5. Потоки-потребители: `Consumer`

```java
package com.utmn.kolmykova.reentrant;

public class Consumer implements Runnable {
    private final BoundedBuffer<Integer> buffer;
    private final int id;
    private final int consumeCount;

    public Consumer(int id, BoundedBuffer<Integer> buffer, int consumeCount) {
        this.id = id;
        this.buffer = buffer;
        this.consumeCount = consumeCount;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < consumeCount; i++) {
                Integer item = buffer.take();
                System.out.println("Consumer " + id + " took: " + item);
                // Thread.sleep(2); // имитация обработки (опционально)
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Каждый консюмер:

- выполняет `consumeCount` операций `take()`;
- выводит потреблённое значение в консоль.

---

### 1.6. Тестовый класс: запуск и измерение времени

```java
package com.utmn.kolmykova.reentrant;

public class BoundedBufferTest {
    public static void main(String[] args) throws InterruptedException {
        int capacity = 10;
        int producerCount = 10;
        int consumerCount = 4;
        int itemsPerProducer = 10000;

        // LockBasedBoundedBuffer<Integer> buffer = new LockBasedBoundedBuffer<>(capacity);
        SynchronizedBoundedBuffer<Integer> buffer = new SynchronizedBoundedBuffer<>(capacity);

        Thread[] producers = new Thread[producerCount];
        Thread[] consumers = new Thread[consumerCount];

        // Общее количество элементов, которое нужно потребить,
        // равномерно распределяем между consumer’ами
        int totalItems = producerCount * itemsPerProducer;
        int itemsPerConsumer = totalItems / consumerCount;

        for (int i = 0; i < producerCount; i++) {
            producers[i] = new Thread(
                    new Producer(i, buffer, itemsPerProducer),
                    "Producer-" + i
            );
        }

        for (int i = 0; i < consumerCount; i++) {
            consumers[i] = new Thread(
                    new Consumer(i, buffer, itemsPerConsumer),
                    "Consumer-" + i
            );
        }

        long start = System.currentTimeMillis();

        for (Thread t : producers) t.start();
        for (Thread t : consumers) t.start();

        for (Thread t : producers) t.join();
        for (Thread t : consumers) t.join();

        long end = System.currentTimeMillis();

        System.out.println("All threads finished. Time: " + (end - start) + " ms");
        System.out.println("Final buffer size: " + buffer.size());
    }
}
```

Этапы в тесте:

1. Настройка параметров (ёмкость буфера, количество продьюсеров и консюмеров, количество элементов).
2. Создание выбранной реализации буфера.
3. Создание и запуск потоков.
4. Ожидание завершения всех потоков (`join`).
5. Измерение общего времени работы и вывод конечного размера буфера.

---

## 2. Промежуточные результаты и выводы по производительности

На одном и том же сценарии были получены следующие результаты:

- Для `SynchronizedBoundedBuffer`:

  - `All threads finished. Time: 2604 ms`
  - `Final buffer size: 0`

- Для `LockBasedBoundedBuffer`:

  - `All threads finished. Time: 2383 ms`
  - `Final buffer size: 0`

Промежуточные выводы:

1. В обоих случаях финальный размер буфера равен 0.  
   Это означает, что:

   - все продьюсеры успешно произвели и положили в буфер свои элементы;
   - все консюмеры успешно их забрали;
   - не осталось «застрявших» элементов, нет дедлоков и потери данных.

2. В конкретном запуске реализация на `ReentrantLock` показала немного лучшее время (примерно на 200–250 мс быстрее):

   - разница небольшая и может зависеть от нагрузки, планировщика, выводов `System.out` и др.;
   - но в целом `ReentrantLock` часто показывает лучшую масштабируемость в высококонкурентных сценариях за счёт более гибких механизмов блокировки.

3. Использование `System.out.println` в каждом `put`/`take` сильно влияет на время, но оно одинаково влияет на оба подхода, поэтому сравнительный вывод всё равно валиден для данного теста.

---

## 3. Объяснение применения `ReentrantLock` и `Condition`

### 3.1. Общий принцип работы `ReentrantLock`

`ReentrantLock` — это явная (эксплицитная) блокировка. Основные моменты:

- Вызов `lock.lock()` захватывает блокировку или блокирует поток, пока она не освободится.
- Вызов `lock.unlock()` освобождает блокировку.
- Блокировка *реентерабельна*: один и тот же поток может захватывать её несколько раз (счётчик вложенности).

По функциональности это аналог `synchronized`, но:

- даёт возможность использовать отдельные условные переменные (`Condition`);
- предоставляет дополнительные методы (`tryLock()`, lock with timeout и т.п.);
- может использовать разные режимы очереди ожидания (fair/unfair).

### 3.2. Роль `Condition`

`Condition` — это аналог «мониторных» ожиданий, но привязанный к конкретной блокировке.

- У `ReentrantLock` можно создать несколько `Condition`:

  ```java
  private final Condition notFull  = lock.newCondition();
  private final Condition notEmpty = lock.newCondition();
  ```

- `await()`:

  - поток временно освобождает связанный `lock`;
  - засыпает и ждёт сигнала;
  - при пробуждении снова захватывает `lock` и продолжает выполнение (обычно — проверяя условие в цикле `while`).

- `signal()` / `signalAll()`:

  - будят один или все потоки, ожидающие на данном `Condition`.

В данном буфере:

- `notFull` — условие «буфер не полон», используется продьюсерами;
- `notEmpty` — условие «буфер не пуст», используется консюмерами.

Это позволяет:

- будить только тех, кому действительно есть что делать (например, `notEmpty.signal()` будит только консюмеров);
- избегать излишних пробуждений, как при `notifyAll()` у монитора.

### 3.3. Логика `put`/`take` с `ReentrantLock` и `Condition`

`put(T item)`:

```java
lock.lock();
try {
    while (count == items.length) {
        notFull.await();
    }

    items[putIndex] = item;
    putIndex = (putIndex + 1) % items.length;
    count++;

    notEmpty.signal();
} finally {
    lock.unlock();
}
```

Пошагово:

1. Захватываем блокировку `lock`.
2. Если буфер полон (`count == capacity`), делаем `notFull.await()`:
   - поток временно отдаёт `lock` и засыпает;
   - просыпается после `notFull.signal()` и вновь захватывает `lock`.
3. После выхода из цикла `while` гарантируется, что есть место.
4. Добавляем элемент в массив, обновляем индекс и `count`.
5. Сигналом `notEmpty.signal()` уведомляем одного из ожидающих консюмеров.
6. Освобождаем блокировку.

`take()` аналогично:

```java
lock.lock();
try {
    while (count == 0) {
        notEmpty.await();
    }

    T item = items[takeIndex];
    items[takeIndex] = null;
    takeIndex = (takeIndex + 1) % items.length;
    count--;

    notFull.signal();
    return item;
} finally {
    lock.unlock();
}
```

---

## 4. Примеры взаимодействия потоков producer’ов и consumer’ов

Рассмотрим типичный сценарий работы (обобщённо):

1. Буфер пуст.  
   Все продьюсеры начинают работу. Консюмеры вызывают `take()` и попадают в `while (count == 0) notEmpty.await()`, засыпая.

2. Продьюсер `P0` захватывает блокировку и выполняет `put`:

   - буфер не полон, элемент добавляется;
   - `count` увеличивается с 0 до 1;
   - вызывается `notEmpty.signal()` (или `notifyAll()` во второй реализации);
   - один или несколько консюмеров просыпаются, но пока не получают `lock`.

3. Продьюсер `P0` завершает `put` и освобождает `lock`.  
   Один из проснувшихся консюмеров (`C0`) захватывает `lock`, выходит из `await`, снова проверяет условие `while (count == 0)`:

   - теперь `count > 0`, условие ложно, консюмер забирает элемент;
   - `count` уменьшается, вызывается `notFull.signal()`.

4. Если буфер становится полон (например, в пик активности продьюсеров):

   - продьюсер, захвативший `lock`, видит `count == items.length` и уходит в `notFull.await()`;
   - консюмеры, в свою очередь, продолжают забирать элементы и после каждого `take` вызывают `notFull.signal()`, будя ожидающих продьюсеров.

5. Так происходит балансировка:  
   - при пустом буфере консюмеры ждут;
   - при переполненном буфере продьюсеры ждут;
   - состояние всегда корректно защищено единой блокировкой.

Логика взаимодействия у реализаций с `ReentrantLock` и `synchronized` концептуально одинаковая. Отличие — в том, каким механизмом оформлены ожидания и уведомления (отдельные `Condition` против единого монитора объекта с `wait()`/`notifyAll()`).

---

## 5. Итоговые выводы: сравнение `ReentrantLock + Condition` и `synchronized + wait/notifyAll`

### 5.1. Преимущества `ReentrantLock` и `Condition`

1. Раздельные очереди ожидания:

   - можно создать несколько `Condition` и точно направлять сигналы:
     - `notEmpty` — только для консюмеров;
     - `notFull` — только для продьюсеров.
   - меньше «ложных» пробуждений по сравнению с `notifyAll()`, который будит всех.

2. Более гибкое управление блокировкой:

   - есть методы `tryLock()`, `lockInterruptibly()`, блокировка с таймаутом;
   - можно тонко контролировать, как потоки борются за ресурс.

3. Потенциально лучшая производительность при высокой конкуренции:

   - более тонкие механизмы очередей ожидания;
   - меньше контекстных переключений за счёт более целевых сигналов.

4. Явное управление:

   - явно видно, где блокировка захватывается и освобождается;
   - можно использовать её в неблокирующих конструкциях (try/finally, разные методы и т.п.).

### 5.2. Недостатки `ReentrantLock` и `Condition`

1. Более сложное и многословное API:

   - нужно явно вызывать `lock.lock()` и `lock.unlock()` (не забывать `finally`);
   - больше кода, выше риск ошибки (например, забыть `unlock()` в исключительной ситуации).

2. Легче допустить ошибку:

   - запуск `await()` не удерживая соответствующий `lock` — ошибка;
   - использование нескольких `Condition` требует аккуратного проектирования.

3. В простых случаях не даёт существенного выигрыша:

   - при умеренной конкуренции и простом паттерне взаимодействия `synchronized` + `wait/notify` проще и достаточен.

### 5.3. Преимущества `synchronized` и `wait/notifyAll`

1. Простота:

   - `synchronized` интегрирован в язык;
   - меньше кода, ниже риск забыть освободить блокировку (освобождение делается автоматически при выходе из синхронизированного блока/метода).

2. Подходит для базовых сценариев:

   - когда нужна одна очередь ожидания и нет сложных условий;
   - легко читается и хорошо знаком большинству разработчиков.

3. Меньше зависимостей от деталей библиотеки:

   - используются только базовые возможности платформы.

### 5.4. Недостатки `synchronized` и `wait/notifyAll`

1. Одна очередь ожидания на монитор:

   - невозможно разделить продьюсеров и консюмеров на разные условия;
   - приходится часто использовать `notifyAll()`, будя все потоки, даже если некоторые не могут продолжить работу.

2. Меньшая гибкость:

   - нет `tryLock`, таймаутов, режимов «справедливости» и т.п.

3. Для сложных систем — менее удобен:

   - если появляются дополнительные состояния и условия, код с `wait/notifyAll` быстро усложняется и становится трудно читаемым.

---

## 6. Общий итог

1. Обе реализации (`SynchronizedBoundedBuffer` и `LockBasedBoundedBuffer`) корректно решают задачу синхронизированного ограниченного буфера:

   - потокобезопасный доступ к разделяемым данным;
   - блокировка продьюсеров при полном буфере и консюмеров при пустом;
   - отсутствие гонок, дедлоков и потери элементов (что подтверждается финальным размером буфера 0).

2. По измеренному времени в данном тесте:

   - реализация на `ReentrantLock` и `Condition` была немного быстрее, чем реализация на `synchronized` и `wait/notifyAll`;
   - разница не радикальная, но показывает потенциальный выигрыш от более тонкого управления ожиданиями.

3. Выбор между `ReentrantLock` и `synchronized`:

   - для простых задач и небольшого числа потоков достаточно `synchronized + wait/notifyAll`;
   - для более сложных или высоконагруженных систем (много потоков, несколько типов условий, гибкое управление блокировками) предпочтительнее `ReentrantLock + Condition`.

В данном примере реализация на `ReentrantLock` демонстрирует как минимум сопоставимую (а по результатам — немного лучшую) производительность и большую гибкость, но ценой более сложного кода.
