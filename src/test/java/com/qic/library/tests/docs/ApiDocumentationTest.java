package com.qic.library.tests.docs;

import com.qic.library.tests.BaseApiTest;
import com.qic.library.tests.framework.http.RequestSpecs;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Feature("Service endpoints")
@DisplayName("Documentation and health endpoints")
class ApiDocumentationTest extends BaseApiTest {

    @Test
    @Story("OpenAPI spec")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Serves the hand-written OpenAPI spec")
    void servesOpenApiSpec() {
        Response response = given().spec(RequestSpecs.root()).get("/openapi/library-api.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString())
                .contains("openapi: 3.0.3")
                .contains("/books/borrow")
                .contains("/books/{bookId}/return");
    }

    @Test
    @Story("Swagger UI")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Serves Swagger UI")
    void servesSwaggerUi() {
        Response response = given().spec(RequestSpecs.root()).get("/swagger-ui.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).startsWith("text/html");
    }

    @Test
    @Story("Health")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Health endpoint reports UP")
    void healthIsUp() {
        Response response = given().spec(RequestSpecs.root()).get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
    }
}
