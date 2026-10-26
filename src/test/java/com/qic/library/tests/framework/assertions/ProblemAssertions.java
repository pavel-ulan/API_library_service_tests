package com.qic.library.tests.framework.assertions;

import com.qic.library.tests.framework.http.Json;
import com.qic.library.tests.framework.model.ErrorCode;
import com.qic.library.tests.framework.model.Problem;
import com.qic.library.tests.framework.model.Violation;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks for RFC 7807 error responses. */
public final class ProblemAssertions {

    private ProblemAssertions() {
    }

    /**
     * Asserts status, {@code application/problem+json} content type and {@code errorCode};
     * returns the parsed problem for further checks.
     */
    @Step("Check problem response: HTTP {status}, {errorCode}")
    public static Problem assertProblem(Response response, int status, ErrorCode errorCode) {
        assertThat(response.statusCode())
                .as("HTTP status (body: %s)", response.asString())
                .isEqualTo(status);
        assertThat(response.contentType())
                .as("Content-Type")
                .startsWith("application/problem+json");

        Problem problem = Json.read(response, Problem.class);
        assertThat(problem.status()).as("problem.status").isEqualTo(status);
        assertThat(problem.title()).as("problem.title").isNotBlank();
        assertThat(problem.errorCode()).as("problem.errorCode").isEqualTo(errorCode);
        return problem;
    }

    @Step("Check problem.instance is {expectedPath}")
    public static void assertInstance(Problem problem, String expectedPath) {
        assertThat(problem.instance()).as("problem.instance").isEqualTo(expectedPath);
    }

    @Step("Check there is a violation for field {field}")
    public static void assertViolationOn(Problem problem, String field) {
        assertThat(problem.violations())
                .as("problem.violations")
                .isNotEmpty()
                .extracting(Violation::field)
                .contains(field);
        assertThat(problem.violations())
                .filteredOn(v -> field.equals(v.field()))
                .allSatisfy(v -> assertThat(v.message()).as("violation message").isNotBlank());
    }
}
