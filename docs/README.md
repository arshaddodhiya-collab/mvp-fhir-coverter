# 📚 FHIR Converter — Project Documentation

## What Is This Project?

This is a **Legacy to NHCX-aligned FHIR Converter** — an MVP that takes old-format hospital messages (HL7 v2) and converts them into modern FHIR R4 JSON that the **National Health Claims Exchange (NHCX)** can understand.

**In plain English:** Indian hospitals often use a text-based format called **HL7 v2** to store patient data. The government's new health exchange (NHCX) requires data in **FHIR R4 JSON** format. This converter bridges the gap.

---

## Documentation Index

| Document | What You'll Learn |
|----------|------------------|
| [1. Architecture Overview](./01-architecture.md) | Project structure, layers, and how data flows end-to-end |
| [2. HL7 Parsing](./02-hl7-parsing.md) | What HL7 is, how we split it by `\|` and `^`, and extract patient data |
| [3. YAML Mapping](./03-yaml-mapping.md) | How mapping rules are stored in YAML and loaded at runtime |
| [4. FHIR Construction](./04-fhir-construction.md) | How we build Patient, Coverage, and CoverageEligibilityRequest resources |
| [5. Database & Persistence](./05-database.md) | MySQL schema, JPA entity, and how conversion records are stored |
| [6. REST API Guide](./06-api-guide.md) | Endpoints, request/response formats, and curl examples |
| [7. Full Code Walkthrough](./07-code-walkthrough.md) | Line-by-line explanation of every Java class |

---

## Quick Start

```bash
# 1. Create the MySQL database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS fhir_converter_db;"

# 2. Update credentials in src/main/resources/application.properties

# 3. Run the app
cd /home/artem/test/fhir-converter
mvn spring-boot:run

# 4. Send a test request
curl -X POST http://localhost:8080/api/convert/coverage \
  -H "Content-Type: text/plain" \
  -d "PID|1||ABHA123||Sharma^Rahul||19900415|M"
```

---

## Tech Stack

| Technology | Purpose |
|-----------|---------|
| **Spring Boot 3.4.3** | Web framework + dependency injection |
| **MySQL** | Persistent storage for conversion records |
| **Spring Data JPA** | ORM layer (Java objects ↔ database rows) |
| **Jackson** | JSON serialization (Map → JSON string) |
| **SnakeYAML** | YAML file parsing (mapping profiles) |
| **Maven** | Build tool + dependency management |
| **H2** | In-memory database for unit tests |
