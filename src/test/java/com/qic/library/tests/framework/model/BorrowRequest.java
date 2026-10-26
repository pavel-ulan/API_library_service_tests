package com.qic.library.tests.framework.model;

import java.util.Arrays;
import java.util.List;

public record BorrowRequest(List<Long> bookIds) {

    public static BorrowRequest of(Long... bookIds) {
        return new BorrowRequest(Arrays.asList(bookIds));
    }
}
