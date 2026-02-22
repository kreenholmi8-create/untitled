# Краткий отчёт о выполнении домашнего задания 2

## Что вы увидите в консоли:

### До старта:
- все потоки в состоянии NEW.

### После старта:
- T1-WAITING быстро перейдет в RUNNABLE, затем войдет в WAITING на wait(), затем после notify() завершится (TERMINATED).
- T2-TIMED_WAITING будет циклически переходить в TIMED_WAITING на sleep и на join(timeout), затем завершится.
- T3-HOLDER удерживает монитор blockLock;
- в это время T3-BLOCKED при входе в synchronized попадет в BLOCKED. После освобождения — RUNNABLE/TERMINATED.
- Монитор каждые ~1 сек печатает текущие состояния, что позволяет наблюдать эволюцию состояний: NEW → RUNNABLE → WAITING/TIMED_WAITING/BLOCKED → RUNNABLE → TERMINATED.

## Короткий отчет (структура для прикрепления ссылкой):

### Цель:
Продемонстрировать основные состояния потоков Java (NEW, RUNNABLE, WAITING, TIMED_WAITING, BLOCKED, TERMINATED) в Spring Boot-приложении.

### Инструменты: 
- Java 21, 
- Spring Boot 3.x, 
- стандартные средства потоков (wait/notify, synchronized, sleep, join).
### Описание потоков:
- T1-WAITING: выполняет короткую работу, переходит в WAITING на wait(), пробуждается через notify(), завершается.
- T2-TIMED_WAITING: несколько раз спит (sleep), использует join(timeout), завершается.
- T3-HOLDER/T3-BLOCKED: HOLDER удерживает монитор, BLOCKED пытается войти в критическую секцию и переходит в BLOCKED, затем завершается.
- Мониторинг: отдельный мониторинговый поток печатает состояния всех потоков раз в секунду до их завершения.

### Результат: на консоли наблюдается изменение состояний, соответствующее чек-листу.

## Вывод в консоли

```bash
Запуск демонстрации состояний потоков...
---- Перед стартом ----
T1-WAITING     | state=NEW           | alive=false | daemon=false | id=34
T2-TIMED_WAITING | state=NEW           | alive=false | daemon=false | id=35
T3-HOLDER      | state=NEW           | alive=false | daemon=false | id=36
T3-BLOCKED     | state=NEW           | alive=false | daemon=false | id=37
[T1-WAITING] T1: старт, выполню небольшую работу перед wait()
[T2-TIMED_WAITING] T2: старт, начну несколько циклов sleep()
[T3-HOLDER] T3-HOLDER: захватил blockLock и удерживаю 3 сек
---- Мониторинг ----
T1-WAITING     | state=RUNNABLE      | alive=true  | daemon=false | id=34
T2-TIMED_WAITING | state=TIMED_WAITING | alive=true  | daemon=false | id=35
T3-HOLDER      | state=TIMED_WAITING | alive=true  | daemon=false | id=36
T3-BLOCKED     | state=TIMED_WAITING | alive=true  | daemon=false | id=37
[T1-WAITING] T1: перехожу в WAITING с помощью wait()
[T3-BLOCKED] T3-BLOCKED: пытаюсь войти в synchronized(blockLock) -> ожидаем BLOCKED
[T2-TIMED_WAITING] T2: проснулся от sleep 1
---- Мониторинг ----
T1-WAITING     | state=WAITING       | alive=true  | daemon=false | id=34
T2-TIMED_WAITING | state=TIMED_WAITING | alive=true  | daemon=false | id=35
T3-HOLDER      | state=TIMED_WAITING | alive=true  | daemon=false | id=36
T3-BLOCKED     | state=BLOCKED       | alive=true  | daemon=false | id=37
[T2-TIMED_WAITING] T2: проснулся от sleep 2
---- Мониторинг ----
T1-WAITING     | state=WAITING       | alive=true  | daemon=false | id=34
T2-TIMED_WAITING | state=TIMED_WAITING | alive=true  | daemon=false | id=35
T3-HOLDER      | state=TIMED_WAITING | alive=true  | daemon=false | id=36
T3-BLOCKED     | state=BLOCKED       | alive=true  | daemon=false | id=37
[T2-TIMED_WAITING] T2: проснулся от sleep 3
[T2-TIMED_WAITING] T2: завершение после join(timeout)
[Notifier] NOTIFIER: вызываю notify() для T1
[T1-WAITING] T1: проснулся после notify, завершаюсь
[T3-HOLDER] T3-HOLDER: отпускаю blockLock
[T3-BLOCKED] T3-BLOCKED: получил lock после освобождения, завершаюсь
---- После завершения ----
T1-WAITING     | state=TERMINATED    | alive=false | daemon=false | id=34
T2-TIMED_WAITING | state=TERMINATED    | alive=false | daemon=false | id=35
T3-HOLDER      | state=TERMINATED    | alive=false | daemon=false | id=36
T3-BLOCKED     | state=TERMINATED    | alive=false | daemon=false | id=37
[main] Демонстрация завершена.
```

