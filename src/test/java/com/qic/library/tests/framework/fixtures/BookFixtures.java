package com.qic.library.tests.framework.fixtures;

import com.qic.library.tests.framework.client.LibraryClient;
import com.qic.library.tests.framework.model.Book;
import io.qameta.allure.Step;
import org.junit.jupiter.api.Assumptions;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds or prepares books in the state a test needs, using only the public API.
 * The service keeps state in memory and can't be reset, so tests never rely on specific seed data:
 * they pick suitable books at runtime. Copies borrowed here are returned after the test by
 * {@code BaseApiTest} (via the inventory tracker).
 */
public class BookFixtures {

    private final LibraryClient library;

    public BookFixtures(LibraryClient library) {
        this.library = library;
    }

    public Book anyBook() {
        return library.listAllBooks().stream()
                .findFirst()
                .orElseGet(() -> Assumptions.abort("The library has no books"));
    }

    @Step("Find a book with at least {minAvailable} available copies")
    public Book withAvailableCopies(int minAvailable, Long... excludeIds) {
        return find(book -> book.availableCopies() >= minAvailable, excludeIds)
                .orElseGet(() -> Assumptions.abort("No book with at least " + minAvailable + " available copies"));
    }

    @Step("Find {count} different books with available copies")
    public List<Book> differentAvailableBooks(int count) {
        List<Book> books = library.listAllBooks().stream()
                .filter(book -> book.availableCopies() > 0)
                .limit(count)
                .toList();
        if (books.size() < count) {
            Assumptions.abort("Need " + count + " available books, found " + books.size());
        }
        return books;
    }

    @Step("Find a book with all copies in the library")
    public Book withAllCopiesInLibrary(Long... excludeIds) {
        return library.listAllBooks().stream()
                .filter(notExcluded(excludeIds))
                .filter(book -> book.availableCopies().equals(book.totalCopies()))
                .min(Comparator.comparing(Book::totalCopies))
                .orElseGet(() -> Assumptions.abort("No book has all copies in the library"));
    }

    /** Reuses a book that already has no copies, otherwise borrows out every copy of one. */
    @Step("Prepare a book with no available copies")
    public Book withNoAvailableCopies(Long... excludeIds) {
        return find(book -> book.availableCopies() == 0, excludeIds)
                .orElseGet(() -> withExactlyAvailableCopies(0, excludeIds));
    }

    /** Picks the book closest to the target and borrows copies until exactly {@code available} remain. */
    @Step("Prepare a book with exactly {available} available copies")
    public Book withExactlyAvailableCopies(int available, Long... excludeIds) {
        Book book = library.listAllBooks().stream()
                .filter(notExcluded(excludeIds))
                .filter(b -> b.availableCopies() >= available)
                .min(Comparator.comparing(Book::availableCopies))
                .orElseGet(() -> Assumptions.abort("No book with at least " + available + " available copies"));
        Book current = book;
        for (int i = book.availableCopies(); i > available; i--) {
            current = library.borrow(book.id()).borrowed().getFirst();
        }
        return current;
    }

    /** An ID far above every existing one. */
    public long unknownBookId() {
        long maxId = library.listAllBooks().stream().mapToLong(Book::id).max().orElse(0);
        return maxId + 1_000_000;
    }

    private Optional<Book> find(Predicate<Book> condition, Long... excludeIds) {
        return library.listAllBooks().stream()
                .filter(notExcluded(excludeIds))
                .filter(condition)
                .findFirst();
    }

    private static Predicate<Book> notExcluded(Long... excludeIds) {
        Set<Long> excluded = new HashSet<>(Arrays.asList(excludeIds));
        return book -> !excluded.contains(book.id());
    }
}
