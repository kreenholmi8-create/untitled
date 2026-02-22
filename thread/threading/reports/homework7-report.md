# Домашнее задание 7. Deadlock, livelock, starvation

## Deadlock

Была написана программа:

```java
package com.utmn.kolmykova.starvation;

public class DeadlockDemo {

    private static final Object resourceA = new Object();
    private static final Object resourceB = new Object();

    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            System.out.println("T1: пытаюсь захватить resourceA");
            synchronized (resourceA) {
                System.out.println("T1: захватил resourceA");
                sleep(100);

                System.out.println("T1: пытаюсь захватить resourceB");
                synchronized (resourceB) {
                    System.out.println("T1: захватил resourceB");
                }
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            System.out.println("T2: пытаюсь захватить resourceB");
            synchronized (resourceB) {
                System.out.println("T2: захватил resourceB");
                sleep(100);

                System.out.println("T2: пытаюсь захватить resourceA");
                synchronized (resourceA) {
                    System.out.println("T2: захватил resourceA");
                }
            }
        }, "Thread-2");

        t1.start();
        t2.start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
```

```bash
Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x00000245d8474bf0 (object 0x000000071946bdd0, a java.lang.Object),
  which is held by "Thread-2"

"Thread-2":
  waiting to lock monitor 0x00000245d8474950 (object 0x000000071946bdc0, a java.lang.Object),
  which is held by "Thread-1"

Java stack information for the threads listed above:
===================================================
"Thread-1":
        at com.utmn.kolmykova.starvation.DeadlockDemo.lambda$main$0(DeadlockDemo.java:17)
        - waiting to lock <0x000000071946bdd0> (a java.lang.Object)
        - locked <0x000000071946bdc0> (a java.lang.Object)
        at com.utmn.kolmykova.starvation.DeadlockDemo$$Lambda/0x0000024594003520.run(Unknown Source)
        at java.lang.Thread.runWith(java.base@21.0.8/Thread.java:1596)
        at java.lang.Thread.run(java.base@21.0.8/Thread.java:1583)
"Thread-2":
        at com.utmn.kolmykova.starvation.DeadlockDemo.lambda$main$1(DeadlockDemo.java:30)
        - waiting to lock <0x000000071946bdc0> (a java.lang.Object)
        - locked <0x000000071946bdd0> (a java.lang.Object)
        at com.utmn.kolmykova.starvation.DeadlockDemo$$Lambda/0x0000024594003740.run(Unknown Source)
        at java.lang.Thread.runWith(java.base@21.0.8/Thread.java:1596)
        at java.lang.Thread.run(java.base@21.0.8/Thread.java:1583)

Found 1 deadlock.
```

Заблокированные потоки и их состояние:

1) Поток "Thread-1"  
- Состояние по дампу: ожидает монитор (waiting to lock) объекта  
  - Ждет блокировки объекта `<0x000000071946bdd0>` (monitor `0x00000245d8474bf0`)  
  - Уже удерживает блокировку объекта `<0x000000071946bdc0>`  
- Стек:
  - `com.utmn.kolmykova.starvation.DeadlockDemo.lambda$main$0(DeadlockDemo.java:17)`
    - waiting to lock `<0x000000071946bdd0>`
    - locked `<0x000000071946bdc0>`
  - далее вызовы `Thread.runWith`, `Thread.run`

2) Поток "Thread-2"  
- Состояние по дампу: ожидает монитор (waiting to lock) другого объекта  
  - Ждет блокировки объекта `<0x000000071946bdc0>` (monitor `0x00000245d8474950`)  
  - Уже удерживает блокировку объекта `<0x000000071946bdd0>`  
- Стек:
  - `com.utmn.kolmykova.starvation.DeadlockDemo.lambda$main$1(DeadlockDemo.java:30)`
    - waiting to lock `<0x000000071946bdc0>`
    - locked `<0x000000071946bdd0>`
  - далее вызовы `Thread.runWith`, `Thread.run`

Связь с Deadlock:

- "Thread-1" держит монитор объекта A (`<0x000000071946bdc0>`) и ждет монитор объекта B (`<0x000000071946bdd0>`).
- "Thread-2" держит монитор объекта B и ждет монитор объекта A.
- Возник круговой захват ресурсов (циклическое ожидание): каждый поток ожидает ресурс, который не освободит другой, т.к. тоже ждет.  
Именно это описано в дампе фразами:
- `"Thread-1": waiting to lock ... which is held by "Thread-2"`
- `"Thread-2": waiting to lock ... which is held by "Thread-1"`

В результате JVM сообщает: `Found one Java-level deadlock` и перечисляет оба потока как участники взаимной блокировки.

