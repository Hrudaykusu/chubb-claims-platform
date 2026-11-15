package com.chubb.claims.api;

import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.dto.ApproveClaimRequest;
import com.chubb.claims.dto.AssignClaimRequest;
import com.chubb.claims.dto.ClaimResponse;
import com.chubb.claims.dto.CreateClaimRequest;
import com.chubb.claims.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid JSON, input, query parameter or pagination"),
        @ApiResponse(responseCode = "404", description = "Claim or endpoint not found"),
        @ApiResponse(responseCode = "409", description = "Invalid claim state or conflicting data"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error; no internal details exposed")
})
public class ClaimController {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "claimNumber", "claimantName", "claimantEmail", "type", "market",
            "incidentDescription", "estimatedAmount", "approvedAmount", "status",
            "assignedOfficer", "createdAt", "updatedAt", "version");
    private static final int MAX_PAGE_SIZE = 100;

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @Operation(summary = "Submit a new motor or property claim")
    @PostMapping
    public ResponseEntity<ClaimResponse> create(@Valid @RequestBody CreateClaimRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "Track a claim by its number and claimant email")
    @GetMapping("/track")
    public ClaimResponse track(@RequestParam String claimNumber, @RequestParam String email) {
        if (claimNumber.isBlank() || email.isBlank()) {
            throw new IllegalArgumentException("claimNumber and email must not be blank");
        }
        return service.track(claimNumber.trim(), email.trim());
    }

    @Operation(summary = "Search claims; status searches require a market")
    @GetMapping
    public Page<ClaimResponse> search(@RequestParam(required = false) ClaimStatus status,
                                      @RequestParam(required = false) String market,
                                      @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                              direction = Sort.Direction.DESC) Pageable pageable,
                                      HttpServletRequest servletRequest) {
        validatePageable(pageable, servletRequest);
        if (status != null && (market == null || market.isBlank())) {
            throw new IllegalArgumentException("market is required when status is supplied");
        }
        if (market != null && !market.trim().matches("[A-Za-z]{2,10}")) {
            throw new IllegalArgumentException("market must contain 2 to 10 letters");
        }
        return service.search(status, market, pageable);
    }

    @Operation(summary = "Assign a submitted claim to an officer")
    @PutMapping("/{id}/assignment")
    public ClaimResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignClaimRequest request) {
        return service.assign(id, request.officerId());
    }

    @Operation(summary = "Start or resume review of an assigned claim")
    @PostMapping("/{id}/review")
    public ClaimResponse review(@PathVariable UUID id) {
        return service.startReview(id);
    }

    @Operation(summary = "Mark an under-review claim as requiring more information")
    @PostMapping("/{id}/information-request")
    public ClaimResponse information(@PathVariable UUID id) {
        return service.requestInformation(id);
    }

    @Operation(summary = "Approve an under-review claim with an amount up to its estimate")
    @PostMapping("/{id}/approve")
    public ClaimResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveClaimRequest request) {
        return service.approve(id, request.approvedAmount());
    }

    @Operation(summary = "Settle an approved claim")
    @PostMapping("/{id}/settle")
    public ClaimResponse settle(@PathVariable UUID id) {
        return service.settle(id);
    }

    @Operation(summary = "Reject a claim that is under review")
    @PostMapping("/{id}/reject")
    public ClaimResponse reject(@PathVariable UUID id) {
        return service.reject(id);
    }

    @Operation(summary = "Get active claims assigned to an officer")
    @GetMapping("/officer/{officerName}/workload")
    public Page<ClaimResponse> workload(@PathVariable String officerName,
                                        @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                direction = Sort.Direction.ASC) Pageable pageable,
                                        HttpServletRequest servletRequest) {
        validatePageable(pageable, servletRequest);
        if (officerName.isBlank()) {
            throw new IllegalArgumentException("officerName must not be blank");
        }
        return service.workload(officerName, pageable);
    }

    @Operation(summary = "Sum estimated amounts for active claims (demo only; no currency conversion)")
    @GetMapping("/management/outstanding-exposure")
    public BigDecimal exposure() {
        return service.exposure();
    }

    /** Fail fast on unknown JPA property names instead of returning a server error. */
    private static void validatePageable(Pageable pageable, HttpServletRequest request) {
        validatePaginationNumber(request.getParameter("page"), "page", Integer.MAX_VALUE, true);
        validatePaginationNumber(request.getParameter("size"), "size", MAX_PAGE_SIZE, false);
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be at most " + MAX_PAGE_SIZE);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Unsupported sort field '" + order.getProperty()
                        + "'. Allowed: " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
            }
        }
    }

    private static void validatePaginationNumber(String raw, String field, int maximum, boolean allowZero) {
        if (raw == null) {
            return;
        }
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < 0 || (!allowZero && parsed == 0) || parsed > maximum) {
                throw new NumberFormatException("outside allowed range");
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be "
                    + (allowZero ? "a non-negative" : "a positive") + " integer"
                    + (allowZero ? "" : " (maximum " + maximum + ")"));
        }
    }
}
