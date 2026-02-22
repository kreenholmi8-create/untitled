package com.utmn.shanaurin.gcdemo;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryLeakService {

    // Статическая коллекция — классическая причина утечки памяти
    // Объекты никогда не удаляются, GC не может их собрать
    private static final List<byte[]> leakyStorage = new ArrayList<>();

    // Счётчик для отслеживания количества добавленных объектов
    private static int objectCount = 0;

    /**
     * Добавляет объект в статическую коллекцию.
     * Каждый вызов создаёт массив ~1 MB, который никогда не освобождается.
     */
    public String createLeak() {
        // Создаём массив примерно на 1 MB
        byte[] leak = new byte[1024 * 1024];
        leakyStorage.add(leak);
        objectCount++;

        return String.format("Добавлен объект #%d. Всего в памяти: %d MB",
                objectCount, objectCount);
    }

    /**
     * Массовое создание утечек для быстрого заполнения heap.
     */
    public String createBulkLeak(int count) {
        for (int i = 0; i < count; i++) {
            byte[] leak = new byte[1024 * 1024];
            leakyStorage.add(leak);
            objectCount++;
        }
        return String.format("Добавлено %d объектов. Всего в памяти: %d MB",
                count, objectCount);
    }

    /**
     * Возвращает текущую статистику памяти.
     */
    public String getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        return String.format(
                "Объектов в leakyStorage: %d\n" +
                        "Используемая память: %d MB\n" +
                        "Свободная память: %d MB\n" +
                        "Всего выделено: %d MB\n" +
                        "Максимум heap: %d MB",
                objectCount, usedMemory, freeMemory, totalMemory, maxMemory
        );
    }

    /**
     * Очистка коллекции (для демонстрации, что после очистки память освобождается).
     */
    public String clearLeaks() {
        int cleared = objectCount;
        leakyStorage.clear();
        objectCount = 0;
        System.gc(); // Подсказка JVM запустить GC
        return String.format("Очищено %d объектов. Запрошена сборка мусора.", cleared);
    }
}