package com.shanaurin.performance.files.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRate implements Serializable {
    private long id;
    private String currencyPair; // например, "USD/RUB"
    private double rate;
    private double volume;
    private LocalDateTime timestamp;

    // Размер одной записи в байтах для бинарного формата
    // long(8) + String(20) + double(8) + double(8) + timestamp(8) = ~52 байта + накладные расходы
    public static final int BINARY_SIZE = 64;

    public String toCSV() {
        return String.format("%d,%s,%.6f,%.2f,%s",
                id, currencyPair, rate, volume, timestamp.toString());
    }

    public static CurrencyRate fromCSV(String csv) {
        String[] parts = csv.split(",");
        return new CurrencyRate(
                Long.parseLong(parts[0]),
                parts[1],
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                LocalDateTime.parse(parts[4])
        );
    }
}