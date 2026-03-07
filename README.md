# DocValidate 🏦

A Spring Boot application for **bank document second-level validation**, built as a learning project.

---

## 🚀 Quick Start (no Docker needed)

```bash
# Run with H2 in-memory database (dev profile is default)
./gradlew bootRun

# App starts at:  http://localhost:8080
# Swagger UI:     http://localhost:8080/swagger-ui.html
# H2 Console:     http://localhost:8080/h2-console
```

**Pre-seeded users:**
| Username     | Password      | Role                              |
|--------------|---------------|-----------------------------------|
| `admin`      | `Admin123!`   | ADMIN + SUPERVISOR + REVIEWER     |
| `supervisor` | `Super123!`   | SUPERVISOR + REVIEWER             |
| `reviewer1`  | `Review123!`  | REVIEWER                          |
| `reviewer2`  | `Review123!`  | REVIEWER                          |

---

## 📋 API Walkthrough

### 1. Login → get your JWT token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "reviewer1", "password": "Review123!"}'
```

### 2. Submit a document (multipart: JSON metadata + file)
```bash
curl -X POST http://localhost:8080/api/documents \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F 'request={"customerId":"CUST001","customerName":"Jane Smith","documentType":"NATIONAL_ID"};type=application/json' \
  -F 'file=@/path/to/id_scan.pdf'
```

### 3. View the pending queue
```bash
curl http://localhost:8080/api/documents/queue \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 4. Claim a document to review
```bash
curl -X POST http://localhost:8080/api/documents/1/claim \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 5. Submit a decision
```bash
# Approve
curl -X POST http://localhost:8080/api/documents/1/decision \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"decision": "APPROVE", "notes": "All documents verified and valid"}'

# Reject
curl -X POST http://localhost:8080/api/documents/1/decision \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"decision": "REJECT", "rejectionReason": "ID card is expired"}'

# Escalate
curl -X POST http://localhost:8080/api/documents/1/decision \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"decision": "ESCALATE", "escalationReason": "Suspected document forgery, needs supervisor review"}'
```

### 6. Supervisor resolves escalation
```bash
# Login as supervisor first, then:
curl -X POST http://localhost:8080/api/documents/1/resolve-escalation \
  -H "Authorization: Bearer SUPERVISOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"decision": "REJECT", "rejectionReason": "Confirmed forgery after manual verification"}'
```

---

## 🗂️ Project Structure

```
src/main/kotlin/com/bank/docvalidate/
├── DocValidateApplication.kt       # Entry point
├── config/
│   ├── SecurityConfig.kt           # Spring Security + JWT setup
│   ├── OpenApiConfig.kt            # Swagger/OpenAPI configuration
│   └── DataInitializer.kt          # Seeds test users on startup (dev only)
├── controller/
│   ├── AuthController.kt           # POST /api/auth/login|register
│   └── DocumentController.kt       # All /api/documents/** endpoints
├── service/
│   ├── AuthService.kt              # Login / registration business logic
│   ├── DocumentService.kt          # Review workflow state machine
│   └── FileStorageService.kt       # File upload / download
├── security/
│   ├── JwtTokenProvider.kt         # Generate & validate JWT tokens
│   ├── JwtAuthenticationFilter.kt  # Intercepts requests, sets auth context
│   ├── UserDetailsImpl.kt          # Bridges User entity ↔ Spring Security
│   └── UserDetailsServiceImpl.kt   # Loads user from DB for Spring Security
├── entity/
│   ├── User.kt                     # @Entity: reviewer/supervisor accounts
│   ├── Document.kt                 # @Entity: submitted documents
│   └── DocumentAuditLog.kt         # @Entity: immutable audit trail
├── repository/
│   └── Repositories.kt             # Spring Data JPA repositories
├── dto/
│   └── Dtos.kt                     # Request/Response objects (never expose entities!)
├── enums/
│   ├── DocumentStatus.kt           # Workflow states
│   ├── DocumentType.kt             # Supported document types
│   └── UserRole.kt                 # REVIEWER, SUPERVISOR, ADMIN
└── exception/
    └── Exceptions.kt               # Custom exceptions + global error handler
```

---

## 📖 Spring Boot Concepts Covered

| Concept | Where to find it |
|---------|-----------------|
| `@RestController` + `@RequestMapping` | `AuthController`, `DocumentController` |
| `@RequestBody`, `@PathVariable`, `@RequestParam` | `DocumentController` |
| `@RequestPart` (multipart/JSON+file) | `DocumentController.submitDocument()` |
| `@Entity`, `@ManyToOne`, `@OneToMany` | `Document`, `DocumentAuditLog` |
| `@GeneratedValue`, `@Enumerated` | `User`, `Document` |
| Spring Data JPA — method name queries | `DocumentRepository` |
| `@Query` (custom JPQL) | `DocumentRepository.findOverdueDocuments()` |
| `@Service`, `@Transactional` | `DocumentService`, `AuthService` |
| `@Transactional(readOnly = true)` | Read methods in `DocumentService` |
| Spring Security filter chain | `SecurityConfig` |
| JWT authentication | `JwtTokenProvider`, `JwtAuthenticationFilter` |
| `@PreAuthorize` method security | `DocumentService.resolveEscalation()` |
| `SecurityContextHolder` | `DocumentService.getCurrentUser()` |
| `@RestControllerAdvice` global error handling | `GlobalExceptionHandler` |
| `@Valid` bean validation | All request DTOs |
| `@Profile("dev")` conditional beans | `DataInitializer` |
| `CommandLineRunner` startup hook | `DataInitializer` |
| Multipart file upload | `FileStorageService` |
| OpenAPI / Swagger | `OpenApiConfig`, `@Operation`, `@Tag` |
| Pagination (`Pageable`, `Page<T>`) | `DocumentService`, `DocumentController` |

---

## 🏗️ Suggested Next Features (to keep learning)

1. **SLA monitoring** — `@Scheduled` method that flags overdue documents
2. **Email notifications** — Spring Mail when document is assigned/completed
3. **Document expiry validation** — business rule: reject if ID expired
4. **Supervisor dashboard** — aggregate stats with `@Query` + projections
5. **Flyway migrations** — replace `ddl-auto=create-drop` for proper DB versioning
6. **Integration tests** — `@SpringBootTest` + `MockMvc` + `@Sql` for DB seeding
7. **Docker multi-stage build** — containerize the app
8. **Actuator** — add `spring-boot-starter-actuator` for health/metrics endpoints

---

## 🐳 Run with PostgreSQL

```bash
# Start PostgreSQL (and MailHog for future email features)
docker-compose up -d

# Run with prod profile (uses PostgreSQL)
DB_USERNAME=docvalidate DB_PASSWORD=docvalidate123 ./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## 🧪 Run Tests

```bash
./gradlew test

# With HTML report
./gradlew test jacocoTestReport
```
