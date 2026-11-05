-- ============================================================
-- 能源中心种子数据
-- 目标库：industrial_agent
-- 幂等：先 DELETE 再 INSERT
--
-- 【口径说明】
--   电   ：kWh        —— 展示时除以 1000 显示为「千kWh」
--   水   ：t
--   气   ：Nm³       —— 展示时除以 1000 显示为「千Nm³」前的折算按标煤系数
--   蒸汽 ：t
--
-- 【标煤折算系数】（折算为 t标煤，用于「综合能耗」）
--   电   ：0.1229 kg标煤/kWh  -> 1 kWh = 0.0001229 t标煤
--   水   ：0.086  kg标煤/t
--   气   ：1.2143 kg标煤/Nm³
--   蒸汽 ：0.0928 t标煤/t
--
-- 【数据构造】
--   granularity='hour'    : 24 小时 x 4 介质 x 3 车间 x 33 天，用于分时电耗/白班夜班
--   granularity='day'     : 每车间每介质每日汇总
--   granularity='day_eq'  : 每设备每日汇总（只灌电与气，作为设备能耗排行的数据源）
--
-- 【日期范围】2026-10-04 ~ 2026-11-05（33 天，含今天）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 清理
-- ------------------------------------------------------------
DELETE FROM energy_meter;
DELETE FROM energy_saving_plan;

-- ------------------------------------------------------------
-- 建临时日历表
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_days;
CREATE TEMPORARY TABLE tmp_days (d DATE PRIMARY KEY);
INSERT INTO tmp_days (d)
WITH RECURSIVE seq AS (
  SELECT 0 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 32
)
SELECT DATE_ADD('2026-10-04', INTERVAL n DAY) FROM seq;

-- 车间 -> 产线映射（每车间对应一条产线）
-- phase / fluct / trend 让三个车间各自有独立的波动形状：
--   只给 base_share 的话，三车间曲线会严格等比，环比/偏离永远算成同一个数（假数据）。
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
-- 1) granularity='hour' —— 分时数据
--    base_kwh : 该车间当日电耗基准（kWh）
--    24 小时曲线形状参考原型：夜间低、8 点起爬升、22 点后回落，A车间夜间偏高
-- ------------------------------------------------------------
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  ws.workshop_id,
  ws.line_id,
  NULL,
  '电',
  ROUND(
      -- 当日各车间电耗总量（kWh）
      (48600 * ws.base_share)
      -- 日波动：每车间独立的相位 + 幅度，避免三车间曲线严格等比
      * (1 + ws.fluct * SIN(DATEDIFF(d.d, '2026-10-04') * 3.14159 / 16 + ws.phase))
      * (1 + ws.trend * DATEDIFF(d.d, '2026-10-04'))
      -- 小时分布权重（总和归一）
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
) h
WHERE 1 = 1;

-- 水 / 气 / 蒸汽 也按小时记录（量小，用于分时介质拆解）
-- 水：全天平稳，略高在换班时段
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  ws.workshop_id, ws.line_id, NULL, '水',
  ROUND((186 * ws.base_share) * (1 + 0.05 * SIN(DATEDIFF(d.d, '2026-10-04') * 3.14159 / 11)) / 24.0, 2),
  't', 'hour', d.d, h.hour
FROM tmp_days d CROSS JOIN tmp_ws ws CROSS JOIN (SELECT 0 AS hour UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23) h;

-- 气：白班高、夜班低（空压机负载特征）
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  ws.workshop_id, ws.line_id, NULL, '气',
  ROUND((920 * ws.base_share) * (1 + 0.05 * SIN(DATEDIFF(d.d, '2026-10-04') * 3.14159 / 13))
        * (CASE WHEN h.hour BETWEEN 8 AND 19 THEN 0.062 ELSE 0.025 END), 2),
  'Nm³', 'hour', d.d, h.hour
FROM tmp_days d CROSS JOIN tmp_ws ws CROSS JOIN (SELECT 0 AS hour UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23) h;

-- 蒸汽：只在白天有（C车间占比高）
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  ws.workshop_id, ws.line_id, NULL, '蒸汽',
  ROUND((14.2 * ws.base_share) * (1 + 0.04 * SIN(DATEDIFF(d.d, '2026-10-04') * 3.14159 / 9))
        * (CASE WHEN h.hour BETWEEN 7 AND 20 THEN 0.072 ELSE 0.0 END), 3),
  't', 'hour', d.d, h.hour
