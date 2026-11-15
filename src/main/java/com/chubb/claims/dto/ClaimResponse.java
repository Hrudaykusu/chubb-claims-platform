package com.chubb.claims.dto;

import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        String claimNumber,
        String claimantName,
        String claimantEmail,
        ClaimType type,
        String market,
        String incidentDescription,
        BigDecimal estimatedAmount,
        BigDecimal approvedAmount,
        ClaimStatus status,
        String assignedOfficer,
        Instant createdAt,
        Instant updatedAt
) {
}
