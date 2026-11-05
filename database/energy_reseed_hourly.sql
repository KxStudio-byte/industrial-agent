-- ============================================================
-- 修复：三车间电耗曲线严格等比
-- ------------------------------------------------------------
-- 问题：原 seed 里三车间共用同一个正弦扰动（相位/幅度相同），只有 base_share 不同，
-- 于是 A/B/C 的曲线是严格等比缩放，导致「车间环比 / 末值偏离日均」
-- 永远算出同一个数（如三个车间都 +3.2%），前端看起来像写死的假数据。
--
-- 修复思路（保持项目既定的三层口径，不破坏一致性）：
--   hour   —— 车间级小时明细，带每车间独立的 phase / fluct / trend
--   day    —— 由 hour 聚合而来（与 seed 第 131 行注释一致：'由 hour 数据聚合，保证口径一致'）
--   day_eq —— 由 day 按设备类型权重分摊而来（设备排行数据源）
--
-- 三层存在派生关系，所以必须按 hour -> day -> day_eq 顺序重建，
-- 不能只算 day（会导致 day ≠ Σhour，且 day_eq 与 day 脱钩）。
--
-- 重复执行安全（先 DELETE 再 INSERT）。只动 medium='电'。
-- ============================================================

DELETE FROM energy_meter
WHERE medium = '电' AND granularity IN ('hour', 'day', 'day_eq');

DROP TEMPORARY TABLE IF EXISTS tmp_days;
CREATE TEMPORARY TABLE tmp_days (d DATE PRIMARY KEY);
INSERT INTO tmp_days (d)
WITH RECURSIVE seq AS (
  SELECT 0 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 32
)
SELECT DATE_ADD('2026-10-04', INTERVAL n DAY) FROM seq;

-- 每车间独立的波动参数，避免三车间曲线严格等比
DROP TEMPORARY TABLE IF EXISTS tmp_ws;
CREATE TEMPORARY TABLE tmp_ws (
  workshop_id BIGINT PRIMARY KEY,
  line_id     BIGINT NOT NULL,
  ws_name     VARCHAR(20) NOT NULL,
  base_share  DECIMAL(6,4) NOT NULL,
  phase       DECIMAL(6,2) NOT NULL,
  fluct       DECIMAL(6,4) NOT NULL,
  trend       DECIMAL(8,5) NOT NULL
);
INSERT INTO tmp_ws VALUES
  (1, 1, 'A车间', 0.44,  0.00, 0.0600,  0.00260),
  (2, 2, 'B车间', 0.31,  2.10, 0.0350,  0.00090),
  (3, 3, 'C车间', 0.25,  4.20, 0.0850, -0.00120);

-- ------------------------------------------------------------
-- 1) hour：车间级小时明细（各车间独立波形）
--    日总量 = 48600 * base_share * 波形；小时权重合计归一
-- ------------------------------------------------------------
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  ws.workshop_id,
  ws.line_id,
  NULL,
  '电',
  ROUND(
      (48600 * ws.base_share)
      * (1 + ws.fluct * SIN(DATEDIFF(d.d, '2026-10-04') * 3.14159 / 16 + ws.phase))
      * (1 + ws.trend * DATEDIFF(d.d, '2026-10-04'))
      * (h.weight / 100.0)
  , 2),
  'kWh',
  'hour',
  d.d,
  h.hour
FROM tmp_days d
CROSS JOIN tmp_ws ws
CROSS JOIN (
  SELECT 0 AS hour, 2.40 AS weight UNION ALL SELECT 1, 2.20 UNION ALL SELECT 2, 2.10
  UNION ALL SELECT 3, 2.05 UNION ALL SELECT 4, 2.00 UNION ALL SELECT 5, 2.30
  UNION ALL SELECT 6, 3.20 UNION ALL SELECT 7, 4.30 UNION ALL SELECT 8, 5.20
  UNION ALL SELECT 9, 4.90 UNION ALL SELECT 10, 4.60 UNION ALL SELECT 11, 4.30
  UNION ALL SELECT 12, 4.10 UNION ALL SELECT 13, 4.50 UNION ALL SELECT 14, 4.80
  UNION ALL SELECT 15, 4.70 UNION ALL SELECT 16, 5.10 UNION ALL SELECT 17, 4.90
  UNION ALL SELECT 18, 4.60 UNION ALL SELECT 19, 4.40 UNION ALL SELECT 20, 4.20
  UNION ALL SELECT 21, 3.90 UNION ALL SELECT 22, 3.40 UNION ALL SELECT 23, 2.80
) h;

-- ------------------------------------------------------------
-- 2) day：由 hour 聚合（口径一致的唯一真值层）
-- ------------------------------------------------------------
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  workshop_id, line_id, NULL, '电', ROUND(SUM(value), 2), 'kWh', 'day', record_date, NULL
FROM energy_meter
WHERE medium = '电' AND granularity = 'hour'
GROUP BY workshop_id, line_id, record_date;

