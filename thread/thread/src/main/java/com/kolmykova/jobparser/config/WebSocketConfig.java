package com.kolmykova.jobparser.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.kolmykova.jobparser.websocket.ParsingWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ParsingWebSocketHandler parsingWebSocketHandler;

    public WebSocketConfig(ParsingWebSocketHandler parsingWebSocketHandler) {
        this.parsingWebSocketHandler = parsingWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(parsingWebSocketHandler, "/ws/parsing")
                .setAllowedOrigins("*");
    }
}