package com.chubb.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Claim aggregate: lifecycle rules live here, independently of the REST controller. */
@Entity
@Table(name = "claims", indexes = {
        @Index(name = "idx_claim_status_market", columnList = "status,market"),
        @Index(name = "idx_claimant_email", columnList = "claimant_email"),
        @Index(name = "idx_claim_assignee", columnList = "assigned_officer")
})
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String claimNumber;
    @Column(nullable = false, length = 100)
    private String claimantName;
    @Column(nullable = false, length = 254)
    private String claimantEmail;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimType type;
    @Column(nullable = false, length = 10)
    private String market;
    @Column(nullable = false, length = 1000)
    private String incidentDescription;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedAmount;
    @Column(precision = 15, scale = 2)
    private BigDecimal approvedAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;
    @Column(length = 100)
    private String assignedOfficer;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Claim() {
        // Required by JPA.
    }

    public Claim(String number, String name, String email, ClaimType type, String market,
                 String description, BigDecimal estimate) {
        this.claimNumber = number;
        this.claimantName = name;
        this.claimantEmail = email;
        this.type = type;
        this.market = market;
        this.incidentDescription = description;
        this.estimatedAmount = estimate;
        this.status = ClaimStatus.SUBMITTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void assign(String officer) {
        require(ClaimStatus.SUBMITTED);
        if (officer == null || officer.isBlank()) {
            throw new IllegalArgumentException("Officer ID is required");
        }
        assignedOfficer = officer.trim();
        status = ClaimStatus.ASSIGNED;
        touch();
    }

    public void startReview() {
        require(ClaimStatus.ASSIGNED, ClaimStatus.INFORMATION_REQUIRED);
        status = ClaimStatus.UNDER_REVIEW;
        touch();
    }

    public void requestInformation() {
        require(ClaimStatus.UNDER_REVIEW);
        status = ClaimStatus.INFORMATION_REQUIRED;
        touch();
    }

    public void approve(BigDecimal amount) {
        require(ClaimStatus.UNDER_REVIEW);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(estimatedAmount) > 0) {
            throw new IllegalArgumentException(
                    "Approved amount must be positive and cannot exceed estimated amount");
        }
        approvedAmount = amount;
        status = ClaimStatus.APPROVED;
        touch();
    }

    public void settle() {
        require(ClaimStatus.APPROVED);
        status = ClaimStatus.SETTLED;
        touch();
    }

    public void reject() {
        require(ClaimStatus.UNDER_REVIEW);
        status = ClaimStatus.REJECTED;
        touch();
    }

    private void require(ClaimStatus... allowed) {
        for (ClaimStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Operation not allowed when claim status is " + status);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public String getClaimantName() { return claimantName; }
    public String getClaimantEmail() { return claimantEmail; }
    public ClaimType getType() { return type; }
    public String getMarket() { return market; }
    public String getIncidentDescription() { return incidentDescription; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public ClaimStatus getStatus() { return status; }
    public String getAssignedOfficer() { return assignedOfficer; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