```java
package com.utmn.kolmykova.starvation;

public class DeadlockFixedDemo {

    private static final Object resourceA = new Object();
    private static final Object resourceB = new Object();

    public static void main(String[] args) {
        Thread t1 = new Thread(() -> doWork(resourceA, resourceB), "T1");
        Thread t2 = new Thread(() -> doWork(resourceB, resourceA), "T2"); // порядок аргументов другой
        t1.start();
        t2.start();
    }

    private static void doWork(Object r1, Object r2) {
        Object first = r1.hashCode() < r2.hashCode() ? r1 : r2;
        Object second = (first == r1) ? r2 : r1;

        synchronized (first) {
            System.out.println(Thread.currentThread().getName() + " захватил first");
            sleep(100);
            synchronized (second) {
                System.out.println(Thread.currentThread().getName() + " захватил second");
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
```

Результат:
- Порядок захвата определяется по hashCode, то есть глобальное правило: «меньший объект — быстрее захватывать».
- Все потоки следуют одному порядку, поэтому циклического ожидания не возникает.

## Livelock

### Livelock demo

```java
package com.utmn.kolmykova.starvation;

public class LivelockDemo {

    static class Resource {
        private volatile boolean inUseByFirst = true;

        public void use(String threadName) {
            System.out.println(threadName + " использует ресурс");
        }

        public boolean isInUseByFirst() {
            return inUseByFirst;
        }

        public void setInUseByFirst(boolean inUseByFirst) {
            this.inUseByFirst = inUseByFirst;
        }
    }

    public static void main(String[] args) {
        Resource resource = new Resource();

        Thread t1 = new Thread(() -> {
            while (true) {
                if (!resource.isInUseByFirst()) {
                    System.out.println("T1: ресурс у второго, уступаю");
                    sleep(100);
                    continue;
                }
                // "Вежливо" уступает, если думает, что мешает
                System.out.println("T1: думаю, что мешаю, переключаю флаг");
                resource.setInUseByFirst(false);
                sleep(100);
            }
        }, "T1");

        Thread t2 = new Thread(() -> {
            while (true) {
                if (resource.isInUseByFirst()) {
                    System.out.println("T2: ресурс у первого, уступаю");
                    sleep(100);
                    continue;
                }
                System.out.println("T2: думаю, что мешаю, переключаю флаг");
                resource.setInUseByFirst(true);
                sleep(100);
            }
        }, "T2");

        t1.start();
        t2.start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
```

Пример вывода:

```
T1: думаю, что мешаю, переключаю флаг
T2: ресурс у первого, уступаю
T1: ресурс у второго, уступаю
T2: думаю, что мешаю, переключаю флаг
T2: ресурс у первого, уступаю
T1: думаю, что мешаю, переключаю флаг
T2: думаю, что мешаю, переключаю флаг
T1: ресурс у второго, уступаю
T2: ресурс у первого, уступаю
T1: думаю, что мешаю, переключаю флаг
T2: думаю, что мешаю, переключаю флаг
T1: ресурс у второго, уступаю
T2: ресурс у первого, уступаю
T1: думаю, что мешаю, переключаю флаг
T1: ресурс у второго, уступаю
T2: думаю, что мешаю, переключаю флаг
T1: думаю, что мешаю, переключаю флаг
T2: ресурс у первого, уступаю
```

Результат:
- Потоки постоянно проверяют состояние и переключают флаг.
- Они активно работают (sleep, вывод в консоль), но никогда не переходят к реальной работе — это и есть Livelock.

### Предотвращение livelock

```java
package com.utmn.kolmykova.starvation;

public class LivelockFixedDemo {

    static class Resource {
        private volatile boolean inUse;

        public synchronized boolean tryUse(String threadName, long timeoutMs) throws InterruptedException {
            long end = System.currentTimeMillis() + timeoutMs;
            while (inUse && System.currentTimeMillis() < end) {
                wait(50); // подождать немного
            }
            if (inUse) {
                System.out.println(threadName + ": не смог получить ресурс, выхожу");
                return false;
            }
            inUse = true;
            System.out.println(threadName + ": получил ресурс");
            return true;
        }

        public synchronized void release(String threadName) {
            inUse = false;
            System.out.println(threadName + ": освобождает ресурс");
            notifyAll();
        }
    }

    public static void main(String[] args) {
        Resource resource = new Resource();

        Runnable task = () -> {
            String name = Thread.currentThread().getName();
            try {
                if (resource.tryUse(name, 1000)) {
                    Thread.sleep(300);
                    resource.release(name);
                }
            } catch (InterruptedException ignored) {
            }
        };

        Thread t1 = new Thread(task, "T1");
        Thread t2 = new Thread(task, "T2");
        t1.start();
        t2.start();
    }
}
```

