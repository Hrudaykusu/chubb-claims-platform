package com.chubb.claims.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ClaimTest {

    private Claim claim() {
        return new Claim("CHB-TEST0001", "A User", "a@example.com", ClaimType.MOTOR,
                "SG", "Collision", new BigDecimal("1000"));
    }

    @Test
    void validLifecycle() {
        Claim claim = claim();
        claim.assign("officer");
        claim.startReview();
        claim.approve(new BigDecimal("800"));
        claim.settle();
        assertEquals(ClaimStatus.SETTLED, claim.getStatus());
    }

    @Test
    void cannotSettleBeforeApproval() {
        assertThrows(IllegalStateException.class, () -> claim().settle());
    }

    @Test
    void cannotAssignTwiceEvenWhenCallingDomainDirectly() {
        Claim claim = claim();
        claim.assign("officer-one");
        assertThrows(IllegalStateException.class, () -> claim.assign("officer-two"));
    }

    @Test
    void officerIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> claim().assign("  "));
    }

    @Test
    void approvalCannotExceedEstimate() {
        Claim claim = claim();
        claim.assign("officer");
        claim.startReview();
        assertThrows(IllegalArgumentException.class, () -> claim.approve(new BigDecimal("1200")));
        assertThrows(IllegalArgumentException.class, () -> claim.approve(BigDecimal.ZERO));
    }

    @Test
    void informationRequestCanResumeReview() {
        Claim claim = claim();
        claim.assign("officer");
        claim.startReview();
        claim.requestInformation();
        claim.startReview();
        assertEquals(ClaimStatus.UNDER_REVIEW, claim.getStatus());
    }

    @Test
    void rejectedClaimCannotResumeReview() {
        Claim claim = claim();
        claim.assign("officer");
        claim.startReview();
        claim.reject();
        assertThrows(IllegalStateException.class, claim::startReview);
    }
}
