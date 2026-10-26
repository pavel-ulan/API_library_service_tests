package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.config.TestConfig;
import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.BorrowResponse;
import com.qic.library.tests.framework.model.ErrorCode;
import com.qic.library.tests.framework.model.Problem;
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
import java.util.List;
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
    @DisplayName("Borrowing a book takes one copy and returns the updated book")
    void borrowsOneCopy() {
        Book before = books.withAvailableCopies(1);

        Response response = library.postBorrowIds(before.id());

        assertThat(response.statusCode()).isEqualTo(200);
        BorrowResponse result = Json.read(response, BorrowResponse.class);
        assertThat(result.borrowed()).hasSize(1);
        Book borrowed = result.borrowed().get(0);
        assertThat(borrowed.id()).isEqualTo(before.id());
        assertThat(borrowed.availableCopies()).as("availableCopies").isEqualTo(before.availableCopies() - 1);
        assertThat(borrowed.totalCopies()).as("totalCopies").isEqualTo(before.totalCopies());
        assertThat(borrowed).usingRecursiveComparison()
                .ignoringFields("availableCopies").isEqualTo(before);
        assertThat(result.borrowedAt()).as("borrowedAt").isCloseTo(OffsetDateTime.now(), within(5, MINUTES));
        assertThat(library.fetchBook(before.id())).as("book state after borrowing").isEqualTo(borrowed);
    }

    @Test
    @Story("Borrow several books")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrowing several books takes one copy of each, in request order")
    void borrowsSeveralBooksInRequestOrder() {
        List<Book> before = books.differentAvailableBooks(3).reversed();
        Long[] ids = before.stream().map(Book::id).toArray(Long[]::new);

        Response response = library.postBorrowIds(ids);

        assertThat(response.statusCode()).isEqualTo(200);
        BorrowResponse result = Json.read(response, BorrowResponse.class);
        assertThat(result.borrowed()).extracting(Book::id).containsExactly(ids);
        for (int i = 0; i < before.size(); i++) {
            Book original = before.get(i);
            assertThat(result.borrowed().get(i).availableCopies())
                    .as("availableCopies of book %d", original.id())
                    .isEqualTo(original.availableCopies() - 1);
            assertThat(library.fetchBook(original.id()).availableCopies())
                    .as("stored availableCopies of book %d", original.id())
                    .isEqualTo(original.availableCopies() - 1);
        }
    }

    @Test
    @Story("Borrow one book")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrowing the last copy makes the book unavailable")
    void borrowingLastCopyMakesBookUnavailable() {
        Book book = books.withExactlyAvailableCopies(1);

        Book borrowed = library.borrow(book.id()).borrowed().get(0);

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
    @DisplayName("Returns 409 NO_COPIES_AVAILABLE and borrows nothing if one book has no copies")
    void rejectsWholeRequestWhenOneBookHasNoCopies() {
        Book unavailable = books.withNoAvailableCopies();
        Book available = books.withAvailableCopies(1, unavailable.id());

        Response response = library.postBorrowIds(available.id(), unavailable.id());

        Problem problem = assertProblem(response, 409, ErrorCode.NO_COPIES_AVAILABLE);
        assertThat(problem.bookIds()).containsExactly(unavailable.id());
        assertInstance(problem, BORROW_PATH);
        assertThat(library.fetchBook(available.id()))
                .as("available book is untouched (all-or-nothing)")
                .isEqualTo(available);
        assertThat(library.fetchBook(unavailable.id()).availableCopies()).isZero();
    }

    @Test
    @Story("No copies available")
    @Severity(SeverityLevel.NORMAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Lists every book without copies in the 409 response")
    void listsAllUnavailableBooks() {
        Book first = books.withNoAvailableCopies();
        Book second = books.withNoAvailableCopies(first.id());

        Response response = library.postBorrowIds(first.id(), second.id());

        Problem problem = assertProblem(response, 409, ErrorCode.NO_COPIES_AVAILABLE);
        assertThat(problem.bookIds()).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    @Story("Unknown book")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Returns 404 BOOK_NOT_FOUND listing every unknown ID and borrows nothing")
    void rejectsUnknownIds() {
        Book known = books.withAvailableCopies(1);
        long unknown1 = books.unknownBookId();
        long unknown2 = unknown1 + 1;

        Response response = library.postBorrowIds(known.id(), unknown1, unknown2);

        Problem problem = assertProblem(response, 404, ErrorCode.BOOK_NOT_FOUND);
        assertThat(problem.bookIds()).containsExactlyInAnyOrder(unknown1, unknown2);
        assertInstance(problem, BORROW_PATH);
        assertThat(library.fetchBook(known.id())).as("known book is untouched").isEqualTo(known);
    }

    @Test
    @Story("Unknown book")
    @Severity(SeverityLevel.NORMAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Reports unknown IDs (404) before books without copies (409)")
    void reportsUnknownBeforeUnavailable() {
        Book unavailable = books.withNoAvailableCopies();
        long unknown = books.unknownBookId();

        Response response = library.postBorrowIds(unavailable.id(), unknown);

        Problem problem = assertProblem(response, 404, ErrorCode.BOOK_NOT_FOUND);
        assertThat(problem.bookIds()).containsExactly(unknown);
    }

    // ---- Request validation ---------------------------------------------------------------

    static Stream<Arguments> invalidRequests() {
        String elevenIds = "{\"bookIds\":[1,2,3,4,5,6,7,8,9,10,11]}";
        return Stream.of(
                arguments("empty bookIds", "{\"bookIds\":[]}", "bookIds"),
                arguments("missing bookIds", "{}", "bookIds"),
                arguments("bookIds is null", "{\"bookIds\":null}", "bookIds"),
                arguments("more than 10 IDs", elevenIds, "bookIds"),
                arguments("duplicate IDs", "{\"bookIds\":[1,1]}", "bookIds"),
                arguments("ID is 0", "{\"bookIds\":[0]}", "bookIds[0]"),
                arguments("negative ID", "{\"bookIds\":[1,-5]}", "bookIds[1]"),
                arguments("null ID", "{\"bookIds\":[null]}", null),
                arguments("fractional ID", "{\"bookIds\":[1.5]}", null),
                arguments("ID is a string", "{\"bookIds\":[\"abc\"]}", null),
                arguments("bookIds is not an array", "{\"bookIds\":1}", null),
                arguments("body is an array", "[1]", null),
                arguments("malformed JSON", "{\"bookIds\":", null),
                arguments("empty body", "", null));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidRequests")
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    // A correct service changes nothing here, but a defective one might borrow a book (see README, known defects)
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
