CREATE TABLE user_balance (
    user_id BIGINT  PRIMARY KEY,        -- manually assigned, no AUTO_INCREMENT — mirrors easyId
    points  BIGINT  NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0  -- mirrors point-interest-api-internal
);
