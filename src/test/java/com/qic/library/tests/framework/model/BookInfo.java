package com.qic.library.tests.framework.model;

/** Book details returned by GET /books/{bookId}; this representation has no author. */
public record BookInfo(
        Long id,
        String title,
        String isbn,
        Integer publicationYear,
        Integer totalCopies,
        Integer availableCopies) {

    public static BookInfo from(Book book) {
        return new BookInfo(
                book.id(),
                book.title(),
                book.isbn(),
                book.publicationYear(),
                book.totalCopies(),
                book.availableCopies());
    }
}