FROM tmp_days d CROSS JOIN tmp_ws ws CROSS JOIN (SELECT 0 AS hour UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23) h;

-- ------------------------------------------------------------
-- 2) granularity='day' —— 车间级日汇总（由 hour 数据聚合，保证口径一致）
-- ------------------------------------------------------------
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  workshop_id, line_id, NULL, medium, SUM(value), MIN(unit), 'day', record_date, NULL
FROM energy_meter
WHERE granularity = 'hour'
GROUP BY workshop_id, line_id, medium, record_date;

-- ------------------------------------------------------------
-- 3) granularity='day_eq' —— 设备级日明细（设备能耗排行数据源）
--    按设备类型给不同权重，权重总和归一后乘该车间当日电耗
-- ------------------------------------------------------------
INSERT INTO energy_meter (workshop_id, line_id, equipment_id, medium, value, unit, granularity, record_date, record_hour)
SELECT
  e.workshop_id_out,
  e.line_id,
  e.id,
  '电',
  ROUND(
    COALESCE(dayk.kwh, 0) *
    (e.type_weight / NULLIF(tot.wsum, 0))
  , 2),
  'kWh', 'day_eq', e.rec_date, NULL
FROM (
  SELECT
    be.id,
    be.line_id,
    be.equipment_type,
    d.d AS rec_date,
    l.workshop_id AS workshop_id_out,
    dm.value AS daykwh,
    -- 类型权重：动力/辅机（空压机、蒸汽发生器）高，检测/包装低
    CASE be.equipment_type
      WHEN '动力'   THEN 3.2
      WHEN '辅机'   THEN 2.9
      WHEN '注塑'   THEN 2.6
      WHEN '冲压'   THEN 2.4
      WHEN '焊接'   THEN 2.1
      WHEN '装配'   THEN 1.6
      WHEN '机器人' THEN 1.4
      WHEN '专用'   THEN 1.3
      WHEN '检测'   THEN 0.9
      WHEN '包装'   THEN 0.8
      WHEN '物流'   THEN 0.7
      ELSE 1.0
    END AS type_weight
  FROM base_equipment be
  JOIN base_production_line l ON l.id = be.line_id
  CROSS JOIN tmp_days d
  LEFT JOIN energy_meter dm
         ON dm.granularity = 'day'
        AND dm.medium = '电'
        AND dm.workshop_id = l.workshop_id
        AND dm.record_date = d.d
  WHERE be.is_deleted = 0
) e
JOIN (
  SELECT be2.line_id, SUM(
      CASE be2.equipment_type
        WHEN '动力' THEN 3.2 WHEN '辅机' THEN 2.9 WHEN '注塑' THEN 2.6
        WHEN '冲压' THEN 2.4 WHEN '焊接' THEN 2.1 WHEN '装配' THEN 1.6
        WHEN '机器人' THEN 1.4 WHEN '专用' THEN 1.3 WHEN '检测' THEN 0.9
        WHEN '包装' THEN 0.8 WHEN '物流' THEN 0.7 ELSE 1.0
      END) AS wsum
  FROM base_equipment be2
  WHERE be2.is_deleted = 0
  GROUP BY be2.line_id
) tot ON tot.line_id = e.line_id
LEFT JOIN (
  SELECT workshop_id, record_date, SUM(value) AS kwh
  FROM energy_meter
  WHERE granularity = 'day' AND medium = '电'
  GROUP BY workshop_id, record_date
) dayk ON dayk.workshop_id = e.workshop_id_out AND dayk.record_date = e.rec_date
WHERE e.daykwh IS NOT NULL;

-- ------------------------------------------------------------
-- 4) AI 节能优化方案（对应原型 5 条）
-- ------------------------------------------------------------
INSERT INTO energy_saving_plan
  (plan_code, plan_name, location, medium, status, saving, reason, advice, scope, result, exec_time, source)
