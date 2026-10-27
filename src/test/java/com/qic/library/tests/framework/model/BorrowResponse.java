package com.qic.library.tests.framework.model;

import java.time.OffsetDateTime;
import java.util.List;

public record BorrowResponse(List<Book> borrowed, OffsetDateTime borrowedAt) {
}
