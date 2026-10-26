package com.qic.library.tests.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Test settings. Each value is resolved in this order:
 * system property ({@code -DbaseUrl=...}) → environment variable ({@code BASE_URL}) →
 * {@code config.properties} on the test classpath → built-in default.
 */
public final class TestConfig {

    private static final Properties FILE = load();

    private TestConfig() {
    }

    /** Root URL of the service, without a trailing slash, e.g. {@code http://localhost:8080}. */
    public static String baseUrl() {
        return stripTrailingSlash(get("baseUrl", "BASE_URL", "base.url", "http://localhost:8080"));
    }

    /** Base path of the API endpoints, e.g. {@code /api/v1}. */
    public static String apiBasePath() {
        return stripTrailingSlash(get("apiBasePath", "API_BASE_PATH", "api.base-path", "/api/v1"));
    }

    /** URL or file path of the OpenAPI spec used for contract validation. */
    public static String specUrl() {
        return get("specUrl", "SPEC_URL", "spec.url", baseUrl() + "/openapi/library-api.yaml");
    }

    public static boolean contractValidation() {
        return Boolean.parseBoolean(get("contractValidation", "CONTRACT_VALIDATION", "contract.validation", "true"));
    }

    private static String get(String systemProperty, String envVariable, String fileKey, String defaultValue) {
        String value = System.getProperty(systemProperty);
        if (isBlank(value)) {
            value = System.getenv(envVariable);
        }
        if (isBlank(value)) {
            value = FILE.getProperty(fileKey);
        }
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = TestConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config.properties", e);
        }
        return properties;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
