CREATE TABLE companies (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    corp_code              VARCHAR(8) NOT NULL,
    stock_code             VARCHAR(6) NOT NULL,
    corp_name              VARCHAR(200) NOT NULL,
    listed_name            VARCHAR(200) NOT NULL,
    corp_eng_name          VARCHAR(300) NOT NULL,
    market                 VARCHAR(20) NOT NULL,
    industry               VARCHAR(100) NOT NULL,
    sector_no              SMALLINT NOT NULL,
    sector                 VARCHAR(100) NOT NULL,
    listing_date           DATE NOT NULL,
    fiscal_month           SMALLINT NOT NULL,
    market_cap             BIGINT NOT NULL,
    market_cap_as_of       DATE NOT NULL,
    listed                 BOOLEAN NOT NULL DEFAULT TRUE,
    source_provider        VARCHAR(30) NOT NULL,
    source_dataset_version VARCHAR(100) NOT NULL,
    note                   VARCHAR(1000),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_companies_corp_code
        UNIQUE (corp_code),
    CONSTRAINT uq_companies_stock_code
        UNIQUE (stock_code),
    CONSTRAINT ck_companies_corp_code
        CHECK (corp_code ~ '^[0-9]{8}$'),
    CONSTRAINT ck_companies_stock_code
        CHECK (stock_code ~ '^[0-9]{6}$'),
    CONSTRAINT ck_companies_corp_name_not_blank
        CHECK (char_length(btrim(corp_name)) > 0),
    CONSTRAINT ck_companies_listed_name_not_blank
        CHECK (char_length(btrim(listed_name)) > 0),
    CONSTRAINT ck_companies_corp_eng_name_not_blank
        CHECK (char_length(btrim(corp_eng_name)) > 0),
    CONSTRAINT ck_companies_market
        CHECK (market IN ('KOSPI', 'KOSDAQ')),
    CONSTRAINT ck_companies_industry_not_blank
        CHECK (char_length(btrim(industry)) > 0),
    CONSTRAINT ck_companies_sector_no
        CHECK (sector_no BETWEEN 1 AND 20),
    CONSTRAINT ck_companies_sector_not_blank
        CHECK (char_length(btrim(sector)) > 0),
    CONSTRAINT ck_companies_fiscal_month
        CHECK (fiscal_month BETWEEN 1 AND 12),
    CONSTRAINT ck_companies_market_cap
        CHECK (market_cap >= 0),
    CONSTRAINT ck_companies_source_provider
        CHECK (source_provider IN ('CONTEST')),
    CONSTRAINT ck_companies_source_dataset_version_not_blank
        CHECK (char_length(btrim(source_dataset_version)) > 0),
    CONSTRAINT ck_companies_note_not_blank
        CHECK (note IS NULL OR char_length(btrim(note)) > 0)
);

CREATE INDEX ix_companies_corp_name
    ON companies (corp_name);

CREATE INDEX ix_companies_listed_name
    ON companies (listed_name);

CREATE INDEX ix_companies_market_sector
    ON companies (market, sector_no);

COMMENT ON TABLE companies IS
    '대회 제공 기업 마스터를 기준으로 한 상장기업 식별 정보';

COMMENT ON COLUMN companies.corp_code IS
    'DART 기업 고유번호 8자리 문자열';

COMMENT ON COLUMN companies.stock_code IS
    '거래소 종목코드 6자리 문자열';

COMMENT ON COLUMN companies.market_cap IS
    'market_cap_as_of 시점의 시가총액, 단위 억원';

COMMENT ON COLUMN companies.market_cap_as_of IS
    '시가총액 기준일';

COMMENT ON COLUMN companies.source_provider IS
    '기업 정보 출처. 평가 DB에서는 CONTEST만 허용';

COMMENT ON COLUMN companies.source_dataset_version IS
    '기업 정보를 마지막으로 반영한 내부 데이터셋 버전';
