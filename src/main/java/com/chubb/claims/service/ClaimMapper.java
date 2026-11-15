package com.chubb.claims.service;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.dto.ClaimResponse;
import org.springframework.stereotype.Component;

@Component
public class ClaimMapper {
    public ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(
                claim.getId(), claim.getClaimNumber(), claim.getClaimantName(), claim.getClaimantEmail(),
                claim.getType(), claim.getMarket(), claim.getIncidentDescription(), claim.getEstimatedAmount(),
                claim.getApprovedAmount(), claim.getStatus(), claim.getAssignedOfficer(),
                claim.getCreatedAt(), claim.getUpdatedAt());
    }
}