## Исходный код ThreadstatesApplication.java

```java
package com.shanaurin.threadstates;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.CommandLineRunner;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
public class ThreadstatesApplication implements CommandLineRunner {

	private final Object waitLock = new Object();
	private final Object blockLock = new Object();
	private final AtomicBoolean stopMonitor = new AtomicBoolean(false);

	public static void main(String[] args) {
		SpringApplication.run(ThreadstatesApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Запуск демонстрации состояний потоков...");

		// Поток 1: демонстрирует WAITING (wait/notify), RUNNABLE, TERMINATED
		Thread waitingThread = new Thread(() -> {
			log("T1: старт, выполню небольшую работу перед wait()");
			busyWork(300); // имитация работы
			synchronized (waitLock) {
				try {
					log("T1: перехожу в WAITING с помощью wait()");
					waitLock.wait(); // -> WAITING до notify
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					log("T1: прерван");
				}
			}
			log("T1: проснулся после notify, завершаюсь");
		}, "T1-WAITING");

		// Поток 2: демонстрирует TIMED_WAITING (sleep/join с таймаутом), RUNNABLE, TERMINATED
		Thread timedWaitingThread = new Thread(() -> {
			try {
				log("T2: старт, начну несколько циклов sleep()");
				for (int i = 0; i < 3; i++) {
					Thread.sleep(700); // -> TIMED_WAITING
					log("T2: проснулся от sleep " + (i + 1));
				}
				// Дополнительно join с таймаутом на уже завершённый поток-заглушку
				Thread dummy = new Thread(() -> {});
				dummy.start();
				dummy.join(500); // -> TIMED_WAITING на время join
				log("T2: завершение после join(timeout)");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log("T2: прерван");
			}
		}, "T2-TIMED_WAITING");

		// Поток 3: демонстрирует BLOCKED: сначала поток-держатель удерживает lock долго,
		// затем поток-блокируемый пытается войти в синхр. блок и попадает в BLOCKED
		Thread lockHolder = new Thread(() -> {
			synchronized (blockLock) {
				log("T3-HOLDER: захватил blockLock и удерживаю 3 сек");
				sleepQuiet(3000); // держим монитор
				log("T3-HOLDER: отпускаю blockLock");
			}
		}, "T3-HOLDER");

		Thread blockedThread = new Thread(() -> {
			sleepQuiet(300); // даем HOLDER захватить lock сначала
			log("T3-BLOCKED: пытаюсь войти в synchronized(blockLock) -> ожидаем BLOCKED");
			synchronized (blockLock) { // пока HOLDER держит — это состояние BLOCKED
				log("T3-BLOCKED: получил lock после освобождения, завершаюсь");
			}
		}, "T3-BLOCKED");

		// Мониторинг состояний: печатаем каждые 1 сек состояние каждого потока,
		// пока все не завершатся или не истечёт таймаут
		List<Thread> threads = List.of(waitingThread, timedWaitingThread, lockHolder, blockedThread);
		Thread monitor = new Thread(() -> monitorStates(threads), "Monitor");

		// Печатаем состояние до старта (должно быть NEW)
		printStates("Перед стартом", threads);

		// Стартуем
		waitingThread.start();
		timedWaitingThread.start();
		lockHolder.start();
		blockedThread.start();

		// Запускаем монитор
		monitor.start();

		// Через 2.5 сек разбудим waitingThread
		new Thread(() -> {
			sleepQuiet(2500);
			synchronized (waitLock) {
				log("NOTIFIER: вызываю notify() для T1");
				waitLock.notify();
			}
		}, "Notifier").start();

		// Ждем завершения основных потоков
		for (Thread t : threads) {
			t.join();
		}

		// Останавливаем монитор
		stopMonitor.set(true);
		monitor.join();

		printStates("После завершения", threads); // должны быть TERMINATED
		log("Демонстрация завершена.");
	}

	private void monitorStates(List<Thread> threads) {
		long start = System.currentTimeMillis();
		long timeoutMs = 15000; // безопасный таймаут для демонстрации
		while (!stopMonitor.get()) {
			printStates("Мониторинг", threads);
			boolean allTerminated = threads.stream().allMatch(t -> t.getState() == Thread.State.TERMINATED);
			if (allTerminated) break;
			if (System.currentTimeMillis() - start > timeoutMs) {
				log("Monitor: таймаут, прекращаю мониторинг");
				break;
			}
			sleepQuiet(1000);
		}
	}

	private static void printStates(String title, List<Thread> threads) {
		System.out.println("---- " + title + " ----");
		for (Thread t : threads) {
			System.out.printf("%-14s | state=%-13s | alive=%-5s | daemon=%-5s | id=%d%n",
					t.getName(), t.getState(), t.isAlive(), t.isDaemon(), t.threadId());
		}
	}

	private static void busyWork(long ms) {
		long end = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < end) {
			double x = Math.sqrt(Math.random());
		}
	}

	private static void sleepQuiet(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	private static void log(String msg) {
		System.out.printf("[%s] %s%n", Thread.currentThread().getName(), msg);
	}
}
```