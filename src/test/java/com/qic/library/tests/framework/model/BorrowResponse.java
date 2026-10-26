package com.qic.library.tests.framework.model;

import java.time.OffsetDateTime;

public record BorrowResponse(Book borrowed, OffsetDateTime borrowedAt) {
}
