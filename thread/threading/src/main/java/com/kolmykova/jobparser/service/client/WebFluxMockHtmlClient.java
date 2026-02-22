package com.kolmykova.jobparser.service.client;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class WebFluxMockHtmlClient {

    private final WebClient webClient;

    public WebFluxMockHtmlClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public String fetchHtml(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block(); // для примера, блокируем
    }
}