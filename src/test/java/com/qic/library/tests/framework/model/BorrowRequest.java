package com.qic.library.tests.framework.model;

public record BorrowRequest(Long bookId) {

    public static BorrowRequest of(Long bookId) {
        return new BorrowRequest(bookId);
    }
}
