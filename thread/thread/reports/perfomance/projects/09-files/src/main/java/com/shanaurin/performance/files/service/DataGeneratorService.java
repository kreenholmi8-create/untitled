package com.shanaurin.performance.files.service;

import com.shanaurin.performance.files.model.CurrencyRate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class DataGeneratorService {

    private static final String[] CURRENCY_PAIRS = {
            "USD/RUB", "EUR/RUB", "GBP/RUB", "JPY/RUB", "CHF/RUB",
            "CNY/RUB", "AUD/RUB", "CAD/RUB", "EUR/USD", "GBP/USD"
    };

    private final Random random = new Random(42); // Фиксированный seed для воспроизводимости

    public List<CurrencyRate> generateData(int count) {
        log.info("Генерация {} записей о курсах валют", count);
        List<CurrencyRate> data = new ArrayList<>(count);
        LocalDateTime baseTime = LocalDateTime.now().minusDays(30);

        for (long i = 0; i < count; i++) {
            CurrencyRate rate = new CurrencyRate(
                    i,
                    CURRENCY_PAIRS[random.nextInt(CURRENCY_PAIRS.length)],
                    50.0 + random.nextDouble() * 50.0, // курс от 50 до 100
                    1000.0 + random.nextDouble() * 100000.0, // объем от 1000 до 101000
                    baseTime.plusMinutes(i)
            );
            data.add(rate);
        }

        log.info("Генерация завершена");
        return data;
    }
}
