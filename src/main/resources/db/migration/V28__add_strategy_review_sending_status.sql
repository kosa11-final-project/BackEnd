ALTER TABLE strategy_review_request
    DROP CONSTRAINT ck_review_request_status;

ALTER TABLE strategy_review_request
    ADD CONSTRAINT ck_review_request_status
        CHECK (
            review_status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')
        );

COMMENT ON COLUMN strategy_review_request.review_status
    IS 'Teams 전송 상태: PENDING, SENDING, SENT, FAILED';
