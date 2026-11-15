package com.chubb.claims.repository;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Optional<Claim> findByClaimNumberAndClaimantEmailIgnoreCase(String claimNumber, String claimantEmail);

    Page<Claim> findByStatusAndMarket(ClaimStatus status, String market, Pageable pageable);

    Page<Claim> findByMarket(String market, Pageable pageable);

    Page<Claim> findByAssignedOfficerAndStatusNotIn(
            String officer, Collection<ClaimStatus> excluded, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(c.estimatedAmount), 0)
            FROM Claim c
            WHERE c.status IN :statuses
            """)
    BigDecimal sumEstimatedAmountByStatusIn(@Param("statuses") Collection<ClaimStatus> statuses);

    boolean existsByClaimNumber(String claimNumber);
}
