-- V24: Daily ML refresh uses the same durable sync pipeline as a manual button request.
ALTER TABLE inventory_sync_run DROP CONSTRAINT ck_isync_trigger;

ALTER TABLE inventory_sync_run ADD CONSTRAINT ck_isync_trigger
    CHECK (trigger_type IN ('MANUAL', 'SCHEDULED'));

-- V5 creates the technical audit principal disabled.  The scheduled trigger is
-- an internal, non-login execution path, so make that existing principal
-- explicitly active without creating or changing any business user.
UPDATE app_user
   SET active_yn = 'Y', updated_at = SYSTIMESTAMP
 WHERE login_id = '__system__'
   AND is_deleted = 0;
