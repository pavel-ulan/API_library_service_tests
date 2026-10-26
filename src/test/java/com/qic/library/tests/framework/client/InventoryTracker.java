package com.qic.library.tests.framework.client;

import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.BorrowResponse;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches successful borrow/return calls made by one test and remembers how many copies of
 * each book the test still holds, so they can be returned afterwards. Because it's a filter,
 * tests can't forget to register what they borrowed.
 */
public class InventoryTracker implements Filter {

    private static final Pattern RETURN_PATH = Pattern.compile(".*/books/(\\d+)/return$");

    private final Map<Long, Integer> outstanding = new ConcurrentHashMap<>();

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        Response response = context.next(request, responseSpec);
        if ("POST".equalsIgnoreCase(request.getMethod()) && response.statusCode() == 200) {
            String path = URI.create(request.getURI()).getPath();
            if (path.endsWith("/books/borrow")) {
                BorrowResponse body = Json.read(response.asString(), BorrowResponse.class);
                for (Book book : body.borrowed()) {
                    outstanding.merge(book.id(), 1, Integer::sum);
                }
            } else {
                Matcher matcher = RETURN_PATH.matcher(path);
                if (matcher.matches()) {
                    outstanding.computeIfPresent(Long.parseLong(matcher.group(1)),
                            (id, count) -> count > 1 ? count - 1 : null);
                }
            }
        }
        return response;
    }

    /** Snapshot: book ID → copies borrowed by the test and not yet returned. */
    public Map<Long, Integer> outstanding() {
        return Map.copyOf(outstanding);
    }
}
