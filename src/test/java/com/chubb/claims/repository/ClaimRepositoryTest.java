
package com.chubb.claims.repository;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class ClaimRepositoryTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("claims_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername
        );

        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword
        );

        registry.add(
                "spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver"
        );
    }

    @Autowired
    private ClaimRepository repository;

    @Test
    void findsClaimByClaimNumberAndEmailIgnoringCase() {

        Claim claim = new Claim(
                "CHB-REPO01",
                "Test Claimant",
                "test@example.com",
                ClaimType.MOTOR,
                "SG",
                "Rear-end collision",
                new BigDecimal("900.00")
        );

        repository.saveAndFlush(claim);

        assertThat(
                repository.findByClaimNumberAndClaimantEmailIgnoreCase(
                        "CHB-REPO01",
                        "TEST@example.com"
                )
        ).isPresent();
    }

    @Test
    void calculatesExposureOnlyForRequestedStatuses() {

        Claim active = new Claim(
                "CHB-ACTIVE1",
                "Active",
                "active@example.com",
                ClaimType.PROPERTY,
                "AU",
                "Storm damage",
                new BigDecimal("1200.00")
        );

        repository.saveAndFlush(active);

        BigDecimal exposure =
                repository.sumEstimatedAmountByStatusIn(
                        List.of(
                                ClaimStatus.SUBMITTED,
                                ClaimStatus.ASSIGNED,
                                ClaimStatus.UNDER_REVIEW,
                                ClaimStatus.INFORMATION_REQUIRED,
                                ClaimStatus.APPROVED
                        )
                );

        assertThat(exposure)
                .isEqualByComparingTo("1200.00");
    }

    @Test
    void canFilterClaimsByMarket() {
        repository.saveAndFlush(new Claim("CHB-SG-01", "Singapore Claimant", "sg@example.com",
                ClaimType.MOTOR, "SG", "Vehicle damage", new BigDecimal("100.00")));
        repository.saveAndFlush(new Claim("CHB-AU-01", "Australian Claimant", "au@example.com",
                ClaimType.MOTOR, "AU", "Vehicle damage", new BigDecimal("200.00")));

        var results = repository.findByMarket("SG", org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getMarket()).isEqualTo("SG");
    }

    @Test
    void returnsZeroExposureWhenNoStatusesMatch() {

        BigDecimal exposure =
                repository.sumEstimatedAmountByStatusIn(
                        List.of(ClaimStatus.SETTLED)
                );

        assertThat(exposure)
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
