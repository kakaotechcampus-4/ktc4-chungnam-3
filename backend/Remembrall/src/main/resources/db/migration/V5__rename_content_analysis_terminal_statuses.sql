ALTER TABLE content
    DROP CONSTRAINT ck_content_analysis_status;

UPDATE content
SET analysis_status = CASE analysis_status
    WHEN 'COMPLETED' THEN 'SUCCESS'
    WHEN 'PARTIAL_SUCCESS' THEN 'PARTIAL'
    ELSE analysis_status
END
WHERE analysis_status IN ('COMPLETED', 'PARTIAL_SUCCESS');

ALTER TABLE content
    ADD CONSTRAINT ck_content_analysis_status
        CHECK (analysis_status IN ('PENDING', 'ANALYZING', 'SUCCESS', 'PARTIAL', 'FAILED'));
