package com.kolmykova.jobparser.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "vacancies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;
    private String url;
    private String title;
    private String company;
    private String city;
    private String salary;

    @Column(length = 2000)
    private String requirements;

    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}