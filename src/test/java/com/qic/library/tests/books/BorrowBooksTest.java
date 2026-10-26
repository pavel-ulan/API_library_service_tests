package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.config.TestConfig;
import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.BookInfo;
import com.qic.library.tests.framework.model.BorrowResponse;
import com.qic.library.tests.framework.model.ErrorCode;
import com.qic.library.tests.framework.model.Problem;
import com.qic.library.tests.framework.model.Violation;
import com.qic.library.tests.framework.support.Inventory;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertInstance;
import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertProblem;
import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertViolationOn;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Feature("Borrow books")
@DisplayName("POST /books/borrow")
class BorrowBooksTest extends BaseApiTest {

    private static final String BORROW_PATH = TestConfig.apiBasePath() + "/books/borrow";

    // ---- Successful borrowing -------------------------------------------------------------

    @Test
    @Story("Borrow one book")
    @Severity(SeverityLevel.BLOCKER)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrowing a book takes one copy and returns it as a single object")
    void borrowsOneCopy() {
        Book before = books.withAvailableCopies(1);

        Response response = library.postBorrowId(before.id());

        assertThat(response.statusCode()).isEqualTo(200);
        BorrowResponse result = Json.read(response, BorrowResponse.class);
        Book borrowed = result.borrowed();
        assertThat(borrowed.id()).isEqualTo(before.id());
        assertThat(borrowed.availableCopies()).as("availableCopies").isEqualTo(before.availableCopies() - 1);
        assertThat(borrowed.totalCopies()).as("totalCopies").isEqualTo(before.totalCopies());
        assertThat(borrowed).usingRecursiveComparison()
                .ignoringFields("availableCopies").isEqualTo(before);
        assertThat(result.borrowedAt()).as("borrowedAt").isCloseTo(OffsetDateTime.now(), within(5, MINUTES));
        assertThat(library.fetchBookInfo(before.id()))
                .as("book state after borrowing")
                .isEqualTo(BookInfo.from(borrowed));
    }

    @Test
    @Story("Borrow one book")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrowing the last copy makes the book unavailable")
    void borrowingLastCopyMakesBookUnavailable() {
        Book book = books.withExactlyAvailableCopies(1);

        Book borrowed = library.borrow(book.id()).borrowed();

        assertThat(borrowed.availableCopies()).isZero();
        assertThat(library.searchBooks(null, null, true))
                .as("books listed with available=true")
                .extracting(Book::id)
                .doesNotContain(book.id());
    }

    // ---- Business rule violations ---------------------------------------------------------

    @Test
    @Story("No copies available")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Returns 409 NO_COPIES_AVAILABLE when the book has no copies")
    void rejectsBookWithNoCopies() {
        Book unavailable = books.withNoAvailableCopies();

        Response response = library.postBorrowId(unavailable.id());

        Problem problem = assertProblem(response, 409, ErrorCode.NO_COPIES_AVAILABLE);
        assertThat(problem.bookIds()).containsExactly(unavailable.id());
        assertInstance(problem, BORROW_PATH);
        assertThat(library.fetchBookInfo(unavailable.id()))
                .as("unavailable book is untouched")
                .isEqualTo(BookInfo.from(unavailable));
    }

    @Test
    @Story("Unknown book")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Returns 404 BOOK_NOT_FOUND for an unknown book ID")
    void rejectsUnknownId() {
        long unknown = books.unknownBookId();

        Response response = library.postBorrowId(unknown);

        Problem problem = assertProblem(response, 404, ErrorCode.BOOK_NOT_FOUND);
        assertThat(problem.bookIds()).containsExactly(unknown);
        assertInstance(problem, BORROW_PATH);
    }

    // ---- Request validation ---------------------------------------------------------------

    @Test
    @Story("Validation")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Rejects the legacy bookIds request with bookId: must not be null")
    void rejectsLegacyBookIdsRequest() {
        Response response = library.postBorrow("{\"bookIds\":[1]}");

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertInstance(problem, BORROW_PATH);
        assertViolationOn(problem, "bookId");
        assertThat(problem.violations())
                .filteredOn(violation -> "bookId".equals(violation.field()))
                .extracting(Violation::message)
                .contains("must not be null");
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                arguments("missing bookId", "{}", "bookId"),
                arguments("bookId is null", "{\"bookId\":null}", "bookId"),
                arguments("bookId is 0", "{\"bookId\":0}", "bookId"),
                arguments("negative bookId", "{\"bookId\":-5}", "bookId"),
                arguments("fractional bookId", "{\"bookId\":1.5}", null),
                arguments("bookId is a string", "{\"bookId\":\"abc\"}", null),
                arguments("bookId is an array", "{\"bookId\":[1]}", null),
                arguments("body is an array", "[1]", null),
                arguments("malformed JSON", "{\"bookId\":", null),
                arguments("empty body", "", null));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidRequests")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Rejects an invalid request with 400 VALIDATION_ERROR")
    void rejectsInvalidRequest(String caseName, String body, String violationField) {
        Response response = library.postBorrow(body);

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertInstance(problem, BORROW_PATH);
        if (violationField != null) {
            assertViolationOn(problem, violationField);
        }
    }
}
