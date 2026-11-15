package com.chubb.claims.dto;

import java.time.Instant;
import java.util.UUID;

public record ClaimEvent(
        UUID claimId,
        String claimNumber,
        String eventType,
        String status,
        String market,
        Instant occurredAt
) {
}
