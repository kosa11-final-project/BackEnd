-- 기존 판매처별 SKU 가격에서 이관 시점의 SKU 기준 원가 한 건을 선정한다.
-- 현재 유효한 원가를 우선하고, 여러 값이 있으면 최빈값과 최신 수정값 순으로 결정한다.
-- 현재 유효한 가격이 없는 SKU는 가장 최근 과거 가격의 원가를 사용한다.
INSERT INTO sku_cost (
    sku_cost_id,
    sku_id,
    unit_cost,
    effective_from,
    effective_to,
    created_at,
    updated_at,
    created_by,
    updated_by,
    is_deleted
)
WITH source_rows AS (
    SELECT scp.sku_channel_price_id,
           scp.sku_id,
           scp.product_cost,
           scp.effective_from,
           scp.updated_at,
           CASE
               WHEN scp.effective_from <= TRUNC(CURRENT_DATE)
                AND (scp.effective_to IS NULL OR scp.effective_to >= TRUNC(CURRENT_DATE))
               THEN 1
               ELSE 0
           END AS active_row
    FROM sku_channel_price scp
    WHERE scp.is_deleted = 0
      AND scp.product_cost IS NOT NULL
      AND scp.effective_from <= TRUNC(CURRENT_DATE)
),
cost_candidates AS (
    SELECT sku_id,
           product_cost,
           SUM(active_row) AS active_count,
           MAX(CASE WHEN active_row = 1 THEN effective_from END) AS latest_active_from,
           MAX(effective_from) AS latest_effective_from,
           MAX(updated_at) AS latest_updated_at,
           MAX(sku_channel_price_id) AS latest_price_id
    FROM source_rows
    GROUP BY sku_id, product_cost
),
ranked_costs AS (
    SELECT sku_id,
           product_cost,
           ROW_NUMBER() OVER (
               PARTITION BY sku_id
               ORDER BY CASE WHEN active_count > 0 THEN 0 ELSE 1 END,
                        active_count DESC,
                        CASE
                            WHEN active_count > 0 THEN latest_active_from
                            ELSE latest_effective_from
                        END DESC,
                        latest_updated_at DESC,
                        latest_price_id DESC,
                        product_cost ASC
           ) AS cost_rank
    FROM cost_candidates
),
system_actor AS (
    SELECT MIN(user_id) AS user_id
    FROM app_user
    WHERE login_id = '__system__'
      AND is_deleted = 0
)
SELECT sku_cost_seq.NEXTVAL,
       ranked.sku_id,
       ranked.product_cost,
       TRUNC(CURRENT_DATE),
       NULL,
       SYSTIMESTAMP,
       SYSTIMESTAMP,
       actor.user_id,
       actor.user_id,
       0
FROM ranked_costs ranked
CROSS JOIN system_actor actor
WHERE ranked.cost_rank = 1;

-- sku_channel_price.product_cost는 애플리케이션 조회 전환과 검증이 끝날 때까지 유지한다.
