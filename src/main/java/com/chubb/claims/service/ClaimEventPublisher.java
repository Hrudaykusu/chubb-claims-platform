package com.chubb.claims.service;

import com.chubb.claims.dto.ClaimEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ClaimEventPublisher {

    public static final String TOPIC = "claims.lifecycle";

    private final KafkaTemplate<String, ClaimEvent> kafka;

    public ClaimEventPublisher(KafkaTemplate<String, ClaimEvent> kafka) {
        this.kafka = kafka;
    }

    public void publish(UUID id, String number, String event, String status, String market) {
        kafka.send(TOPIC, id.toString(), new ClaimEvent(id, number, event, status, market, Instant.now()));
    }
}
