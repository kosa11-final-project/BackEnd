-- Seed the only role used by the first authentication release.
-- User-specific role assignment is intentionally handled by the shared setup SQL.

MERGE INTO app_role target
USING (
    SELECT system_user.user_id AS system_user_id,
           'GREENFOOD_ADMIN' AS role_code,
           '그린푸드 총괄' AS role_name
    FROM app_user system_user
    WHERE system_user.login_id = '__system__'
      AND system_user.is_deleted = 0
) source
ON (target.role_code = source.role_code)
WHEN MATCHED THEN
    UPDATE SET target.role_name = source.role_name,
               target.updated_at = SYSTIMESTAMP,
               target.updated_by = source.system_user_id,
               target.is_deleted = 0
WHEN NOT MATCHED THEN
    INSERT (
        role_code,
        role_name,
        created_at,
        updated_at,
        created_by,
        updated_by,
        is_deleted
    )
    VALUES (
        source.role_code,
        source.role_name,
        SYSTIMESTAMP,
        SYSTIMESTAMP,
        source.system_user_id,
        source.system_user_id,
        0
    );
