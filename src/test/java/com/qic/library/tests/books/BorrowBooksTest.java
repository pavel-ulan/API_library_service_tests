package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.config.TestConfig;
import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.BookInfo;
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
    @DisplayName("Borrowing one book takes one copy and returns a single-element list")
    void borrowsOneCopy() {
        Book before = books.withAvailableCopies(1);

        Response response = library.postBorrowId(before.id());

        assertThat(response.statusCode()).isEqualTo(200);
        BorrowResponse result = Json.read(response, BorrowResponse.class);
        assertThat(result.borrowed()).hasSize(1);
        Book borrowed = result.borrowed().getFirst();
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
    @Story("Borrow several books")
    @Severity(SeverityLevel.BLOCKER)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrows one copy of each requested book in request order")
    void borrowsSeveralDifferentBooksAtomically() {
        List<Book> before = books.differentAvailableBooks(2);
        List<Long> requestedIds = before.stream().map(Book::id).toList();

        BorrowResponse result = library.borrow(requestedIds);

        assertThat(result.borrowed()).extracting(Book::id)
                .as("borrowed book IDs")
                .containsExactlyElementsOf(requestedIds);
        for (int index = 0; index < before.size(); index++) {
            Book original = before.get(index);
            Book borrowed = result.borrowed().get(index);
            assertThat(borrowed.availableCopies())
                    .as("availableCopies for book %s", original.id())
                    .isEqualTo(original.availableCopies() - 1);
            assertThat(borrowed.totalCopies()).isEqualTo(original.totalCopies());
            assertThat(borrowed).usingRecursiveComparison()
                    .ignoringFields("availableCopies")
                    .isEqualTo(original);
            assertThat(library.fetchBookInfo(borrowed.id()))
                    .as("stored state for book %s", borrowed.id())
                    .isEqualTo(BookInfo.from(borrowed));
        }
    }

    @Test
    @Story("Borrow one book")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrowing the last copy makes the book unavailable")
    void borrowingLastCopyMakesBookUnavailable() {
        Book book = books.withExactlyAvailableCopies(1);

        Book borrowed = library.borrow(book.id()).borrowed().getFirst();

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
    @Story("No copies available")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Does not borrow any requested book when one has no available copy")
    void rejectsBatchWithUnavailableBookAtomically() {
        Book unavailable = books.withNoAvailableCopies();
        Book available = books.withAvailableCopies(1, unavailable.id());

        Response response = library.postBorrowIds(List.of(available.id(), unavailable.id()));

        Problem problem = assertProblem(response, 409, ErrorCode.NO_COPIES_AVAILABLE);
        assertThat(problem.bookIds()).containsExactly(unavailable.id());
        assertInstance(problem, BORROW_PATH);
        assertThat(library.fetchBookInfo(available.id()))
                .as("available book is untouched when the batch fails")
                .isEqualTo(BookInfo.from(available));
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

    @Test
    @Story("Unknown book")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Lists all unknown IDs and does not borrow known books in a failed batch")
    void rejectsBatchWithUnknownIdsAtomically() {
        Book available = books.withAvailableCopies(1);
        long firstUnknown = books.unknownBookId();
        long secondUnknown = firstUnknown + 1;

        Response response = library.postBorrowIds(List.of(available.id(), firstUnknown, secondUnknown));

        Problem problem = assertProblem(response, 404, ErrorCode.BOOK_NOT_FOUND);
        assertThat(problem.bookIds()).containsExactlyInAnyOrder(firstUnknown, secondUnknown);
        assertInstance(problem, BORROW_PATH);
        assertThat(library.fetchBookInfo(available.id()))
                .as("known book is untouched when the batch fails")
                .isEqualTo(BookInfo.from(available));
    }

    // ---- Request validation ---------------------------------------------------------------

    @Test
    @Story("Validation")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Rejects the legacy bookId request")
    void rejectsLegacyBookIdRequest() {
        Response response = library.postBorrow("{\"bookId\":1}");

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertInstance(problem, BORROW_PATH);
        assertViolationOn(problem, "bookIds");
    }

    @Test
    @Story("Validation")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Rejects duplicate book IDs without changing inventory")
    void rejectsDuplicateBookIds() {
        Book before = books.withAvailableCopies(1);

        Response response = library.postBorrowIds(List.of(before.id(), before.id()));

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertInstance(problem, BORROW_PATH);
        assertViolationOn(problem, "bookIds");
        assertThat(library.fetchBookInfo(before.id()))
                .as("book is untouched")
                .isEqualTo(BookInfo.from(before));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                arguments("missing bookIds", "{}", "bookIds"),
                arguments("bookIds is null", "{\"bookIds\":null}", "bookIds"),
                arguments("bookIds is empty", "{\"bookIds\":[]}", "bookIds"),
                arguments("more than ten bookIds", "{\"bookIds\":[1,2,3,4,5,6,7,8,9,10,11]}", "bookIds"),
                arguments("bookIds contains zero", "{\"bookIds\":[0]}", null),
                arguments("bookIds contains a negative ID", "{\"bookIds\":[-5]}", null),
                arguments("bookIds contains a fractional ID", "{\"bookIds\":[1.5]}", null),
                arguments("bookIds contains a string", "{\"bookIds\":[\"abc\"]}", null),
                arguments("bookIds is not an array", "{\"bookIds\":1}", null),
                arguments("body is an array", "[1]", null),
                arguments("malformed JSON", "{\"bookIds\":", null),
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
