package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.config.TestConfig;
import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.BookInfo;
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
import org.junit.jupiter.params.provider.ValueSource;

import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertInstance;
import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertProblem;
import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertViolationOn;
import static org.assertj.core.api.Assertions.assertThat;

@Feature("Return book")
@DisplayName("POST /books/{bookId}/return")
class ReturnBookTest extends BaseApiTest {

    @Test
    @Story("Return a borrowed copy")
    @Severity(SeverityLevel.BLOCKER)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Returning a borrowed copy puts it back and returns the updated book")
    void returnsBorrowedCopy() {
        Book original = books.withAvailableCopies(1);
        library.borrow(original.id());

        Response response = library.returnBook(original.id());

        assertThat(response.statusCode()).isEqualTo(200);
        Book returned = Json.read(response, Book.class);
        assertThat(returned).as("book after borrow + return").isEqualTo(original);
        assertThat(library.fetchBookInfo(original.id()))
                .as("stored book")
                .isEqualTo(BookInfo.from(original));
    }

    @Test
    @Story("All copies already returned")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Returns 409 ALL_COPIES_RETURNED when every copy is already in the library")
    void rejectsReturnWhenAllCopiesAreIn() {
        Book book = books.withAllCopiesInLibrary();

        Response response = library.returnBook(book.id());

        Problem problem = assertProblem(response, 409, ErrorCode.ALL_COPIES_RETURNED);
        assertThat(problem.bookIds()).containsExactly(book.id());
        assertInstance(problem, TestConfig.apiBasePath() + "/books/" + book.id() + "/return");
        assertThat(library.fetchBookInfo(book.id()))
                .as("book is untouched")
                .isEqualTo(BookInfo.from(book));
    }

    @Test
    @Story("Unknown book")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Returns 404 BOOK_NOT_FOUND for an unknown ID")
    void returns404ForUnknownId() {
        long unknownId = books.unknownBookId();

        Response response = library.returnBook(unknownId);

        Problem problem = assertProblem(response, 404, ErrorCode.BOOK_NOT_FOUND);
        assertThat(problem.bookIds()).containsExactly(unknownId);
    }

    @ParameterizedTest(name = "bookId = \"{0}\"")
    @ValueSource(strings = {"abc", "0", "-1"})
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Returns 400 for an invalid ID")
    void returns400ForInvalidId(String bookId) {
        Response response = library.returnBook(bookId);

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertViolationOn(problem, "bookId");
    }
}
