-- Harbor & Pine support agent schema. Idempotent: safe to run on every start.

CREATE TABLE IF NOT EXISTS customer (
  id          SERIAL PRIMARY KEY,
  email       TEXT NOT NULL UNIQUE,
  full_name   TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS product (
  id          SERIAL PRIMARY KEY,
  sku         TEXT NOT NULL UNIQUE,
  name        TEXT NOT NULL,
  price_cents INT  NOT NULL CHECK (price_cents > 0)
);

CREATE TABLE IF NOT EXISTS shop_order (
  id            SERIAL PRIMARY KEY,
  order_number  TEXT NOT NULL UNIQUE,
  customer_id   INT  NOT NULL REFERENCES customer (id),
  status        TEXT NOT NULL CHECK (status IN ('processing', 'shipped', 'delivered', 'cancelled')),
  placed_at     TIMESTAMPTZ NOT NULL,
  shipped_at    TIMESTAMPTZ,
  delivered_at  TIMESTAMPTZ,
  carrier       TEXT,
  tracking_code TEXT,
  ship_to       TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS order_item (
  id          SERIAL PRIMARY KEY,
  order_id    INT NOT NULL REFERENCES shop_order (id),
  product_id  INT NOT NULL REFERENCES product (id),
  quantity    INT NOT NULL CHECK (quantity > 0),
  price_cents INT NOT NULL
);

CREATE TABLE IF NOT EXISTS help_article (
  id        SERIAL PRIMARY KEY,
  slug      TEXT NOT NULL UNIQUE,
  title     TEXT NOT NULL,
  body      TEXT NOT NULL,
  search    TSVECTOR GENERATED ALWAYS AS (
              setweight(to_tsvector('english', title), 'A') || setweight(to_tsvector('english', body), 'B')
            ) STORED
);
CREATE INDEX IF NOT EXISTS help_article_search_idx ON help_article USING GIN (search);

CREATE TABLE IF NOT EXISTS conversation (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id INT  NOT NULL REFERENCES customer (id),
  status      TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'escalated', 'closed')),
  -- Full Messages API history, appended exactly as returned (thinking blocks included).
  transcript  JSONB NOT NULL DEFAULT '[]'::jsonb,
  input_tokens  INT NOT NULL DEFAULT 0,
  output_tokens INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- What the agent did, in plain terms, for the staff console.
CREATE TABLE IF NOT EXISTS conversation_event (
  id              BIGSERIAL PRIMARY KEY,
  conversation_id UUID NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
  kind            TEXT NOT NULL CHECK (kind IN ('customer', 'agent', 'tool', 'error')),
  payload         JSONB NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS conversation_event_conv_idx ON conversation_event (conversation_id, id);

CREATE TABLE IF NOT EXISTS refund_request (
  id              SERIAL PRIMARY KEY,
  reference       TEXT NOT NULL UNIQUE,
  order_id        INT  NOT NULL REFERENCES shop_order (id),
  conversation_id UUID REFERENCES conversation (id),
  amount_cents    INT  NOT NULL CHECK (amount_cents > 0),
  reason          TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'pending_review' CHECK (status IN ('pending_review', 'approved', 'denied')),
  decided_by      TEXT,
  decided_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- One open or approved refund per order.
CREATE UNIQUE INDEX IF NOT EXISTS refund_one_per_order ON refund_request (order_id) WHERE status <> 'denied';

CREATE TABLE IF NOT EXISTS support_ticket (
  id              SERIAL PRIMARY KEY,
  reference       TEXT NOT NULL UNIQUE,
  conversation_id UUID NOT NULL REFERENCES conversation (id),
  customer_id     INT  NOT NULL REFERENCES customer (id),
  priority        TEXT NOT NULL CHECK (priority IN ('low', 'normal', 'urgent')),
  summary         TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'resolved')),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
