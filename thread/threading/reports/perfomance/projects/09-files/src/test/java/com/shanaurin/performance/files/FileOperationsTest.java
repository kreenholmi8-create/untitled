package com.shanaurin.performance.files;

import com.shanaurin.performance.files.model.CurrencyRate;
import com.shanaurin.performance.files.service.DataGeneratorService;
import com.shanaurin.performance.files.service.FileChannelService;
import com.shanaurin.performance.files.service.MemoryMappedFileService;
import com.shanaurin.performance.files.service.RandomAccessFileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileOperationsTest {

    @Autowired
    private DataGeneratorService dataGenerator;

    @Autowired
    private RandomAccessFileService randomAccessFileService;

    @Autowired
    private FileChannelService fileChannelService;

    @Autowired
    private MemoryMappedFileService memoryMappedFileService;

    @Test
    void testDataGeneration() {
        List<CurrencyRate> data = dataGenerator.generateData(100);
        assertNotNull(data);
        assertEquals(100, data.size());
        assertNotNull(data.get(0).getCurrencyPair());
        assertTrue(data.get(0).getRate() > 0);
    }

    @Test
    void testRandomAccessFileOperations() {
        List<CurrencyRate> data = dataGenerator.generateData(1000);
        var writeMetrics = randomAccessFileService.writeData(data);
        assertNotNull(writeMetrics);
        assertTrue(writeMetrics.getDurationMillis() > 0);

        var readMetrics = randomAccessFileService.readDataSequential(1000);
        assertNotNull(readMetrics);
        assertTrue(readMetrics.getDurationMillis() > 0);
    }

    @Test
    void testFileChannelOperations() {
        List<CurrencyRate> data = dataGenerator.generateData(1000);
        var writeMetrics = fileChannelService.writeData(data);
        assertNotNull(writeMetrics);
        assertTrue(writeMetrics.getDurationMillis() >= 0);

        var readMetrics = fileChannelService.readDataSequential(1000);
        assertNotNull(readMetrics);
        assertTrue(readMetrics.getDurationMillis() >= 0);
    }

    @Test
    void testMemoryMappedFileOperations() {
        List<CurrencyRate> data = dataGenerator.generateData(1000);
        var writeMetrics = memoryMappedFileService.writeData(data);
        assertNotNull(writeMetrics);
        assertTrue(writeMetrics.getDurationMillis() >= 0);

        var readMetrics = memoryMappedFileService.readDataSequential(1000);
        assertNotNull(readMetrics);
        assertTrue(readMetrics.getDurationMillis() >= 0);
    }
}
