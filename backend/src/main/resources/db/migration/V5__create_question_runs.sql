CREATE TABLE question_runs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_question_id VARCHAR(100) NOT NULL,
    question_text        TEXT NOT NULL,
    channel              VARCHAR(20) NOT NULL,
    status               VARCHAR(20) NOT NULL,
    query_plan_json      JSONB,
    answer_text          TEXT,
    error_code           VARCHAR(50),
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);