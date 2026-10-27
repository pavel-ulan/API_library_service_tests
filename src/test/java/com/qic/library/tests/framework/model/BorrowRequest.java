package com.qic.library.tests.framework.model;

import java.util.List;

public record BorrowRequest(List<Long> bookIds) {

    public static BorrowRequest of(Long bookId) {
        return new BorrowRequest(List.of(bookId));
    }

    public static BorrowRequest of(List<Long> bookIds) {
        return new BorrowRequest(bookIds);
    }
}
