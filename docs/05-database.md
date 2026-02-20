# 5. Database & Persistence — How Data Is Stored

## Why Store Conversions?

Every conversion attempt — successful or failed — is saved to MySQL. This gives us:

| Benefit | Description |
|---------|-------------|
| **Audit trail** | Know exactly what was converted, when, and whether it succeeded |
| **Debugging** | If a conversion fails, we can see the original HL7 message and the error |
| **Replay** | Re-process old messages if the mapping rules change |
| **Analytics** | Count success/error rates, most common errors, etc. |

---

## MySQL Schema

**File:** `src/main/resources/schema.sql`

```sql
CREATE TABLE IF NOT EXISTS conversion_records (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    raw_hl7         TEXT          NOT NULL,         -- Original HL7 message
    fhir_json       LONGTEXT,                       -- Generated FHIR Bundle
    status          VARCHAR(20)   NOT NULL,          -- SUCCESS or ERROR
    error_message   TEXT,                            -- Error details (if failed)
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
```

### Column Details

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | `BIGINT` | No | Auto-incrementing primary key |
| `raw_hl7` | `TEXT` | No | The exact HL7 message received (up to 65KB) |
| `fhir_json` | `LONGTEXT` | Yes | Generated FHIR JSON (null if conversion failed) |
| `status` | `VARCHAR(20)` | No | `"SUCCESS"` or `"ERROR"` |
| `error_message` | `TEXT` | Yes | Error description (null if successful) |
| `created_at` | `TIMESTAMP` | No | Auto-set to current time on insert |

### Why TEXT vs LONGTEXT?

- `TEXT` = up to 65,535 bytes (~65 KB) — enough for HL7 messages and error strings
- `LONGTEXT` = up to 4 GB — FHIR bundles can be large with many resources

---

## JPA Entity

**File:** `src/main/java/com/nhcx/fhirconverter/model/ConversionRecord.java`

This Java class maps to the MySQL table:

```java
@Entity                                      // Marks this as a JPA entity
@Table(name = "conversion_records")           // Links to the table name
public class ConversionRecord {

    @Id                                       // Primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // Auto-increment
    private Long id;

    @Column(name = "raw_hl7", nullable = false, columnDefinition = "TEXT")
    private String rawHl7;

    @Column(name = "fhir_json", columnDefinition = "LONGTEXT")
    private String fhirJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist                               // Called before saving
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();  // Auto-set timestamp
    }
}
```

### How JPA Maps Java ↔ SQL

```
Java Class                         MySQL Table
──────────                         ───────────
ConversionRecord          ←→       conversion_records
  Long id                 ←→         BIGINT id
  String rawHl7           ←→         TEXT raw_hl7
  String fhirJson         ←→         LONGTEXT fhir_json
  String status           ←→         VARCHAR(20) status
  String errorMessage     ←→         TEXT error_message
  LocalDateTime createdAt ←→         TIMESTAMP created_at
```

### What Is `@PrePersist`?

It's a JPA lifecycle callback. When you call `repository.save(record)`:
1. JPA checks if `@PrePersist` methods exist
2. Calls `onCreate()` → sets `createdAt = now()`
3. Generates the SQL INSERT
4. Executes the INSERT

---

## Repository

**File:** `src/main/java/com/nhcx/fhirconverter/repository/ConversionRepository.java`

```java
@Repository
public interface ConversionRepository extends JpaRepository<ConversionRecord, Long> {
    // No custom methods — JpaRepository provides everything we need
}
```

### What Do We Get for Free?

By extending `JpaRepository<ConversionRecord, Long>`, Spring auto-generates:

| Method | SQL Equivalent |
|--------|---------------|
| `save(record)` | `INSERT INTO conversion_records ...` |
| `findById(1L)` | `SELECT * FROM conversion_records WHERE id = 1` |
| `findAll()` | `SELECT * FROM conversion_records` |
| `deleteById(1L)` | `DELETE FROM conversion_records WHERE id = 1` |
| `count()` | `SELECT COUNT(*) FROM conversion_records` |

**Zero SQL written.** Spring Data JPA generates it all at runtime.

---

## How Records Are Saved (in ConversionService)

### On Success:
```java
ConversionRecord record = new ConversionRecord();
record.setRawHl7(rawHl7);          // Store original message
record.setFhirJson(fhirJson);      // Store generated FHIR
record.setStatus("SUCCESS");        // Mark as successful
conversionRepository.save(record);  // INSERT into MySQL
```

### On Error:
```java
record.setStatus("ERROR");
record.setErrorMessage(e.getMessage());  // Store the error
conversionRepository.save(record);       // Still save — for debugging
```

---

## Database Configuration

**File:** `src/main/resources/application.properties`

```properties
# Connection URL
spring.datasource.url=jdbc:mysql://localhost:3306/fhir_converter_db

# Credentials (change to match your MySQL setup)
spring.datasource.username=root
spring.datasource.password=root

# JDBC driver class
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Hibernate auto-creates/updates tables based on @Entity classes
spring.jpa.hibernate.ddl-auto=update

# Show SQL in console (helpful for learning)
spring.jpa.show-sql=true

# Run schema.sql on startup
spring.sql.init.mode=always
spring.sql.init.continue-on-error=true
```

### What Does `ddl-auto=update` Do?

| Value | Behavior |
|-------|----------|
| `none` | Don't touch the schema |
| `update` | Create missing tables/columns, never drops anything |
| `create` | Drop all tables and recreate on each startup |
| `create-drop` | Like `create`, but also drops on shutdown |
| `validate` | Only check that schema matches entities, fail if not |

We use `update` for development — it creates the table automatically if it doesn't exist.

---

## Test Database Configuration

**File:** `src/test/resources/application.properties`

```properties
# H2 in-memory database — no MySQL needed for tests
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop    # Recreate schema for each test
spring.sql.init.mode=never                    # Don't run schema.sql
```

Tests use **H2** (an in-memory database) so they run without MySQL.

---

## Querying the Database

After running conversions, you can inspect the data:

```sql
-- See all conversions
SELECT * FROM conversion_records;

-- See only failures
SELECT id, status, error_message, created_at
FROM conversion_records
WHERE status = 'ERROR';

-- Count conversions by status
SELECT status, COUNT(*) as count
FROM conversion_records
GROUP BY status;
```
