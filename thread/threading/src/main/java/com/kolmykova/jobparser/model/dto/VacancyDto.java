package com.kolmykova.jobparser.model.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VacancyDto {
    private Long id;
    private String source;
    private String url;
    private String title;
    private String company;
    private String city;
    private String salary;
    private String requirements;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
