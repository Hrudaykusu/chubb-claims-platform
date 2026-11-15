
CREATE TABLE claims (
    id CHAR(36) PRIMARY KEY,
    claim_number VARCHAR(30) NOT NULL UNIQUE,
    claimant_name VARCHAR(100) NOT NULL,
    claimant_email VARCHAR(254) NOT NULL,
    type VARCHAR(20) NOT NULL,
    market VARCHAR(10) NOT NULL,
    incident_description VARCHAR(1000) NOT NULL,
    estimated_amount DECIMAL(15,2) NOT NULL,
    approved_amount DECIMAL(15,2),
    status VARCHAR(30) NOT NULL,
    assigned_officer VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_claim_status_market
    ON claims(status, market);

CREATE INDEX idx_claimant_email
    ON claims(claimant_email);

CREATE INDEX idx_claim_assignee
    ON claims(assigned_officer);
