-- Identify how each demand forecast was produced and expose its confidence.
-- Existing dummy forecast rows are preserved and classified as low confidence.

ALTER TABLE demand_forecast ADD (
    forecast_source  VARCHAR2(40),
    confidence_level VARCHAR2(10)
);

UPDATE demand_forecast
SET forecast_source = 'DUMMY_BASELINE',
    confidence_level = 'LOW';

ALTER TABLE demand_forecast MODIFY (
    forecast_source  NOT NULL,
    confidence_level NOT NULL
);

ALTER TABLE demand_forecast ADD CONSTRAINT ck_demand_forecast_source
    CHECK (forecast_source IN (
        'LIGHTGBM',
        'SAME_SKU_OTHER_POINT',
        'CATEGORY_SALES_POINT_MEDIAN',
        'CATEGORY_GLOBAL_MEDIAN',
        'MANUAL_INITIAL',
        'DUMMY_BASELINE'
    ));

ALTER TABLE demand_forecast ADD CONSTRAINT ck_demand_forecast_confidence
    CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW'));

COMMENT ON COLUMN demand_forecast.forecast_source
    IS 'Forecast origin: model, cold-start fallback, manual input, or existing dummy baseline';

COMMENT ON COLUMN demand_forecast.confidence_level
    IS 'Forecast confidence level: HIGH, MEDIUM, or LOW';
