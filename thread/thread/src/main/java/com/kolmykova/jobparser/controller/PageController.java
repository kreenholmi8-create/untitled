// src/main/java/com/kolmykova/jobparser/controller/PageController.java
package com.kolmykova.jobparser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/comparison")
    public String comparison() {
        return "comparison";
    }
}