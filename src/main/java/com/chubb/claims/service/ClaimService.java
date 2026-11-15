package com.chubb.claims.service;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.dto.ClaimResponse;
import com.chubb.claims.dto.CreateClaimRequest;
import com.chubb.claims.repository.ClaimRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ClaimService {

    private static final List<ClaimStatus> OPEN_STATUSES = List.of(
            ClaimStatus.SUBMITTED, ClaimStatus.ASSIGNED, ClaimStatus.UNDER_REVIEW,
            ClaimStatus.INFORMATION_REQUIRED, ClaimStatus.APPROVED);

    private final ClaimRepository repository;
    private final ClaimMapper mapper;
    private final ClaimEventPublisher events;

    public ClaimService(ClaimRepository repository, ClaimMapper mapper, ClaimEventPublisher events) {
        this.repository = repository;
        this.mapper = mapper;
        this.events = events;
    }

    public ClaimResponse create(CreateClaimRequest request) {
        String number;
        do {
            number = "CHB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (repository.existsByClaimNumber(number));

        Claim claim = repository.save(new Claim(
                number,
                request.claimantName().trim(),
                request.claimantEmail().trim(),
                request.type(),
                request.market().trim().toUpperCase(Locale.ROOT),
                request.incidentDescription().trim(),
                request.estimatedAmount()));
        events.publish(claim.getId(), number, "CLAIM_SUBMITTED", claim.getStatus().name(), claim.getMarket());
        return mapper.toResponse(claim);
    }

    @Transactional(readOnly = true)
    public ClaimResponse track(String number, String email) {
        return repository.findByClaimNumberAndClaimantEmailIgnoreCase(number, email)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ClaimNotFoundException(
                        "Claim not found for supplied claim number and email"));
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> search(ClaimStatus status, String market, Pageable pageable) {
        if (status != null && (market == null || market.isBlank())) {
            throw new IllegalArgumentException("market is required when status is supplied");
        }
        Page<Claim> results;
        if (market == null || market.isBlank()) {
            results = repository.findAll(pageable);
        } else {
            String normalizedMarket = market.trim().toUpperCase(Locale.ROOT);
            results = status == null
                    ? repository.findByMarket(normalizedMarket, pageable)
                    : repository.findByStatusAndMarket(status, normalizedMarket, pageable);
        }
        return results.map(mapper::toResponse);
    }

    public ClaimResponse assign(UUID id, String officer) {
        Claim claim = get(id);
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted claims can be assigned");
        }
        claim.assign(officer);
        return emit(claim, "CLAIM_ASSIGNED");
    }

    public ClaimResponse startReview(UUID id) {
        Claim claim = get(id);
        claim.startReview();
        return emit(claim, "REVIEW_STARTED");
    }

    public ClaimResponse requestInformation(UUID id) {
        Claim claim = get(id);
        claim.requestInformation();
        return emit(claim, "INFORMATION_REQUESTED");
    }

    public ClaimResponse approve(UUID id, BigDecimal amount) {
        Claim claim = get(id);
        claim.approve(amount);
        return emit(claim, "CLAIM_APPROVED");
    }

    public ClaimResponse settle(UUID id) {
        Claim claim = get(id);
        claim.settle();
        return emit(claim, "CLAIM_SETTLED");
    }

    public ClaimResponse reject(UUID id) {
        Claim claim = get(id);
        claim.reject();
        return emit(claim, "CLAIM_REJECTED");
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> workload(String officer, Pageable pageable) {
        return repository.findByAssignedOfficerAndStatusNotIn(officer,
                List.of(ClaimStatus.SETTLED, ClaimStatus.REJECTED), pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BigDecimal exposure() {
        // Assessment-only total: does not account for different market currencies.
        return repository.sumEstimatedAmountByStatusIn(OPEN_STATUSES);
    }

    private Claim get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException("Claim not found: " + id));
    }

    private ClaimResponse emit(Claim claim, String eventType) {
        // Direct publishing is intentionally kept simple. A production system needs a transactional outbox.
        events.publish(claim.getId(), claim.getClaimNumber(), eventType,
                claim.getStatus().name(), claim.getMarket());
        return mapper.toResponse(claim);
    }
}
