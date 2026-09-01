ALTER TABLE question_runs
    ADD COLUMN request_id VARCHAR(100),
    ADD COLUMN started_at TIMESTAMPTZ;

UPDATE question_runs
SET request_id = id::text
WHERE request_id IS NULL;

ALTER TABLE question_runs
    ALTER COLUMN request_id SET NOT NULL;
