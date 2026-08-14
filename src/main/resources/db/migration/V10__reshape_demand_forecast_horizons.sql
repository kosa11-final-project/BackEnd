-- Store one cumulative forecast row per SKU, sales point, model, and base date.
-- Forecast values are intentionally initialized to zero and will be updated later.

ALTER TABLE demand_forecast ADD (
    predicted_qty_d7  NUMBER(15,3) DEFAULT 0 NOT NULL,
    predicted_qty_d14 NUMBER(15,3) DEFAULT 0 NOT NULL,
    predicted_qty_d30 NUMBER(15,3) DEFAULT 0 NOT NULL,
    predicted_qty_d60 NUMBER(15,3) DEFAULT 0 NOT NULL,
    predicted_qty_d90 NUMBER(15,3) DEFAULT 0 NOT NULL
);

-- Existing data has one row per horizon. Preserve the smallest forecast_id as
-- the representative row so child foreign keys can remain valid.
MERGE INTO risk_assessment target
USING (
    SELECT
        forecast_id AS old_forecast_id,
        MIN(forecast_id) OVER (
            PARTITION BY sku_id, sales_point_id, model_version_id, base_date
        ) AS representative_forecast_id
    FROM demand_forecast
) source
ON (target.forecast_id = source.old_forecast_id)
WHEN MATCHED THEN UPDATE SET
    target.forecast_id = source.representative_forecast_id;

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
