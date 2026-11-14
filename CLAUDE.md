# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

DocValidate: a Kotlin + Spring Boot 3.2 (JDK 17, Gradle Kotlin DSL) REST API for second-level validation of bank customer documents. It is a learning project — source files carry long "KEY CONCEPTS" doc comments explaining Spring features; keep that style when adding code. A React/Vite frontend lives in the sibling repo `../docvalidate-frontend` (Vite dev server on port 3000 proxies `/api` to `http://localhost:8080`).

## Commands

```bash
./gradlew bootRun                     # run with dev profile (H2 in-memory, seeded users)
./gradlew build                       # compile + test
./gradlew test                        # all tests
./gradlew test --tests "com.bank.docvalidate.DocumentServiceTest"                 # one class
./gradlew test --tests "com.bank.docvalidate.DocumentServiceTest.someMethodName"  # one test

# PostgreSQL via docker-compose + prod profile
docker-compose up -d
DB_USERNAME=docvalidate DB_PASSWORD=docvalidate123 ./gradlew bootRun --args='--spring.profiles.active=prod'
```

On Windows use `gradlew.bat` (or `./gradlew` from Git Bash). The README mentions `jacocoTestReport`, but the jacoco plugin is not configured in `build.gradle.kts`, so that task does not exist. There is no linter configured.

Dev URLs: Swagger UI `http://localhost:8080/swagger-ui.html`, H2 console `/h2-console`. Dev seed users (from `DataInitializer`, `@Profile("dev")`): `admin`/`Admin123!`, `supervisor`/`Super123!`, `reviewer1`/`Review123!`, `reviewer2`/`Review123!`.

## Architecture

Layered: `controller` → `service` → `repository` (Spring Data JPA) → `entity`. Controllers only return DTOs from `dto/Dtos.kt` wrapped in `ApiResponse<T>`; entities are never exposed (use the `DocumentResponse.from(...)` / `DocumentSummary.from(...)` mappers). All repositories live in one file, `repository/Repositories.kt`; all DTOs in `dto/Dtos.kt`; all custom exceptions plus the `@RestControllerAdvice` handler in `exception/Exceptions.kt` (`ResourceNotFoundException` → 404, `BadRequestException` → 400).

### Review workflow (state machine in `service/DocumentService.kt`)

Status transitions are enforced in the service, not the entity:

```
POST /api/documents            → PENDING_SECOND_LEVEL   (first-level review happens in another system)
POST /{id}/claim               PENDING_SECOND_LEVEL → UNDER_REVIEW (sets assignedReviewer, reviewStartedAt)
POST /{id}/decision            UNDER_REVIEW → APPROVED | REJECTED | ESCALATED
POST /{id}/resolve-escalation  ESCALATED → APPROVED | REJECTED   (SUPERVISOR/ADMIN only)
```

- REJECT requires `rejectionReason`; ESCALATE requires `escalationReason`. Invalid transitions throw `BadRequestException`.
- Every transition must write a `DocumentAuditLog` row via `addAuditLog(...)`. The class is `@Transactional`, so status change and audit row commit together.
- `SUBMITTED` is used only as the "from" status of the audit row written on submission; no document is ever stored with `SUBMITTED` or `CANCELLED`.
- `getDashboardStats()` `approvedToday`/`rejectedToday` are all-time counts (there is a TODO to filter by today).

### Security

- Stateless JWT: `JwtAuthenticationFilter` validates the bearer token and populates `SecurityContextHolder`; services get the current user via `getCurrentUser()` (reads `UserDetailsImpl` principal, then loads `User` from DB).
- `SecurityConfig` only distinguishes public (`/api/auth/**`, Swagger, H2 console) vs authenticated. Role checks are method-level `@PreAuthorize` in services (`@EnableMethodSecurity`).
- `UserRole` enum values already carry the `ROLE_` prefix (`ROLE_REVIEWER`, …) and are mapped directly to authorities, so `@PreAuthorize("hasRole('REVIEWER')")` works as expected.

### File storage

`FileStorageService` writes uploads to `app.upload.dir` (`./uploads`, gitignored) under a UUID-based name. `Document` stores both `originalFileName` and `storedFileName`; `loadAsResource` expects the **stored** name. Because `DocumentResponse` doesn't expose `storedFileName`, file loading goes through `DocumentService.loadDocumentFile(id)` rather than the controller calling `FileStorageService` directly.

### Profiles / persistence

- `application.properties` sets `spring.profiles.active=dev` and holds JWT secret, upload limits (10MB), Swagger paths.
- `dev`: H2 in-memory (PostgreSQL mode), `ddl-auto=create-drop` — data is lost on restart.
- `prod`: PostgreSQL, `ddl-auto=validate`, and there are no migrations (no Flyway/Liquibase), so the schema must already exist.

### Tests

`src/test` currently has only `DocumentServiceTest` — plain JUnit 5 + mockito-kotlin unit tests with no Spring context. They mock the repositories and set a fake `SecurityContext` so `getCurrentUser()` resolves.

Stub repository `save()` with `doAnswer { it.arguments[0] }.whenever(repo).save(any<Entity>())`, not `whenever(repo.save(any())).thenAnswer { ... }`. The latter calls the mock during stubbing, gets `null` back for the generic `<S : T> save(S): S`, and Kotlin's non-null check throws an NPE.
