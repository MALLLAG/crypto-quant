-- 주문 테이블
CREATE TABLE orders (
    -- 기본 식별자
    id                  VARCHAR(36) PRIMARY KEY,

    -- 거래 정보
    pair                VARCHAR(20) NOT NULL,
    side                VARCHAR(10) NOT NULL,
    order_type          VARCHAR(20) NOT NULL,
    state               VARCHAR(20) NOT NULL,

    -- 주문 수량/가격 (주문 타입별로 다름)
    volume              DECIMAL(20, 8),      -- Limit, MarketSell, Best
    price               DECIMAL(20, 8),      -- Limit
    total_price         DECIMAL(20, 8),      -- MarketBuy

    -- 체결 정보
    remaining_volume    DECIMAL(20, 8) NOT NULL,
    executed_volume     DECIMAL(20, 8) NOT NULL,
    executed_amount     DECIMAL(20, 8) NOT NULL,
    paid_fee            DECIMAL(20, 8) NOT NULL,

    -- 시간 정보
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    done_at             TIMESTAMP WITH TIME ZONE,

    -- 제약 조건
    CONSTRAINT chk_side CHECK (side IN ('BID', 'ASK')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('LIMIT', 'MARKET_BUY', 'MARKET_SELL', 'BEST')),
    CONSTRAINT chk_state CHECK (state IN ('WAIT', 'WATCH', 'DONE', 'CANCEL'))
);

-- 인덱스
CREATE INDEX idx_orders_pair ON orders(pair);
CREATE INDEX idx_orders_state ON orders(state);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_pair_state ON orders(pair, state);
