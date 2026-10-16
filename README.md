# AI Travel Companion – Backend API

A location-aware travel assistant API that will help international travelers discover nearby attractions and activities based on location, interests, available time, and budget.

This repository currently contains only the initial application skeleton (Task 1 of the backlog): Spring Boot setup, configuration, and a health check. Business features will be added in later stories.

## Tech Stack

- Java 21
- Spring Boot 3.5.16
- Maven (with Maven Wrapper)
- Spring Web MVC, Spring Boot Validation, Spring Boot Actuator
- JUnit 5 and Spring Boot Test
- Embedded Tomcat

## Prerequisites

- JDK 21 (any vendor). Verify with `java -version`.
- No Maven installation is required; the included wrapper (`mvnw`) downloads Maven 3.9.9 on first use.

## Project Structure

```
travel-companion-api/
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw / mvnw.cmd
├── pom.xml
└── src
    ├── main
    │   ├── java/com/aitravel/companion
    │   │   ├── TravelCompanionApplication.java   # Spring Boot entry point
    │   │   ├── config/        # Spring configuration classes (future)
    │   │   ├── controller/    # REST controllers (future)
    │   │   ├── dto/           # Request/response objects (future)
    │   │   ├── exception/     # Error handling (future)
    │   │   ├── model/         # Domain model (future)
    │   │   ├── provider/      # External providers, e.g. Places/AI (future)
    │   │   ├── repository/    # Persistence (future)
    │   │   └── service/       # Business logic (future)
    │   └── resources
    │       └── application.yml
    └── test
        └── java/com/aitravel/companion
            └── TravelCompanionApplicationTests.java
```

The empty packages contain `.gitkeep` placeholders so Git tracks them.

## Configuration

Configuration lives in `src/main/resources/application.yml`.

| Setting | Default | Environment variable |
|---|---|---|
| Server port | `8080` | `SERVER_PORT` |

Actuator exposes only `health`, with details hidden. Secrets and API keys must never be committed. Future secrets will be supplied through environment variables.

## Build and Test

```bash
./mvnw clean verify          # macOS/Linux
mvnw.cmd clean verify        # Windows
```

## Run Locally

```bash
./mvnw spring-boot:run

# or build and run the JAR
./mvnw clean package
java -jar target/travel-companion-api-0.0.1-SNAPSHOT.jar

# optional: run on another port
SERVER_PORT=9090 ./mvnw spring-boot:run
```

## Verify the Health Endpoint

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Other actuator endpoints (for example `/actuator/env`) return 404 by design.

## Git Notes

`target/`, IDE files (`.idea/`, `*.iml`), and local secret files (`.env`, `application-local.yml`) are ignored via `.gitignore`.