VALUES
('EO-20260907-012', '关闭A车间2台低负载空压机', 'A车间 / 4号空压机', '电', '待执行', 28600.00,
 '夜间 22:00-06:00 电耗较昨日 +18.7%。4 台空压机中 2 台长期低于 35% 负载空转。',
 '关闭 2 台低负载空压机，保留 2 台联控带载；预计每月节约 ¥28,600。',
 'A车间供气压力维持 0.65 MPa，不影响白班注塑。',
 NULL, NULL, '能耗分析Agent'),

('EO-20260907-008', 'C车间蒸汽设定下调 2℃', 'C车间 / 蒸汽总阀', '蒸汽', '已完成', 9400.00,
 'C车间蒸汽设定温度 118℃，较工艺要求 116℃ 高 2℃，蒸汽单耗高于同类型产线 11%。',
 '蒸汽总阀设定值由 118℃ 下调至 116℃，观察 7 天产品质量无波动后固化。',
 'C车间全部装配工位，不影响注塑与冲压。',
 '负荷 -6.2%，当日已兑现 ¥312', '2026-09-07 11:20:00', '能耗分析Agent'),

('EO-20260906-021', '空压机联控避峰', 'A车间空压站', '电', '已完成', 16800.00,
 '峰段（08:00-11:00、18:00-21:00）空压机满载，避开峰段可降低电费单价。',
 '空压机联控在峰段降载 30%，储气罐压力维持在 0.7 MPa 以上。',
 'A车间空压站，全厂供气。',
 '夜班峰值下降 11%', '2026-09-06 22:18:00', '能耗分析Agent'),

('EO-20260906-014', '夜间照明分区关闭', 'B车间通道', '电', '已完成', 2100.00,
 'B车间通道照明 22:00-06:00 无人员作业但保持全亮，占该车间电耗 3.4%。',
 '通道照明按区域分三组，夜班仅保留安全通道组常亮，其余定时关闭。',
 'B车间通道与更衣区。',
 '照度达标，耗电 -8%', '2026-09-06 19:05:00', '能耗分析Agent'),

('EO-20260905-033', '冷却塔风机变频', '公用工程 / 冷却塔', '电', '执行失败', 5600.00,
 '冷却塔风机 3 台定频运行，水温低时仍全速，浪费约 12% 风机能耗。',
 '加装变频器，按出水温度 PID 调节风机转速。',
 '公用工程冷却塔组。',
 'PLC 通讯超时，指令未下发', '2026-09-05 16:40:00', '能耗分析Agent');

-- ------------------------------------------------------------
-- 5) 校验
-- ------------------------------------------------------------
SELECT 'energy_meter 总行数' AS item, COUNT(*) AS v FROM energy_meter
UNION ALL SELECT 'hour 行数', COUNT(*) FROM energy_meter WHERE granularity='hour'
UNION ALL SELECT 'day 行数', COUNT(*) FROM energy_meter WHERE granularity='day'
UNION ALL SELECT 'day_eq 行数', COUNT(*) FROM energy_meter WHERE granularity='day_eq'
UNION ALL SELECT '日期范围最小', COUNT(*) FROM (SELECT 1) t WHERE FALSE;

SELECT '日期区间' AS item, CONCAT(MIN(record_date), ' ~ ', MAX(record_date)) AS v FROM energy_meter;
SELECT '节能方案' AS item, COUNT(*) AS v FROM energy_saving_plan;

-- 介质汇总（今日，车间级日汇总口径）
SELECT dm.medium, ROUND(SUM(dm.value),2) AS total, dm.unit
FROM energy_meter dm
JOIN tmp_days d ON d.d = dm.record_date
WHERE dm.granularity='day' AND dm.record_date='2026-11-05'
GROUP BY dm.medium, dm.unit
ORDER BY dm.medium;

-- 车间汇总（今日，电）
SELECT w.workshop_name, ROUND(SUM(dm.value)/1000, 2) AS kwh_ku
FROM energy_meter dm JOIN sys_workshop w ON w.id=dm.workshop_id
WHERE dm.granularity='day' AND dm.medium='电' AND dm.record_date='2026-11-05'
GROUP BY w.workshop_name ORDER BY kwh_ku DESC;

DROP TEMPORARY TABLE IF EXISTS tmp_days;
DROP TEMPORARY TABLE IF EXISTS tmp_ws;