package com.qic.library.tests.books;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.model.Book;
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
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertProblem;
import static com.qic.library.tests.framework.assertions.ProblemAssertions.assertViolationOn;
import static org.assertj.core.api.Assertions.assertThat;

@Feature("Search books")
@DisplayName("GET /books — list and search")
class SearchBooksTest extends BaseApiTest {

    /** Ways to write the same search text; the server must treat them all alike. */
    enum QueryVariant {
        AS_IS(s -> s),
        LOWER_CASE(s -> s.toLowerCase(Locale.ROOT)),
        UPPER_CASE(s -> s.toUpperCase(Locale.ROOT)),
        SURROUNDED_BY_SPACES(s -> "  " + s + "  ");

        private final UnaryOperator<String> transform;

        QueryVariant(UnaryOperator<String> transform) {
            this.transform = transform;
        }

        String apply(String value) {
            return transform.apply(value);
        }
    }

    @Test
    @Story("List books")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Lists all books with valid fields and copy counters")
    void listsAllBooksWithValidCounters() {
        List<Book> all = library.listAllBooks();

        assertThat(all).as("books").isNotEmpty();
        assertThat(all).extracting(Book::id).as("book IDs").doesNotHaveDuplicates();
        assertThat(all).allSatisfy(book -> {
            assertThat(book.id()).as("id").isPositive();
            assertThat(book.title()).as("title").isNotBlank();
            assertThat(book.author()).as("author").isNotBlank();
            assertThat(book.totalCopies()).as("totalCopies of %s", book.id()).isGreaterThanOrEqualTo(1);
            assertThat(book.availableCopies()).as("availableCopies of %s", book.id())
                    .isBetween(0, book.totalCopies());
        });
    }

    @Test
    @Story("List books")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Sorts books by title, then author, ignoring case")
    void sortsByTitleThenAuthor() {
        List<Book> all = library.listAllBooks();

        assertThat(all).isSortedAccordingTo(Comparator
                .comparing(Book::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Book::author, String.CASE_INSENSITIVE_ORDER));
    }

    @ParameterizedTest(name = "title fragment written {0}")
    @EnumSource(QueryVariant.class)
    @Story("Search by title")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Finds books by part of the title, ignoring case and surrounding spaces")
    void searchesByPartialTitle(QueryVariant variant) {
        List<Book> all = library.listAllBooks();
        Book target = longest(all, Book::title);
        String fragment = middleOf(target.title());

        List<Book> found = library.searchBooks(variant.apply(fragment), null, null);

        assertThat(found).extracting(Book::id).as("target book").contains(target.id());
        assertThat(found).extracting(Book::id)
                .as("exactly the books whose title contains '%s', in list order", fragment)
                .containsExactlyElementsOf(idsMatching(all, Book::title, fragment));
    }

    @ParameterizedTest(name = "author fragment written {0}")
    @EnumSource(QueryVariant.class)
    @Story("Search by author")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Finds books by part of the author's name, ignoring case and surrounding spaces")
    void searchesByPartialAuthor(QueryVariant variant) {
        List<Book> all = library.listAllBooks();
        Book target = longest(all, Book::author);
        String fragment = middleOf(target.author());

        List<Book> found = library.searchBooks(null, variant.apply(fragment), null);

        assertThat(found).extracting(Book::id).as("target book").contains(target.id());
        assertThat(found).extracting(Book::id)
                .as("exactly the books whose author contains '%s', in list order", fragment)
                .containsExactlyElementsOf(idsMatching(all, Book::author, fragment));
    }

    @Test
    @Story("Search by title and author")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Combines title and author filters (both must match)")
    void combinesTitleAndAuthorFilters() {
        List<Book> all = library.listAllBooks();
        Book target = longest(all, Book::title);
        String titleFragment = middleOf(target.title());
        String authorFragment = middleOf(target.author());

        List<Book> found = library.searchBooks(titleFragment, authorFragment, null);

        List<Long> expected = all.stream()
                .filter(b -> containsIgnoreCase(b.title(), titleFragment))
                .filter(b -> containsIgnoreCase(b.author(), authorFragment))
                .map(Book::id)
                .toList();
        assertThat(found).extracting(Book::id).contains(target.id()).containsExactlyElementsOf(expected);
    }

    @Test
    @Story("Search by title and author")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Returns an empty list when nothing matches")
    void returnsEmptyListWhenNothingMatches() {
        List<Book> found = library.searchBooks("no-such-book-" + UUID.randomUUID(), null, null);

        assertThat(found).isEmpty();
    }

    @Test
    @Story("Filter by availability")
    @Severity(SeverityLevel.CRITICAL)
    @ResourceLock(value = Inventory.LOCK, mode = ResourceAccessMode.READ)
    @DisplayName("available=true returns exactly the books with at least one copy on the shelf")
    void availableTrueReturnsOnlyBooksWithCopies() {
        List<Book> all = library.listAllBooks();

        List<Book> available = library.searchBooks(null, null, true);

        assertThat(available).allSatisfy(b -> assertThat(b.availableCopies()).isPositive());
        assertThat(available).extracting(Book::id).containsExactlyElementsOf(
                all.stream().filter(b -> b.availableCopies() > 0).map(Book::id).toList());
    }

    @Test
    @Story("Filter by availability")
    @Severity(SeverityLevel.MINOR)
    @ResourceLock(value = Inventory.LOCK, mode = ResourceAccessMode.READ)
    @DisplayName("available=false applies no filter")
    void availableFalseAppliesNoFilter() {
        List<Book> all = library.listAllBooks();

        List<Book> found = library.searchBooks(null, null, false);

        assertThat(found).isEqualTo(all);
    }

    @ParameterizedTest(name = "{0} with 200 characters")
    @ValueSource(strings = {"title", "author"})
    @Story("Validation")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Accepts a filter of the maximum length (200)")
    void acceptsFilterOfMaximumLength(String parameter) {
        Response response = library.listBooks(Map.of(parameter, "x".repeat(200)));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @ParameterizedTest(name = "{0} with 201 characters")
    @ValueSource(strings = {"title", "author"})
    @Story("Validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Rejects a filter longer than 200 characters")
    void rejectsTooLongFilter(String parameter) {
        Response response = library.listBooks(Map.of(parameter, "x".repeat(201)));

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertViolationOn(problem, parameter);
    }

    @Test
    @Story("Validation")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Rejects a non-boolean 'available' value")
    void rejectsNonBooleanAvailable() {
        Response response = library.listBooks(Map.of("available", "maybe"));

        Problem problem = assertProblem(response, 400, ErrorCode.VALIDATION_ERROR);
        assertViolationOn(problem, "available");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static Book longest(List<Book> books, Function<Book, String> field) {
        return books.stream()
                .max(Comparator.comparingInt((Book b) -> field.apply(b).length()))
                .orElseThrow();
    }

    /** A fragment from the middle of the value, at least 3 characters, never padded with spaces. */
    private static String middleOf(String value) {
        int start = value.length() / 4;
        int length = Math.max(3, value.length() / 2);
        String fragment = value.substring(start, Math.min(value.length(), start + length)).strip();
        return fragment.isEmpty() ? value.strip() : fragment;
    }

    private static List<Long> idsMatching(List<Book> books, Function<Book, String> field, String fragment) {
        return books.stream().filter(b -> containsIgnoreCase(field.apply(b), fragment)).map(Book::id).toList();
    }

    private static boolean containsIgnoreCase(String value, String fragment) {
        return value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }
}
