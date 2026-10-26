package com.qic.library.tests.framework.http;

import com.qic.library.tests.framework.config.TestConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.Filter;
import io.restassured.specification.RequestSpecification;

public final class RequestSpecs {

    /** Errors come back as application/problem+json, so both types are accepted. */
    private static final String ACCEPT = "application/json, application/problem+json";

    private RequestSpecs() {
    }

    /**
     * Spec for {@code /api/v1} endpoints. Filter order matters: contract validation is outermost so
     * the request/response is attached to Allure even when validation fails.
     */
    public static RequestSpecification api(Filter... extraFilters) {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(TestConfig.baseUrl())
                .setBasePath(TestConfig.apiBasePath())
                .setAccept(ACCEPT);
        if (TestConfig.contractValidation()) {
            builder.addFilter(ContractValidation.filter());
        }
        builder.addFilter(new AllureRestAssured());
        for (Filter filter : extraFilters) {
            builder.addFilter(filter);
        }
        return builder.build();
    }

    /** Spec for service endpoints outside the API contract (docs, actuator). */
    public static RequestSpecification root() {
        return new RequestSpecBuilder()
                .setBaseUri(TestConfig.baseUrl())
                .addFilter(new AllureRestAssured())
                .build();
    }
}
