package com.qic.library.tests.framework.http;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import com.qic.library.tests.framework.config.TestConfig;

/**
 * Builds (once) a REST Assured filter that checks every API response against the service's
 * OpenAPI spec: status code is documented, content type matches, body matches the schema.
 * <p>
 * Request validation is switched off on purpose: negative tests send invalid requests deliberately.
 */
public final class ContractValidation {

    private static volatile OpenApiValidationFilter filter;

    private ContractValidation() {
    }

    public static OpenApiValidationFilter filter() {
        OpenApiValidationFilter result = filter;
        if (result == null) {
            synchronized (ContractValidation.class) {
                result = filter;
                if (result == null) {
                    filter = result = new OpenApiValidationFilter(createValidator());
                }
            }
        }
        return result;
    }

    private static OpenApiInteractionValidator createValidator() {
        return OpenApiInteractionValidator
                .createForSpecificationUrl(TestConfig.specUrl())
                .withBasePathOverride(TestConfig.apiBasePath())
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request", ValidationReport.Level.IGNORE)
                        .build())
                .build();
    }
}
