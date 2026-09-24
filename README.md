# E-Commerce Analytics Platform

A distributed e-commerce analytics platform built with Java and Spring Boot for managing e-commerce data, processing real-time events, and performing large-scale data analytics.

The platform combines REST APIs, PostgreSQL, Apache Kafka, Apache Spark, and Elasticsearch to support transactional operations, event streaming, data processing, and analytics.

## Tech Stack

* Java 21
* Spring Boot
* Spring Data JPA / Hibernate
* PostgreSQL
* Apache Kafka
* Apache Spark
* Elasticsearch
* JUnit / Mockito
* Maven
* Swagger / OpenAPI

## Architecture

```text
                              ┌──────────────────────┐
                              │        Client        │
                              │  REST / Swagger UI   │
                              └──────────┬───────────┘
                                         │
                                         ▼
                              ┌──────────────────────┐
                              │     Spring Boot      │
                              │       Backend        │
                              │                      │
                              │ Controllers          │
                              │      ↓               │
                              │ Services             │
                              │      ↓               │
                              │ Repositories         │
                              └───────┬───────┬──────┘
                                      │       │
                         Transaction │       │ Event
                                      │       │
                                      ▼       ▼
                              ┌───────────┐  ┌──────────────┐
                              │ PostgreSQL│  │    Kafka     │
                              │           │  │              │
                              │ E-Commerce│  │   Producer   │
                              │   Data    │  │      ↓       │
                              └───────────┘  │    Topics    │
                                            │      ↓       │
                                            │   Consumer   │
                                            └──────┬───────┘
                                                   │
                                                   ▼
                                        ┌──────────────────┐
                                        │   Apache Spark   │
                                        │                  │
                                        │ Data Processing  │
                                        │   & Analytics    │
                                        └────────┬─────────┘
                                                 │
                                                 ▼
                                        ┌──────────────────┐
                                        │  Elasticsearch   │
                                        │                  │
                                        │ Analytics Data   │
                                        │ & Search         │
                                        └────────┬─────────┘
                                                 │
                                                 ▼
                                        ┌──────────────────┐
                                        │ Analytics APIs / │
                                        │     Results      │
                                        └──────────────────┘
```

## Features

* RESTful APIs for e-commerce operations
* Complete CRUD functionality
* PostgreSQL data persistence
* Apache Kafka producer and consumer
* Event-driven data processing
* Apache Spark data processing and analytics
* Elasticsearch indexing and search
* Automated data generation
* JUnit and Mockito testing
* Swagger/OpenAPI documentation
* DTO-based API design
* Centralized exception handling

## Data Flow

Transactional operations are handled by the Spring Boot application and persisted in PostgreSQL.

Application events are published to Kafka, where they are consumed and processed by the analytics pipeline.

```text
Application
    │
    ├──────────────► PostgreSQL
    │
    └──────────────► Kafka
                       │
                       ▼
                     Spark
                       │
                       ▼
                Elasticsearch
                       │
                       ▼
                  Analytics
```

This separates transactional data management from large-scale analytics processing while allowing application events to be processed asynchronously.

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/ecommerce/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       ├── entity/
│   │       ├── dto/
│   │       ├── exception/
│   │       ├── config/
│   │       ├── kafka/
│   │       └── ...
│   │
│   └── resources/
│       └── application.properties
│
└── test/
    └── java/
```

## Getting Started

### Prerequisites

* Java 21
* Maven
* PostgreSQL
* Apache Kafka
* Apache Spark
* Elasticsearch

### Configuration

Configure the application services in:

```text
src/main/resources/application.properties
```

The configuration includes the required database, Kafka, Spark, and Elasticsearch connection settings.

### Run the Application

```bash
mvn clean install
mvn spring-boot:run
```

## API Documentation

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI specification:

```text
http://localhost:8080/v3/api-docs
```

## Testing

Run the test suite with:

```bash
mvn test
```

The project uses JUnit and Mockito to test application and service-layer functionality.

## Data Generation

The project includes a data generation component for creating e-commerce data and events used throughout the application and analytics pipeline.

Generated data can be used to populate the database and produce Kafka events for processing through Spark and Elasticsearch.

## Analytics Pipeline

The analytics pipeline processes events from Kafka using Apache Spark and stores processed data in Elasticsearch.

```text
Kafka
  │
  ▼
Apache Spark
  │
  ├── Transform
  ├── Aggregate
  └── Process
        │
        ▼
Elasticsearch
        │
        ▼
Analytics & Search
```

This pipeline supports processing large volumes of e-commerce events and making the resulting data available for analytical queries and search.

## License

This project is developed for educational and development purposes.
