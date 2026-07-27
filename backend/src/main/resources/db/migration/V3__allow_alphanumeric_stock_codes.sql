ALTER TABLE companies
    DROP CONSTRAINT ck_companies_stock_code;

ALTER TABLE companies
    ADD CONSTRAINT ck_companies_stock_code
        CHECK (
            stock_code IS NULL
            OR stock_code ~ '^[0-9A-Z]{6}$'
        );