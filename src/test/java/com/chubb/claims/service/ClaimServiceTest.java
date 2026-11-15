package com.chubb.claims.service;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.ClaimType;
import com.chubb.claims.dto.CreateClaimRequest;
import com.chubb.claims.repository.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock ClaimRepository repository;
    @Mock ClaimMapper mapper;
    @Mock ClaimEventPublisher events;
    @InjectMocks ClaimService service;

    @Test
    void createPersistsNormalizedInputAndPublishesEvent() {
        var request = new CreateClaimRequest(" Jane ", " jane@example.com ", ClaimType.MOTOR,
                "sg", " Accident ", new BigDecimal("500"));
        when(repository.existsByClaimNumber(anyString())).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        verify(repository).save(argThat(claim ->
                claim.getStatus() == ClaimStatus.SUBMITTED
                        && claim.getMarket().equals("SG")
                        && claim.getClaimantName().equals("Jane")
                        && claim.getClaimantEmail().equals("jane@example.com")
                        && claim.getIncidentDescription().equals("Accident")));
        verify(events).publish(any(), anyString(), eq("CLAIM_SUBMITTED"), eq("SUBMITTED"), eq("SG"));
    }

    @Test
    void trackingRequiresMatchingEmail() {
        when(repository.findByClaimNumberAndClaimantEmailIgnoreCase("CHB-1", "jane@example.com"))
                .thenReturn(Optional.empty());
        assertThrows(ClaimNotFoundException.class, () -> service.track("CHB-1", "jane@example.com"));
    }

    @Test
    void cannotAssignAlreadyAssignedClaim() {
        UUID id = UUID.randomUUID();
        Claim claim = exampleClaim();
        claim.assign("first");
        when(repository.findById(id)).thenReturn(Optional.of(claim));

        assertThrows(IllegalStateException.class, () -> service.assign(id, "second"));
        verifyNoInteractions(events);
    }

    @Test
    void filterByMarketWhenStatusIsAbsent() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findByMarket("SG", pageable)).thenReturn(Page.empty(pageable));

        service.search(null, " sg ", pageable);

        verify(repository).findByMarket("SG", pageable);
        verify(repository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void statusFilterRequiresMarketEvenIfCalledOutsideController() {
        assertThrows(IllegalArgumentException.class,
                () -> service.search(ClaimStatus.SUBMITTED, null, PageRequest.of(0, 20)));
        verifyNoInteractions(repository);
    }

    @Test
    void exposureExcludesClosedStatuses() {
        when(repository.sumEstimatedAmountByStatusIn(anyCollection())).thenReturn(new BigDecimal("1500"));

        assertEquals(new BigDecimal("1500"), service.exposure());
        verify(repository).sumEstimatedAmountByStatusIn(argThat(statuses ->
                statuses.containsAll(List.of(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED))
                        && !statuses.contains(ClaimStatus.SETTLED)
                        && !statuses.contains(ClaimStatus.REJECTED)));
    }

    private Claim exampleClaim() {
        return new Claim("CHB-1", "Jane", "jane@example.com", ClaimType.MOTOR,
                "SG", "Accident", new BigDecimal("500"));
    }
}