-- ------------------------------------------------------------
-- 3) day_eq：设备级日明细，按设备类型权重分摊该车间当日电耗
-- ------------------------------------------------------------
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  l.workshop_id,
  be.line_id,
  be.id,
  '电',
  ROUND(
    COALESCE(dayk.kwh, 0) * (tw.type_weight / NULLIF(tw.wsum, 0))
  , 2),
  'kWh',
  'day_eq',
  d.d,
  NULL
FROM base_equipment be
JOIN base_production_line l ON l.id = be.line_id
CROSS JOIN tmp_days d
JOIN (
  SELECT e_id, type_weight, SUM(type_weight) OVER (PARTITION BY line_id) AS wsum
  FROM (
    SELECT be2.id AS e_id, be2.line_id,
      CASE be2.equipment_type
        WHEN '动力' THEN 3.2 WHEN '辅机' THEN 2.9 WHEN '注塑' THEN 2.6
        WHEN '冲压' THEN 2.4 WHEN '焊接' THEN 2.1 WHEN '装配' THEN 1.6
        WHEN '机器人' THEN 1.4 WHEN '专用' THEN 1.3 WHEN '检测' THEN 0.9
        WHEN '包装' THEN 0.8 WHEN '物流' THEN 0.7 ELSE 1.0
      END AS type_weight
    FROM base_equipment be2
    WHERE be2.is_deleted = 0
  ) w
) tw ON tw.e_id = be.id
LEFT JOIN (
  SELECT workshop_id, record_date, SUM(value) AS kwh
  FROM energy_meter
  WHERE granularity = 'day' AND medium = '电'
  GROUP BY workshop_id, record_date
) dayk ON dayk.workshop_id = l.workshop_id AND dayk.record_date = d.d
WHERE be.is_deleted = 0;

-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
-- 校验 1：三车间「末值相对日均」的偏离必须各不相同
SELECT workshop_id,
       ROUND(AVG(s.daily), 1) AS avg_kwh,
       ROUND(MAX(CASE WHEN s.rn = 1 THEN s.daily END), 1) AS latest_kwh,
       ROUND((MAX(CASE WHEN s.rn = 1 THEN s.daily END) - AVG(s.daily)) / AVG(s.daily) * 100, 2) AS deviation_pct
FROM (
  SELECT workshop_id, record_date, SUM(value) AS daily,
         ROW_NUMBER() OVER (PARTITION BY workshop_id ORDER BY record_date DESC) AS rn
  FROM energy_meter
  WHERE medium = '电' AND granularity = 'day'
  GROUP BY workshop_id, record_date
) s
GROUP BY workshop_id
ORDER BY workshop_id;

-- 校验 2：day 与 Σhour 必须完全相等（口径一致）
SELECT d.workshop_id, d.record_date,
       ROUND(d.day_kwh, 1) AS day_kwh,
       ROUND(h.hour_kwh, 1) AS hour_sum_kwh,
       ROUND(d.day_kwh - h.hour_kwh, 2) AS diff
FROM (SELECT workshop_id, record_date, SUM(value) day_kwh FROM energy_meter
      WHERE medium = '电' AND granularity = 'day' GROUP BY workshop_id, record_date) d
JOIN (SELECT workshop_id, record_date, SUM(value) hour_kwh FROM energy_meter
      WHERE medium = '电' AND granularity = 'hour' GROUP BY workshop_id, record_date) h
  ON h.workshop_id = d.workshop_id AND h.record_date = d.record_date
WHERE ABS(d.day_kwh - h.hour_kwh) > 0.01
ORDER BY d.workshop_id, d.record_date;

-- 校验 3：day_eq 合计应等于 day（按车间/日期）
SELECT g.workshop_id, g.record_date,
       ROUND(g.day_eq_kwh, 1) AS day_eq_sum,
       ROUND(g.day_kwh, 1) AS day_kwh,
       ROUND(g.day_eq_kwh - g.day_kwh, 1) AS diff
FROM (
  SELECT workshop_id, record_date,
         SUM(CASE WHEN granularity = 'day_eq' THEN value ELSE 0 END) AS day_eq_kwh,
         SUM(CASE WHEN granularity = 'day' THEN value ELSE 0 END) AS day_kwh
  FROM energy_meter
  WHERE medium = '电' AND granularity IN ('day', 'day_eq')
  GROUP BY workshop_id, record_date
) g
WHERE ABS(g.day_eq_kwh - g.day_kwh) > 1
ORDER BY g.workshop_id, g.record_date
LIMIT 10;

DROP TEMPORARY TABLE IF EXISTS tmp_days;
DROP TEMPORARY TABLE IF EXISTS tmp_ws;