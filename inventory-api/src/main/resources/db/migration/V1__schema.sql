-- Inventory service schema. See specs/001-inventory-service/data-model.md.

CREATE TABLE product (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL UNIQUE CHECK (code = upper(code)),
    name          VARCHAR(200) NOT NULL,
    reorder_point INT          NOT NULL DEFAULT 0 CHECK (reorder_point >= 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE warehouse (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(40)  NOT NULL UNIQUE CHECK (code = upper(code)),
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Current stock: a projection of the movement ledger, updated in the same transaction.
-- The CHECKs are the last line of defence against overselling (constitution II).
CREATE TABLE stock_level (
    product_id   BIGINT      NOT NULL REFERENCES product (id),
    warehouse_id BIGINT      NOT NULL REFERENCES warehouse (id),
    on_hand      INT         NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
    reserved     INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, warehouse_id),
    CONSTRAINT reserved_within_on_hand CHECK (reserved <= on_hand)
);

CREATE TABLE hold (
    id              UUID         PRIMARY KEY,
    order_reference VARCHAR(100) NOT NULL,
    warehouse_id    BIGINT       NOT NULL REFERENCES warehouse (id),
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ
);
CREATE INDEX hold_active_expiry_idx ON hold (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE hold_line (
    hold_id    UUID   NOT NULL REFERENCES hold (id),
    product_id BIGINT NOT NULL REFERENCES product (id),
    quantity   INT    NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (hold_id, product_id)
);

-- Append-only ledger (constitution III).
CREATE TABLE movement (
    id         BIGSERIAL    PRIMARY KEY,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('RECEIVE', 'SHIP', 'ADJUST', 'TRANSFER')),
    product_id BIGINT       NOT NULL REFERENCES product (id),
    reason     VARCHAR(500),
    reference  VARCHAR(100),
    hold_id    UUID         REFERENCES hold (id),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX movement_product_idx ON movement (product_id, id DESC);

CREATE TABLE movement_line (
    id           BIGSERIAL PRIMARY KEY,
    movement_id  BIGINT    NOT NULL REFERENCES movement (id),
    warehouse_id BIGINT    NOT NULL REFERENCES warehouse (id),
    delta        INT       NOT NULL CHECK (delta <> 0)
);
CREATE INDEX movement_line_movement_idx ON movement_line (movement_id);

CREATE FUNCTION forbid_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'the movement ledger is append-only';
END $$;
CREATE TRIGGER movement_append_only BEFORE UPDATE OR DELETE ON movement FOR EACH ROW EXECUTE FUNCTION forbid_change();
CREATE TRIGGER movement_line_append_only BEFORE UPDATE OR DELETE ON movement_line FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE idempotency_record (
    client          VARCHAR(100) NOT NULL,
    key             VARCHAR(200) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    state           VARCHAR(12)  NOT NULL CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    response_status INT,
    response_type   VARCHAR(100),
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (client, key)
);
CREATE INDEX idempotency_created_idx ON idempotency_record (created_at);
