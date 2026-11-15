package com.chubb.claims.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Consistent, safe error response for every public API endpoint. */
@Schema(description = "Standard API error response")
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path
) {
}
