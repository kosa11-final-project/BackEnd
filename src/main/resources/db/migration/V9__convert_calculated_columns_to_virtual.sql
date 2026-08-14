-- Replace stored calculated values with Oracle virtual columns.
-- Existing values are intentionally discarded because they are derived from source columns.

ALTER TABLE inventory_balance
    DROP COLUMN total_qty;

ALTER TABLE inventory_balance
    ADD total_qty NUMBER(15,3)
        GENERATED ALWAYS AS (on_hand_qty + reserved_qty) VIRTUAL;

COMMENT ON COLUMN inventory_balance.total_qty IS
    'Virtual column: on_hand_qty + reserved_qty';

ALTER TABLE strategy_price_snapshot
    DROP COLUMN baseline_unit_contribution_margin;

ALTER TABLE strategy_price_snapshot
    ADD baseline_unit_contribution_margin NUMBER(18,2)
        GENERATED ALWAYS AS (current_price - unit_variable_cost) VIRTUAL;

COMMENT ON COLUMN strategy_price_snapshot.baseline_unit_contribution_margin IS
    'Virtual column: current_price - unit_variable_cost';
