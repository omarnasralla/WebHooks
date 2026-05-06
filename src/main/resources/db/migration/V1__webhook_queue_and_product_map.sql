CREATE TABLE sync_job (
    id              BIGSERIAL PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR(512) NOT NULL UNIQUE,
    topic           VARCHAR(128) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error      TEXT,
    CONSTRAINT sync_job_status_chk CHECK (status IN
        ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD'))
);

CREATE INDEX idx_sync_job_poll ON sync_job (status, next_retry_at, created_at);

CREATE TABLE product_map (
    site_a_product_id   BIGINT       NOT NULL PRIMARY KEY,
    site_b_product_id   BIGINT       NOT NULL,
    sku                 VARCHAR(255),
    product_type        VARCHAR(32)  NOT NULL,
    site_a_parent_id    BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_map_sku ON product_map (sku);
CREATE INDEX idx_product_map_parent ON product_map (site_a_parent_id);
