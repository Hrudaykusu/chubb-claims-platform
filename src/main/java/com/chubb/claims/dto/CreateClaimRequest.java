package com.chubb.claims.dto;

import com.chubb.claims.domain.ClaimType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateClaimRequest(
        @NotBlank @Size(max = 100) String claimantName,
        @NotBlank @Email @Size(max = 254) String claimantEmail,
        @NotNull ClaimType type,
        @NotBlank @Pattern(regexp = "[a-zA-Z]{2,10}", message = "must contain 2 to 10 letters")
        String market,
        @NotBlank @Size(max = 1000) String incidentDescription,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero")
        @Digits(integer = 13, fraction = 2) BigDecimal estimatedAmount
) {
}
