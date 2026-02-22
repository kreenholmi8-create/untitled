package com.kolmykova.jobparser.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParseRequest {
    private List<String> urls;
}