# Library API tests

Automated API tests for the [Library API](https://github.com/pavel-ulan/API_library_service): search, get, borrow and return books.

**Stack:** Java 21 · Maven · JUnit 5 · REST Assured · AssertJ · Allure · swagger-request-validator (OpenAPI contract checks)

## What's covered

| Area | Test class | Checks |
|---|---|---|
| List and search | `SearchBooksTest` | all books and field invariants, sorting, partial and case-insensitive title/author search, combined filters, `available=true/false`, no matches, parameter validation (length, type) |
| Get a book | `GetBookTest` | existing book, 404 for an unknown ID, 400 for non-integer and non-positive IDs |
| Borrow | `BorrowBooksTest` | one book, several books in request order, the last copy, all-or-nothing on 409, every unavailable/unknown ID listed, 404 before 409, 14 kinds of invalid request body |
| Return | `ReturnBookTest` | return a borrowed copy, 409 when all copies are in, 404, 400 |
| Lifecycle | `BookLifecycleTest` | borrow every copy → 409 → return every copy → 409, book back to its original state |
| Service endpoints | `ApiDocumentationTest` | OpenAPI spec is served, Swagger UI, health |

**Contract validation.** Every `/api/v1` response is also checked against the service's OpenAPI spec (status code documented, content type, body schema). The spec is downloaded from the running service (`/openapi/library-api.yaml`), so tests always check the contract the service actually publishes. Request validation is off because negative tests send invalid requests deliberately.

## Running

Start the service first (see its README), then:

```bash
mvn clean test                                   # against http://localhost:8080
mvn clean test -DbaseUrl=http://test-host:8080   # another environment
mvn clean test -Dtest=BorrowBooksTest            # one class
```

### Allure report

```bash
mvn allure:serve     # builds the report and opens it in the browser
mvn allure:report    # writes it to target/site/allure-maven-plugin
```

The report includes every HTTP request and response, steps, severity, features/stories and an environment section (base URL, spec, Java version).

### Settings

Each value is taken from a system property, then an environment variable, then `src/test/resources/config.properties`.

| System property | Env variable | Default | Meaning |
|---|---|---|---|
| `baseUrl` | `BASE_URL` | `http://localhost:8080` | Where the service runs |
| `apiBasePath` | `API_BASE_PATH` | `/api/v1` | API base path |
| `specUrl` | `SPEC_URL` | `<baseUrl>/openapi/library-api.yaml` | OpenAPI spec for contract checks (URL or file path) |
| `contractValidation` | `CONTRACT_VALIDATION` | `true` | Turn contract checks on or off |

Parallelism is set in `junit-platform.properties` (4 threads by default). To change it: `-Djunit.jupiter.execution.parallel.config.fixed.parallelism=8`. To run sequentially: `-Djunit.jupiter.execution.parallel.enabled=false`.

## How tests handle shared state

The service keeps data in memory and has no reset endpoint, so the tests:

- **Don't depend on seed data.** Each test finds a suitable book at runtime (`BookFixtures`), e.g. a book with at least one copy available, or one with no copies left (prepared by borrowing every copy if none exists).
- **Clean up after themselves.** `InventoryTracker` is a REST Assured filter that records every successful borrow/return a test makes. After each test, `BaseApiTest` returns whatever is still borrowed, so the service is left as the test found it.
- **Run in parallel safely.** Tests that change copy counters hold the `library-inventory` resource lock (read-write); tests that compare counters across requests hold it in read mode; everything else runs freely. See `Inventory`.
- **Fail fast.** If the service isn't reachable, every test fails with one clear message instead of connection errors.

## Project layout

```
src/test/java/com/qic/library/tests
├── BaseApiTest                 per-test client, fixtures, clean-up, service check
├── books/                      SearchBooksTest, GetBookTest, BorrowBooksTest, ReturnBookTest, BookLifecycleTest
├── docs/                       ApiDocumentationTest
└── framework/
    ├── client/                 LibraryClient (API calls as Allure steps), InventoryTracker
    ├── fixtures/               BookFixtures (find or prepare books in a given state)
    ├── assertions/             ProblemAssertions (RFC 7807 errors)
    ├── model/                  Book, BorrowRequest, BorrowResponse, Problem, Violation, ErrorCode
    ├── http/                   RequestSpecs, ContractValidation, Json
    ├── config/                 TestConfig
    └── support/                Inventory (lock name), ServiceHealth
```

`LibraryClient` has two kinds of methods: raw ones (`listBooks`, `getBook`, `postBorrow`, `returnBook`) return the `Response` and assert nothing, for the request under test; typed ones (`listAllBooks`, `fetchBook`, `borrow`, `returnCopy`) expect `200` and return parsed objects, for preparation and follow-up checks.

## CI

`.github/workflows/api-tests.yml` checks out this repo and the service repo, builds and starts the service, runs the tests and uploads the Allure report and raw results as artifacts. It runs on push to `main`, on pull requests and manually (with a choice of service branch). If the service repository is private, add a `SERVICE_REPO_TOKEN` secret with read access to it.

## Service defects found by this suite

All fixed in the service; kept here as a record of what the tests guard against.

| Found by | Problem | Fix in the service |
|---|---|---|
| Contract validation (every test that reads books) | Books without an ISBN were returned with `"isbn": null`; the spec allows the field to be absent, not null | `spring.jackson.default-property-inclusion: non_null` |
| `BorrowBooksTest` › null ID | `{"bookIds":[null]}` → 500 (NullPointerException) | `BookService` rejects null IDs with 400 `VALIDATION_ERROR` |
| `BorrowBooksTest` › fractional ID | `{"bookIds":[1.5]}` → 200, book 1 borrowed (1.5 truncated to 1) | `spring.jackson.deserialization.accept-float-as-int: false` → 400 |
