# 08 — Scalability & Production-Readiness Guide

> How to evolve the FHIR Converter MVP into a hospital-grade, production-ready system.

---

## Table of Contents

1. [Current Architecture (MVP)](#1-current-architecture-mvp)
2. [Message Ingestion at Scale](#2-message-ingestion-at-scale)
3. [Dead Letter Queue — Production Upgrade](#3-dead-letter-queue--production-upgrade)
4. [Database Scalability](#4-database-scalability)
5. [Security & Access Control](#5-security--access-control)
6. [Observability — Logging, Metrics, Alerts](#6-observability--logging-metrics-alerts)
7. [Horizontal Scaling & Containerisation](#7-horizontal-scaling--containerisation)
8. [CI/CD Pipeline](#8-cicd-pipeline)
9. [FHIR Compliance & Terminology Binding](#9-fhir-compliance--terminology-binding)
10. [Phased Roadmap](#10-phased-roadmap)

---

## 1. Current Architecture (MVP)

```
┌───────────────┐       HTTP POST        ┌──────────────────────┐       MySQL
│  Browser UI   │ ────────────────────►   │  Spring Boot App     │ ──────────►  conversion_records
│  (Dashboard)  │ ◄────────────────────   │  (Single Instance)   │ ──────────►  error_logs (DLQ)
└───────────────┘       JSON Response     └──────────────────────┘
```

**What works today:**

| Layer             | Current State                                       |
|-------------------|-----------------------------------------------------|
| Input             | Manual paste via web UI (HL7, JSON, CSV)            |
| Processing        | Single-threaded, synchronous conversion             |
| Storage           | Single MySQL instance, JPA/Hibernate                |
| Error Handling    | Errors saved to `error_logs` table (Dead Letter Queue)|
| Security          | None — open access                                  |
| Deployment        | `mvn spring-boot:run` on developer machine          |
| Monitoring        | `System.out.println` console logs                   |

**This works for development and demos. Below is the roadmap to production.**

---

## 2. Message Ingestion at Scale

### Problem
Hospitals don't paste messages into a web UI. Real systems generate **thousands of HL7 messages per hour** from EHR/HIS systems over TCP/IP using the MLLP (Minimal Lower Layer Protocol).

### Solution — Two Approaches

#### Option A: MLLP Listener (Traditional)

Add a TCP listener that speaks the HL7 MLLP protocol directly.

**Technology:** [HAPI HL7v2](https://hapifhir.github.io/hapi-hl7v2/) — already on our classpath.

```java
// Example: MLLP Listener using HAPI
HapiContext context = new DefaultHapiContext();
HL7Service server = context.newServer(2575, false); // port 2575
server.registerApplication("ADT", "A01", new AdtA01Handler(conversionService));
server.startAndWait();
```

**When to use:** Direct integration with a single hospital HIS system.

#### Option B: Message Broker (Enterprise Scale)

Place a **message broker** between the hospital systems and our converter.

```
┌──────────┐     MLLP/TCP     ┌──────────────┐     ┌──────────────────────┐
│  HIS/EHR │ ───────────────► │  Mirth Connect│ ──► │  Apache Kafka /      │
│  Systems │                  │  (Interface   │     │  RabbitMQ            │
└──────────┘                  │   Engine)     │     └──────────┬───────────┘
                              └──────────────┘                │
                                                              │ Consumer
                                                              ▼
                                               ┌──────────────────────┐
                                               │  FHIR Converter      │
                                               │  (Multiple Instances)│
                                               └──────────────────────┘
```

**Dependencies to add (`pom.xml`):**

```xml
<!-- For Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- For RabbitMQ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**Kafka Consumer Example:**

```java
@Service
public class Hl7KafkaConsumer {

    private final ConversionService conversionService;

    public Hl7KafkaConsumer(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @KafkaListener(topics = "hl7-inbound", groupId = "fhir-converter")
    public void consume(String rawHl7) {
        try {
            conversionService.convertCoverage(rawHl7);
        } catch (Exception e) {
            // Already saved to DLQ by ConversionService
            log.error("Failed to process message from Kafka", e);
        }
    }
}
```

**When to use:** Multi-hospital deployments, high throughput (>100 messages/sec), need for replay.

**Recommendation:** Start with **Option A** for a single hospital. Move to **Option B** when integrating multiple facilities.

---

## 3. Dead Letter Queue — Production Upgrade

### Current State
Failed messages are saved to the `error_logs` MySQL table. Hospital staff can view them in the DLQ tab of the dashboard.

### Production Enhancements

#### 3.1 — Retry Mechanism

Add a **scheduled job** that periodically re-processes failed messages:

```java
@Service
public class DlqRetryService {

    private final ErrorLogRepository errorLogRepository;
    private final ConversionService conversionService;

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    public void retryFailedMessages() {
        List<ErrorLog> failedMessages = errorLogRepository
            .findByRetryCountLessThan(3); // max 3 retries

        for (ErrorLog error : failedMessages) {
            try {
                conversionService.convertCoverage(error.getRawMessage());
                errorLogRepository.delete(error); // remove from DLQ on success
            } catch (Exception e) {
                error.setRetryCount(error.getRetryCount() + 1);
                error.setLastRetryAt(LocalDateTime.now());
                errorLogRepository.save(error);
            }
        }
    }
}
```

**Database Migration Required:**

```sql
ALTER TABLE error_logs ADD COLUMN retry_count INT DEFAULT 0;
ALTER TABLE error_logs ADD COLUMN last_retry_at DATETIME NULL;
ALTER TABLE error_logs ADD COLUMN resolved BOOLEAN DEFAULT FALSE;
```

#### 3.2 — DLQ Alerting

Send email/SMS/Slack notifications when the DLQ exceeds a threshold:

```java
@Scheduled(fixedDelay = 60000) // every minute
public void checkDlqThreshold() {
    long unresolvedCount = errorLogRepository.countByResolvedFalse();
    if (unresolvedCount > 50) {
        notificationService.sendAlert(
            "DLQ Alert: " + unresolvedCount + " unresolved errors"
        );
    }
}
```

#### 3.3 — Manual Retry from UI

Add a "Retry" button per DLQ row in the dashboard so hospital staff can manually trigger reprocessing after fixing the data in the source system.

---

## 4. Database Scalability

### Current State
Single MySQL 8.0 instance with two tables (`conversion_records`, `error_logs`).

### Production Enhancements

| Enhancement              | What to Do                                                | When Needed             |
|--------------------------|-----------------------------------------------------------|-------------------------|
| **Connection Pooling**   | HikariCP is already configured (Spring Boot default). Tune `maximumPoolSize` to 20–50. | Day 1                  |
| **Indexing**             | Add indexes on `created_at`, `status`, `hl7_hash`.       | >10,000 records         |
| **Archival**             | Move records older than 90 days to an archive table.      | >100,000 records        |
| **Read Replicas**        | Set up MySQL replication for dashboard reads.             | High dashboard traffic  |
| **Database per Service** | If splitting into microservices, each service owns its DB.| Microservice migration  |

**Index Migration:**

```sql
CREATE INDEX idx_conversion_status ON conversion_records(status);
CREATE INDEX idx_conversion_created ON conversion_records(created_at);
CREATE INDEX idx_errorlog_created ON error_logs(created_at);
CREATE INDEX idx_errorlog_resolved ON error_logs(resolved);
```

### Flyway for Schema Migrations

Replace `spring.jpa.hibernate.ddl-auto=update` with Flyway for versioned, tracked migrations:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

Place migration scripts in `src/main/resources/db/migration/`:
```
V1__create_conversion_records.sql
V2__create_error_logs.sql
V3__add_retry_columns_to_error_logs.sql
```

---

## 5. Security & Access Control

### What to Implement

| Feature                   | Technology                        | Priority  |
|---------------------------|-----------------------------------|-----------|
| **Authentication**        | Spring Security + JWT tokens     | Critical  |
| **Role-Based Access**     | `ADMIN`, `OPERATOR`, `VIEWER`    | Critical  |
| **API Rate Limiting**     | Bucket4j or Spring Cloud Gateway | High      |
| **HTTPS/TLS**             | SSL certificate on Tomcat/Nginx  | Critical  |
| **Audit Logging**         | Log who did what, when           | High      |
| **Data Encryption**       | Encrypt PII at rest (AES-256)    | Critical  |

### Spring Security Configuration (Example)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/convert/**").hasRole("OPERATOR")
                .requestMatchers("/api/convert/errors").hasRole("ADMIN")
                .requestMatchers("/api/convert/history").hasAnyRole("ADMIN", "VIEWER")
                .requestMatchers("/", "/index.html", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

### Role Definitions

| Role       | Permissions                                    |
|------------|------------------------------------------------|
| `ADMIN`    | View DLQ, retry errors, manage users, full API |
| `OPERATOR` | Run conversions, view history                  |
| `VIEWER`   | View history and dashboard (read-only)         |

---

## 6. Observability — Logging, Metrics, Alerts

### Replace `System.out.println` with SLF4J

```java
// Before (current)
System.out.println("📋 Step 1: Parsing HL7 message...");

// After (production)
private static final Logger log = LoggerFactory.getLogger(ConversionService.class);
log.info("Parsing HL7 message, hash={}", inputHash);
log.error("Conversion failed for hash={}", inputHash, e);
```

### Spring Boot Actuator (Health & Metrics)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, info, prometheus
  endpoint:
    health:
      show-details: always
```

### Metrics Stack

```
┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│ Spring Boot  │  /prometheus  │  Prometheus │  query  │   Grafana    │
│ Actuator     │ ────────────► │  (Scraper)  │ ──────► │ (Dashboard)  │
└──────────────┘        └─────────────┘        └──────────────┘
```

**Key Metrics to Track:**

| Metric                              | Purpose                          |
|-------------------------------------|----------------------------------|
| `fhir.conversions.total`            | Total conversion attempts        |
| `fhir.conversions.success`          | Successful conversions           |
| `fhir.conversions.failed`           | Failed conversions               |
| `fhir.dlq.size`                     | Current unresolved DLQ entries   |
| `fhir.conversion.duration.seconds`  | Time per conversion              |

---

## 7. Horizontal Scaling & Containerisation

### Dockerization

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose (Full Stack)

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpass
      MYSQL_DATABASE: fhir_converter
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  fhir-converter:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/fhir_converter
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: rootpass
    depends_on:
      - mysql

  # Optional: Message broker for scale
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"

volumes:
  mysql_data:
```

### Kubernetes Deployment (Enterprise)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fhir-converter
spec:
  replicas: 3       # 3 instances for high availability
  selector:
    matchLabels:
      app: fhir-converter
  template:
    metadata:
      labels:
        app: fhir-converter
    spec:
      containers:
        - name: fhir-converter
          image: your-registry/fhir-converter:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 10
```

### Load Balancing

```
                    ┌─────────────────────┐
                    │   Nginx / AWS ALB   │
                    │   (Load Balancer)   │
                    └────────┬────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
     │  Instance 1  │ │  Instance 2  │ │  Instance 3  │
     │  (Pod)       │ │  (Pod)       │ │  (Pod)       │
     └──────────────┘ └──────────────┘ └──────────────┘
              │              │              │
              └──────────────┼──────────────┘
                             ▼
                    ┌──────────────────┐
                    │  MySQL (RDS /    │
                    │  Managed DB)     │
                    └──────────────────┘
```

---

## 8. CI/CD Pipeline

### GitHub Actions Example

```yaml
name: FHIR Converter CI/CD

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build & Test
        run: mvn clean verify

      - name: Build Docker Image
        run: docker build -t fhir-converter:${{ github.sha }} .

      - name: Push to Registry
        run: |
          docker tag fhir-converter:${{ github.sha }} your-registry/fhir-converter:latest
          docker push your-registry/fhir-converter:latest

  deploy:
    needs: build-and-test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: kubectl set image deployment/fhir-converter fhir-converter=your-registry/fhir-converter:${{ github.sha }}
```

---

## 9. FHIR Compliance & Terminology Binding

### Current Gap
Our FHIR output is structurally valid but uses simple codes. Real NHCX compliance requires:

| Requirement                   | Current State        | Production Target               |
|-------------------------------|----------------------|---------------------------------|
| ICD-10 Diagnosis Codes       | Free text mapping    | Validated ICD-10-CM lookup      |
| SNOMED-CT Procedures         | Basic CPT codes      | SNOMED-CT terminology server    |
| LOINC Lab Observations       | Not implemented      | LOINC code binding              |
| NHCX FHIR Profiles           | Basic validation     | Full StructureDefinition checks |
| NHA Health ID (ABHA)         | Simple string field  | ABHA API verification           |

### Terminology Server Integration

Use a **FHIR Terminology Server** (e.g., [Ontoserver](https://ontoserver.csiro.au/) or [HAPI FHIR Server](https://hapifhir.io/)) to validate codes at conversion time:

```java
// Example: Validate ICD-10 code against a terminology server
public boolean validateCode(String system, String code) {
    String url = terminologyServerBase +
        "/ValueSet/$validate-code?system=" + system + "&code=" + code;
    // Call the FHIR terminology server and return validation result
}
```

---

## 10. Phased Roadmap

### Phase 1 — Hardening (Weeks 1–2)
- [ ] Replace `System.out.println` with SLF4J logging
- [ ] Add Spring Boot Actuator health endpoints
- [ ] Add Flyway for database migrations
- [ ] Add input validation for all mandatory HL7 fields (PID-8 gender, IN1 policy, etc.)
- [ ] Add retry count and resolved flag to DLQ

### Phase 2 — Security (Weeks 3–4)
- [ ] Add Spring Security with JWT authentication
- [ ] Implement role-based access control (Admin, Operator, Viewer)
- [ ] Enable HTTPS/TLS
- [ ] Encrypt PII fields at rest

### Phase 3 — Containerisation (Weeks 5–6)
- [ ] Create Dockerfile and docker-compose.yml
- [ ] Set up CI/CD with GitHub Actions
- [ ] Deploy to staging environment (Docker Compose or K8s)
- [ ] Add Prometheus + Grafana monitoring

### Phase 4 — Enterprise Integration (Weeks 7–10)
- [ ] Add MLLP listener for direct HL7 TCP ingestion
- [ ] Integrate Apache Kafka for high-throughput message processing
- [ ] Add scheduled DLQ retry service
- [ ] Add DLQ alerting (email/Slack)

### Phase 5 — NHCX Compliance (Weeks 11–14)
- [ ] Integrate FHIR terminology server for code validation
- [ ] Add full NHCX StructureDefinition profile validation
- [ ] Implement ABHA ID verification via NHA API
- [ ] Conduct compliance testing with NHCX sandbox

---

## Architecture Evolution Summary

```
  MVP (Now)                    Production (Target)
  ─────────                    ───────────────────

  Browser UI ──► Spring Boot   HIS/EHR ──► Mirth Connect ──► Kafka
                     │                                          │
                     ▼                              ┌───────────┼───────────┐
                  MySQL                             ▼           ▼           ▼
                                              Instance 1   Instance 2   Instance 3
                                                  │           │           │
                                                  └───────────┼───────────┘
                                                              ▼
                                                    MySQL (Primary + Replica)
                                                              │
                                                    Prometheus + Grafana
                                                    Spring Security + JWT
                                                    Flyway Migrations
                                                    FHIR Terminology Server
```

---

*Document Version: 1.0 — Created 27 Feb 2026*