Результат:
- Поток ограничен по времени ожидания ресурса.
- Если ресурс не получен за timeoutMs, поток прекращает попытки → нет бесконечного «вежливого» цикла.

## Starvation

### StarvationDemo

```java
package com.utmn.kolmykova.starvation;

import java.util.LinkedList;
import java.util.Queue;

public class StarvationDemo {

    // Наша задача
    static class Task {
        private final String name;
        private final boolean important;

        public Task(String name, boolean important) {
            this.name = name;
            this.important = important;
        }

        public String getName() {
            return name;
        }

        public boolean isImportant() {
            return important;
        }

        public void run() {
            System.out.println("Выполняется задача: " + name +
                    " (важная=" + important + ")");
            // имитация работы
            busyWork();
        }
    }

    // Очередь задач со "справедливостью = false"
    static class UnfairTaskQueue {
        private final Queue<Task> importantTasks = new LinkedList<>();
        private final Queue<Task> normalTasks = new LinkedList<>();

        public synchronized void addTask(Task task) {
            if (task.isImportant()) {
                // важные задачи ставим в "быструю" очередь
                importantTasks.add(task);
            } else {
                // обычные в отдельную очередь
                normalTasks.add(task);
            }
            notifyAll();
        }

        public synchronized Task takeTask() throws InterruptedException {
            while (importantTasks.isEmpty() && normalTasks.isEmpty()) {
                wait();
            }

            // Несправедливое правило:
            // если есть важные задачи, выполняем ТОЛЬКО их,
            // до обычных не доходим.
            if (!importantTasks.isEmpty()) {
                return importantTasks.poll();
            }

            // До этого места поток доходит очень редко
            return normalTasks.poll();
        }
    }

    private static volatile boolean running = true;

    public static void main(String[] args) throws InterruptedException {
        UnfairTaskQueue queue = new UnfairTaskQueue();

        // Один рабочий поток
        Thread worker = new Thread(() -> {
            while (running) {
                try {
                    Task task = queue.takeTask();
                    if (task != null) {
                        task.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Worker");

        worker.start();

        Thread producerImportant = new Thread(() -> {
            int i = 0;
            while (running) {
                Task task = new Task("Important-" + i++, true);
                queue.addTask(task);
                if (i % 100 == 0) {
                    Task starvingTask = new Task("Starving-Normal-Task", false);
                    queue.addTask(starvingTask);
                }
            }
        }, "ImportantProducer");

        producerImportant.start();

        // Дадим системе поработать 5 секунд
        sleep(5000);
        running = false;

        worker.interrupt();
        producerImportant.interrupt();
    }

    private static void busyWork() {
        for (int i = 0; i < 300_000_000; i++) {
            Math.sqrt(i);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
```

Результат:
- Постоянно печатаются задачи вида Important-0, Important-1, Important-2, ...
- Задача Starving-Normal-Task либо не выполняется вообще, либо выполняется один раз очень поздно (если вы немного измените логику).

Это и есть Starvation:
- Рабочий поток существует, очередь существует.
- «Голодающая» задача уже добавлена в очередь.
- Но из‑за «несправедливого» правила выбора задач (всегда сначала важные) обычная задача почти никогда не получает процессорное время.

Почему это приводит к Starvation:
- обычная задача технически есть, но до неё очередь не доходит, потому что важные задачи постоянно подкидываются;
- происходит систематическое «голодание» одной задачи/типа задач.

Как предотвратить:изменить правило выборки: 
- например, после N важных задач обязательно брать одну обычную;
- или использовать справедливые структуры (типа одной очереди FIFO, без деления).


```java
package com.utmn.kolmykova.starvation;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StarvationFixedDemo {

    private static final Lock lock = new ReentrantLock(true); // fair = true
    private static volatile boolean running = true;

    public static void main(String[] args) {

        Runnable task = () -> {
            String name = Thread.currentThread().getName();
            while (running) {
                lock.lock();
                try {
                    System.out.println(name + " получил lock");
                    busyWork();
                } finally {
                    lock.unlock();
                }
            }
        };

        Thread t1 = new Thread(task, "T1");
        Thread t2 = new Thread(task, "T2");
        Thread t3 = new Thread(task, "T3");

        t1.start();
        t2.start();
        t3.start();

        sleep(3000);
        running = false;
    }

    private static void busyWork() {
        for (int i = 0; i < 500000; i++) {
            Math.sqrt(i);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
```

Результат:
- Не злоупотреблять экстремальными приоритетами потоков.
- Использовать Thread.yield() или sleep во «вечно работающих» потоках, давая другим шанс.