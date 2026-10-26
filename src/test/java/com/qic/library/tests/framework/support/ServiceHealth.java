package com.qic.library.tests.framework.support;

import com.qic.library.tests.framework.config.TestConfig;
import io.restassured.RestAssured;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Checks once per run that the service is up, so a stopped service gives one clear error
 * instead of dozens of connection failures. Also writes the Allure environment section.
 */
public final class ServiceHealth {

    private static Boolean available;
    private static String failure;

    private ServiceHealth() {
    }

    public static synchronized void ensureAvailable() {
        if (available == null) {
            String url = TestConfig.baseUrl() + "/actuator/health";
            try {
                int status = RestAssured.given().get(url).statusCode();
                available = status == 200;
                failure = "GET " + url + " returned HTTP " + status;
            } catch (RuntimeException e) {
                available = false;
                failure = "GET " + url + " failed: " + e;
            }
            writeAllureEnvironment();
        }
        if (!available) {
            throw new IllegalStateException("Library API is not available at " + TestConfig.baseUrl()
                    + " (" + failure + "). Start the service or pass -DbaseUrl=<url>.");
        }
    }

    private static void writeAllureEnvironment() {
        Path dir = Path.of(System.getProperty("allure.results.directory", "target/allure-results"));
        Properties env = new Properties();
        env.setProperty("Base URL", TestConfig.baseUrl());
        env.setProperty("API base path", TestConfig.apiBasePath());
        env.setProperty("OpenAPI spec", TestConfig.specUrl());
        env.setProperty("Contract validation", String.valueOf(TestConfig.contractValidation()));
        env.setProperty("Java", System.getProperty("java.version"));
        env.setProperty("OS", System.getProperty("os.name"));
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(dir.resolve("environment.properties"), StandardCharsets.UTF_8)) {
                env.store(writer, "Allure environment");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
