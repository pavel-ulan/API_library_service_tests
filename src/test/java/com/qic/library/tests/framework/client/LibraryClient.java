package com.qic.library.tests.framework.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.http.RequestSpecs;
import com.qic.library.tests.framework.model.Book;
import com.qic.library.tests.framework.model.BorrowRequest;
import com.qic.library.tests.framework.model.BorrowResponse;
import io.qameta.allure.Step;
import io.restassured.filter.Filter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client for the Library API.
 * <ul>
 *   <li>Methods returning {@link Response} send exactly what they're given and assert nothing:
 *       use them for the request under test, including invalid input.</li>
 *   <li>Typed methods ({@code listAllBooks}, {@code fetchBook}, {@code borrow}, {@code returnCopy}) expect
 *       {@code 200} and return the parsed body: use them for preconditions and follow-up checks.</li>
 * </ul>
 */
public class LibraryClient {

    private final RequestSpecification spec;

    public LibraryClient(Filter... extraFilters) {
        this.spec = RequestSpecs.api(extraFilters);
    }

    @Step("GET /books {query}")
    public Response listBooks(Map<String, ?> query) {
        return given().spec(spec)
                .queryParams(query)
                .when().get("/books");
    }

    @Step("GET /books/{bookId}")
    public Response getBook(Object bookId) {
        return given().spec(spec)
                .pathParam("bookId", bookId)
                .when().get("/books/{bookId}");
    }

    /** Sends {@code body} as-is if it's a String (for malformed JSON), otherwise serializes it. */
    @Step("POST /books/borrow {body}")
    public Response postBorrow(Object body) {
        String json = body instanceof String raw ? raw : Json.write(body);
        return given().spec(spec)
                .contentType(ContentType.JSON)
                .body(json)
                .when().post("/books/borrow");
    }

    public Response postBorrowIds(List<Long> bookIds) {
        return postBorrow(BorrowRequest.of(bookIds));
    }

    /** Named differently from {@link #postBorrow(Object)} so a Long never binds to the raw overload. */
    public Response postBorrowId(Long bookId) {
        return postBorrow(BorrowRequest.of(bookId));
    }

    @Step("POST /books/{bookId}/return")
    public Response returnBook(Object bookId) {
        return given().spec(spec)
                .pathParam("bookId", bookId)
                .when().post("/books/{bookId}/return");
    }

    public List<Book> searchBooks(String title, String author, Boolean available) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (title != null) {
            query.put("title", title);
        }
        if (author != null) {
            query.put("author", author);
        }
        if (available != null) {
            query.put("available", available);
        }
        return Json.read(expectOk(listBooks(query)), new TypeReference<List<Book>>() {
        });
    }

    public List<Book> listAllBooks() {
        return searchBooks(null, null, null);
    }

    public Book fetchBook(long bookId) {
        return Json.read(expectOk(getBook(bookId)), Book.class);
    }

    public BorrowResponse borrow(long bookId) {
        return Json.read(expectOk(postBorrowId(bookId)), BorrowResponse.class);
    }

    public BorrowResponse borrow(List<Long> bookIds) {
        return Json.read(expectOk(postBorrowIds(bookIds)), BorrowResponse.class);
    }

    public Book returnCopy(long bookId) {
        return Json.read(expectOk(returnBook(bookId)), Book.class);
    }

    private static Response expectOk(Response response) {
        assertThat(response.statusCode())
                .as("Expected HTTP 200, got %d: %s", response.statusCode(), response.asString())
                .isEqualTo(200);
        return response;
    }
}
