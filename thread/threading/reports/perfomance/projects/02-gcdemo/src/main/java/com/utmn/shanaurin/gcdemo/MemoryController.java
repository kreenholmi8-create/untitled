package com.utmn.shanaurin.gcdemo;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final MemoryLeakService memoryLeakService;

    public MemoryController(MemoryLeakService memoryLeakService) {
        this.memoryLeakService = memoryLeakService;
    }

    // GET http://localhost:8080/memory/leak
    @GetMapping("/leak")
    public String createLeak() {
        return memoryLeakService.createLeak();
    }

    // GET http://localhost:8080/memory/leak/bulk?count=50
    @GetMapping("/leak/bulk")
    public String createBulkLeak(@RequestParam(defaultValue = "10") int count) {
        return memoryLeakService.createBulkLeak(count);
    }

    // GET http://localhost:8080/memory/stats
    @GetMapping("/stats")
    public String getStats() {
        return memoryLeakService.getMemoryStats();
    }

    // GET http://localhost:8080/memory/clear
    @GetMapping("/clear")
    public String clearLeaks() {
        return memoryLeakService.clearLeaks();
    }
}