package com.loyalty.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Schema(description = "Standard error envelope returned on API failures")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "400")
    private final int status;

    @Schema(description = "Short error code", example = "VALIDATION_ERROR")
    private final String error;

    @Schema(description = "Human-readable error message", example = "points must be at least 1")
    private final String message;

    @Schema(description = "Originating request path", example = "/api/wallet/earn")
    private final String path;

    @Schema(description = "UTC timestamp of the error")
    private final Instant timestamp;

    @Schema(description = "Correlation ID for tracing through logs", example = "req-abc123")
    private final String requestId;

    @Schema(description = "Field-level validation errors (populated for 400 responses)")
    private final List<FieldError> fieldErrors;

    @Getter
    @Builder
    @Schema(description = "Individual field validation failure")
    public static class FieldError {
        @Schema(example = "points")
        private final String field;
        @Schema(example = "must be at least 1")
        private final String message;
    }
}
