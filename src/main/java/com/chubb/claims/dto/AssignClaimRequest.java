package com.chubb.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignClaimRequest(
        @NotBlank @Size(max = 100) String officerId
) {
}
