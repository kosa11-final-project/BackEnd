-- Store one cumulative forecast row per SKU, sales point, model, and base date.
-- Forecast values are intentionally initialized to zero and will be updated later.

-- Oracle commits DDL statement by statement. Add only missing columns so a
-- repaired failed migration can safely resume without raising ORA-01430.
DECLARE
    PROCEDURE add_column_if_missing(
        p_column_name       VARCHAR2,
        p_column_definition VARCHAR2
    ) IS
        v_column_count NUMBER;
    BEGIN
        SELECT COUNT(*)
        INTO v_column_count
        FROM user_tab_columns
        WHERE table_name = 'DEMAND_FORECAST'
          AND column_name = UPPER(p_column_name);

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE demand_forecast ADD ('
                || p_column_name || ' ' || p_column_definition || ')';
        END IF;
    END add_column_if_missing;
BEGIN
    add_column_if_missing('predicted_qty_d7', 'NUMBER(15,3) DEFAULT 0 NOT NULL');
    add_column_if_missing('predicted_qty_d14', 'NUMBER(15,3) DEFAULT 0 NOT NULL');
    add_column_if_missing('predicted_qty_d30', 'NUMBER(15,3) DEFAULT 0 NOT NULL');
    add_column_if_missing('predicted_qty_d60', 'NUMBER(15,3) DEFAULT 0 NOT NULL');
    add_column_if_missing('predicted_qty_d90', 'NUMBER(15,3) DEFAULT 0 NOT NULL');
END;
/

-- Existing data has one row per horizon. Preserve the smallest forecast_id as
-- the representative row so child foreign keys can remain valid.
-- A MERGE cannot update a target column referenced by its ON clause in Oracle
-- (ORA-38104), so use a correlated UPDATE instead.
UPDATE risk_assessment target
SET forecast_id = (
    SELECT MIN(group_member.forecast_id)
    FROM demand_forecast current_forecast
    JOIN demand_forecast group_member
      ON group_member.sku_id = current_forecast.sku_id
     AND group_member.sales_point_id = current_forecast.sales_point_id
     AND group_member.model_version_id = current_forecast.model_version_id
     AND group_member.base_date = current_forecast.base_date
    WHERE current_forecast.forecast_id = target.forecast_id
)
WHERE EXISTS (
    SELECT 1
    FROM demand_forecast current_forecast
    WHERE current_forecast.forecast_id = target.forecast_id
);

DELETE FROM demand_forecast target
WHERE target.forecast_id <> (
    SELECT MIN(source.forecast_id)
    FROM demand_forecast source
    WHERE source.sku_id = target.sku_id
      AND source.sales_point_id = target.sales_point_id
      AND source.model_version_id = target.model_version_id
      AND source.base_date = target.base_date
);

ALTER TABLE demand_forecast DROP (
    forecast_from,
    forecast_to,
    predicted_qty,
    method_type
);

ALTER TABLE demand_forecast ADD CONSTRAINT uq_demand_forecast_target
    UNIQUE (sku_id, sales_point_id, model_version_id, base_date);

ALTER TABLE demand_forecast ADD CONSTRAINT ck_demand_forecast_qty_nonnegative
    CHECK (
        predicted_qty_d7 >= 0
        AND predicted_qty_d14 >= 0
        AND predicted_qty_d30 >= 0
        AND predicted_qty_d60 >= 0
        AND predicted_qty_d90 >= 0
    );

ALTER TABLE demand_forecast ADD CONSTRAINT ck_demand_forecast_qty_cumulative
    CHECK (
        predicted_qty_d7 <= predicted_qty_d14
        AND predicted_qty_d14 <= predicted_qty_d30
        AND predicted_qty_d30 <= predicted_qty_d60
        AND predicted_qty_d60 <= predicted_qty_d90
    );

COMMENT ON COLUMN demand_forecast.predicted_qty_d7
    IS 'Cumulative predicted demand from D+1 through D+7';
COMMENT ON COLUMN demand_forecast.predicted_qty_d14
    IS 'Cumulative predicted demand from D+1 through D+14';
COMMENT ON COLUMN demand_forecast.predicted_qty_d30
    IS 'Cumulative predicted demand from D+1 through D+30';
COMMENT ON COLUMN demand_forecast.predicted_qty_d60
    IS 'Cumulative predicted demand from D+1 through D+60';
COMMENT ON COLUMN demand_forecast.predicted_qty_d90
    IS 'Cumulative predicted demand from D+1 through D+90';
