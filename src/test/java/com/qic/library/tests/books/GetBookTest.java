package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.config.TestConfig;
import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.ErrorCode;
import com.qic.library.tests.framework.model.Problem;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertInstance;
import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertProblem;
import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertViolationOn;
import static org.assertj.core.api.Assertions.assertThat;

@Feature("Get book")
@DisplayName("GET /books/{bookId}")
class GetBookTest extends BaseApiTest {

    @Test
    @Story("Get existing book")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Returns the complete Book schema, including author")
    void returnsBookById() {
        Book expected = books.anyBook();

        Response response = library.getBook(expected.id());

        assertThat(response.statusCode()).isEqualTo(200);
        Book actual = Json.read(response, Book.class);
        assertThat(response.jsonPath().getMap("$"))
                .as("GET /books/{bookId} response fields")
                .containsKey("author");
        // Counters may change in parallel tests between the two requests, so only static data is compared.
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.title()).isEqualTo(expected.title());
        assertThat(actual.author()).isEqualTo(expected.author());
        assertThat(actual.isbn()).isEqualTo(expected.isbn());
        assertThat(actual.publicationYear()).isEqualTo(expected.publicationYear());
        assertThat(actual.totalCopies()).isEqualTo(expected.totalCopies());
        assertThat(actual.availableCopies()).isBetween(0, actual.totalCopies());
    }

    @Test
    @Story("Unknown book")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Returns 404 BOOK_NOT_FOUND for an unknown ID")
    void returns404ForUnknownId() {
        long unknownId = books.unknownBookId();

        Response response = library.getBook(unknownId);

        Problem problem = assertProblem(response, 404, ErrorCode.BOOK_NOT_FOUND);
        assertThat(problem.bookIds()).containsExactly(unknownId);
        assertThat(problem.detail()).contains(String.valueOf(unknownId));
        assertInstance(problem, TestConfig.apiBasePath() + "/books/" + unknownId);
    }

    @ParameterizedTest(name = "bookId = \"{0}\"")
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Returns 400 for an ID that isn't a 64-bit integer")
    void returns400ForNonIntegerId(String bookId) {
        Response response = library.getBook(bookId);

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertViolationOn(problem, "bookId");
    }

    @ParameterizedTest(name = "bookId = {0}")
    @ValueSource(longs = {0, -1})
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Returns 400 for an ID below 1")
    void returns400ForNonPositiveId(long bookId) {
        Response response = library.getBook(bookId);

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertViolationOn(problem, "bookId");
    }
}
