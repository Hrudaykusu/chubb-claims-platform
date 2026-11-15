package com.chubb.claims.service;

import com.chubb.claims.dto.ClaimEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClaimEventPublisherTest {

    @Mock KafkaTemplate<String, ClaimEvent> kafka;

    @Test
    void publishesClaimIdAsKafkaKeyAndCorrectEventData() {
        UUID id = UUID.randomUUID();
        new ClaimEventPublisher(kafka).publish(id, "CHB-123", "CLAIM_SUBMITTED", "SUBMITTED", "SG");

        verify(kafka).send(eq(ClaimEventPublisher.TOPIC), eq(id.toString()), argThat(event ->
                id.equals(event.claimId())
                        && "CHB-123".equals(event.claimNumber())
                        && "CLAIM_SUBMITTED".equals(event.eventType())
                        && "SUBMITTED".equals(event.status())
                        && "SG".equals(event.market())
                        && event.occurredAt() != null));
    }
}
