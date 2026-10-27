package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.ErrorCode;
import com.qic.library.tests.framework.support.Inventory;
import io.qameta.allure.Allure;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

@Feature("Borrow and return lifecycle")
@DisplayName("Borrow / return lifecycle")
class BookLifecycleTest extends BaseApiTest {

    @Test
    @Story("Full cycle")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(Inventory.LOCK)
    @DisplayName("Borrow every copy, fail to borrow more, return every copy, fail to return more")
    void borrowAndReturnEveryCopy() {
        Book original = books.withAllCopiesInLibrary();
        long id = original.id();
        int total = original.totalCopies();

        for (int copy = 1; copy <= total; copy++) {
            int expectedAvailable = total - copy;
            Allure.step("Borrow copy " + copy + " of " + total, () -> {
                Book book = library.borrow(id).borrowed().getFirst();
                assertThat(book.availableCopies()).isEqualTo(expectedAvailable);
            });
        }

        Allure.step("No copies left: borrowing fails and the book isn't listed as available", () -> {
            assertProblem(library.postBorrowId(id), 409, ErrorCode.NO_COPIES_AVAILABLE);
            assertThat(library.searchBooks(null, null, true)).extracting(Book::id).doesNotContain(id);
        });

        for (int copy = 1; copy <= total; copy++) {
            int expectedAvailable = copy;
            Allure.step("Return copy " + copy + " of " + total, () -> {
                Book book = library.returnCopy(id);
                assertThat(book.availableCopies()).isEqualTo(expectedAvailable);
                assertThat(library.searchBooks(null, null, true)).extracting(Book::id).contains(id);
            });
        }

        Allure.step("All copies are back: returning fails and the book matches its original state", () -> {
            assertProblem(library.returnBook(id), 409, ErrorCode.ALL_COPIES_RETURNED);
            assertThat(library.fetchBook(id)).isEqualTo(original);
        });
    }
}
