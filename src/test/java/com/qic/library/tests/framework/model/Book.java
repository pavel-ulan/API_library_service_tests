package com.qic.library.tests.framework.model;

/** A book as returned by the API. Boxed types so a missing field shows up as null in assertions. */
public record Book(
        Long id,
        String title,
        String author,
        String isbn,
        Integer publicationYear,
        Integer totalCopies,
        Integer availableCopies) {
}
