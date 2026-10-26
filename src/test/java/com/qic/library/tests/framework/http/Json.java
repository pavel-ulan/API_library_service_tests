package com.qic.library.tests.framework.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.response.Response;

import java.io.UncheckedIOException;

/**
 * One Jackson mapper for request and response bodies, so mapping doesn't depend on how
 * REST Assured interprets content types such as {@code application/problem+json}.
 */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            // Unknown fields are the contract validator's job, not the mapper's
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T read(Response response, Class<T> type) {
        return read(response.asString(), type);
    }

    public static <T> T read(Response response, TypeReference<T> type) {
        try {
            return MAPPER.readValue(response.asString(), type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Cannot parse response body: " + response.asString(), e);
        }
    }

    public static <T> T read(String body, Class<T> type) {
        try {
            return MAPPER.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Cannot parse body: " + body, e);
        }
    }
}
