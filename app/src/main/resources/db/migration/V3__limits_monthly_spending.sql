-- Сумма транзакции в USD по курсу на момент операции.
-- Нужна, чтобы считать расходы за месяц одним агрегирующим запросом
ALTER TABLE transactions
    ADD COLUMN sum_usd DECIMAL(19,6) NOT NULL DEFAULT 0 AFTER sum;

-- DECIMAL без точности в MySQL = DECIMAL(10,0), из-за этого обрезались центы
ALTER TABLE limits
    MODIFY limit_remainder DECIMAL(19,6) NOT NULL;

ALTER TABLE transactions
    MODIFY limit_sum_at_time DECIMAL(15,2) NOT NULL;

CREATE INDEX idx_account_from_category_time ON transactions (account_from, expense_category, datetime);
