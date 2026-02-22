INSERT INTO vacancies (source, url, title, company, city, salary, requirements, published_at, created_at)
VALUES
    ('hh', 'http://localhost:8080/mock/vacancy/1',
     'Java Developer', 'Awesome Company', 'Moscow', '200000 RUB',
     'Java, Spring, SQL', '2024-10-01 10:00:00', CURRENT_TIMESTAMP),

    ('superjob', 'http://localhost:8080/mock/vacancy/2',
     'Middle Java Developer', 'SuperJob LLC', 'Saint Petersburg', '180000 RUB',
     'Java, Spring Boot, REST, Docker', '2024-10-02 11:30:00', CURRENT_TIMESTAMP),

    ('habr', 'http://localhost:8080/mock/vacancy/3',
     'Junior Java Developer', 'Startup Inc.', 'Moscow', '120000 RUB',
     'Java, Git, базовые знания SQL', '2024-10-03 09:15:00', CURRENT_TIMESTAMP);