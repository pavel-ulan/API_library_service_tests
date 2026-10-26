package com.qic.library.tests.framework.model;

import java.util.List;

/** RFC 7807 problem details with the Library API extensions. */
public record Problem(
        String type,
        String title,
        Integer status,
        String detail,
        String instance,
        ErrorCode errorCode,
        List<Long> bookIds,
        List<Violation> violations) {
}
