package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.config.TestConfig;
import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.Book;
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

    @Test
    @Story("Borrow one book")
    @Severity(SeverityLevel.BLOCKER)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrowing one book takes one copy and returns it in a one-item list")
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
        assertThat(library.fetchBook(before.id()))
                .as("book state after borrowing")
                .isEqualTo(borrowed);
    }

    @Test
    @Story("Borrow several books")
    @Severity(SeverityLevel.BLOCKER)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrowing several different books is returned in request order")
    void borrowsSeveralBooksInRequestOrder() {
        List<Book> before = books.differentAvailableBooks(2);
        List<Long> requestedIds = before.stream().map(Book::id).toList();

        BorrowResponse result = library.borrow(requestedIds);

        assertThat(result.borrowed()).extracting(Book::id).containsExactlyElementsOf(requestedIds);
        for (int index = 0; index < before.size(); index++) {
            Book expectedBefore = before.get(index);
            Book borrowed = result.borrowed().get(index);
            assertThat(borrowed).usingRecursiveComparison()
                    .ignoringFields("availableCopies")
                    .isEqualTo(expectedBefore);
            assertThat(borrowed.availableCopies()).isEqualTo(expectedBefore.availableCopies() - 1);
            assertThat(library.fetchBook(borrowed.id())).isEqualTo(borrowed);
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
        assertThat(library.fetchBook(unavailable.id()))
                .as("unavailable book is untouched")
                .isEqualTo(unavailable);
    }

    @Test
    @Story("Unknown book")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Returns 404 BOOK_NOT_FOUND for an unknown book ID without borrowing other requested books")
    void rejectsUnknownIdAtomically() {
        Book available = books.withAvailableCopies(1);
        long unknown = books.unknownBookId();

        Response response = library.postBorrowIds(List.of(available.id(), unknown));

        Problem problem = assertProblem(response, 404, ErrorCode.BOOK_NOT_FOUND);
        assertThat(problem.bookIds()).containsExactly(unknown);
        assertInstance(problem, BORROW_PATH);
        assertThat(library.fetchBook(available.id()))
                .as("available book is untouched when another ID is unknown")
                .isEqualTo(available);
    }

    @Test
    @Story("Atomic borrowing")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Does not borrow an available book when another requested book has no copies")
    void rejectsUnavailableBatchAtomically() {
        Book unavailable = books.withNoAvailableCopies();
        Book available = books.withAvailableCopies(1, unavailable.id());

        Response response = library.postBorrowIds(List.of(available.id(), unavailable.id()));

        Problem problem = assertProblem(response, 409, ErrorCode.NO_COPIES_AVAILABLE);
        assertThat(problem.bookIds()).containsExactly(unavailable.id());
        assertInstance(problem, BORROW_PATH);
        assertThat(library.fetchBook(available.id()))
                .as("available book is untouched when another requested book is unavailable")
                .isEqualTo(available);
    }

    @Test
    @Story("Validation")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Rejects the obsolete bookId request with a bookIds validation violation")
    void rejectsObsoleteSingleBookIdRequest() {
        Response response = library.postBorrow("{\"bookId\":1}");

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertInstance(problem, BORROW_PATH);
        assertViolationOn(problem, "bookIds");
    }

    @Test
    @Story("Validation")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Rejects duplicate book IDs")
    void rejectsDuplicateBookIds() {
        Book book = books.withAvailableCopies(1);

        Problem problem = assertProblem(library.postBorrowIds(List.of(book.id(), book.id())), 400, ErrorCode.VALIDATION_ERROR);

        assertInstance(problem, BORROW_PATH);
        assertViolationOn(problem, "bookIds");
        assertThat(library.fetchBook(book.id())).isEqualTo(book);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                arguments("missing bookIds", "{}", "bookIds"),
                arguments("bookIds is null", "{\"bookIds\":null}", "bookIds"),
                arguments("bookIds is empty", "{\"bookIds\":[]}", "bookIds"),
                arguments("bookIds contains zero", "{\"bookIds\":[0]}", null),
                arguments("bookIds contains a negative ID", "{\"bookIds\":[-5]}", null),
                arguments("bookIds has more than ten IDs", "{\"bookIds\":[1,2,3,4,5,6,7,8,9,10,11]}", "bookIds"),
                arguments("bookIds is a number", "{\"bookIds\":1}", null),
                arguments("bookIds contains a string", "{\"bookIds\":[\"abc\"]}", null),
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
