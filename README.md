# CST438 Gradebook (Team 02)

Spring Boot 3.4 / Java 17 REST service for a course gradebook. Instructors manage
assignments, enrollment grades, and assignment scores; students view their assignments.
Uses an in-memory H2 database (seeded from `schema.sql` / `data.sql`), JWT
authentication, and RabbitMQ messaging to the Registrar service.

## Running

```
./mvnw spring-boot:run        # serves on http://localhost:8081
./mvnw test                   # run unit tests
```

Seed users (see `data.sql`): `admin@csumb.edu`, `sam@csumb.edu` (student), `ted@csumb.edu` (instructor).
Obtain a JWT with `GET /login` using HTTP Basic auth, then send `Authorization: Bearer <jwt>`.

## Database schema

```
term 1─* section *─1 course
section 1─* enrollment *─1 user_table
section 1─* assignment
enrollment 1─* grade *─1 assignment
```

`section.instructor_email` is a plain string (not a foreign key).

## Endpoints

| Controller | Endpoint |
|---|---|
| Assignment | `GET /sections`, `GET /sections/{secNo}/assignments`, `GET /assignments`, `POST /assignments`, `PUT /assignments`, `DELETE /assignments/{assignmentId}` |
| Enrollment | `GET /sections/{sectionNo}/enrollments`, `PUT /enrollments` |
| Grade | `GET /assignments/{assignmentId}/grades`, `PUT /grades` |
| Login / Health | `GET /login`, `GET /`, `GET /exit` |

## Team contributions

Based on git history.

| Member | Work |
|---|---|
| **David Wisneski** | Initial project scaffolding (domain, DTOs, services, security config, schema), `.gitignore`, webflux test dependency, 2026 terms and Fall 2026 test data in `data.sql` |
| **Alexis Wogoman** | `AssignmentController` and `AssignmentControllerUnitTest` (including negative-path tests); updated gradebook system test data in `data.sql` |
| **Brandon Nhep** | `GradeController` (`getAssignmentGrades`, `updateGrades`) and `GradeControllerUnitTest` (including bad-path tests); new queries in `SectionRepository`, `EnrollmentRepository`, `GradeRepository` |
| **Austin Avery** | `EnrollmentController` methods and `EnrollmentControllerUnitTest` |
| **Shpetim Mujeci** | Reviewed and merged the assignment-controller pull request (#6) |
