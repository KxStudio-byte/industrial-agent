-- =====================================================================
-- 质量中心 · 缺陷明细初始化数据（方案 A：按批次 defect_qty 拆分）
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h 192.168.100.128 -P 3306 \
--         -u root -p industrial_agent < quality_seed.sql
--
-- ★ 核心约束：每一个批次拆分出的 4 条记录 qty 之和
--           必须严格等于该批次的 prod_batch_record.defect_qty。
--   分配用「向下取整 + 余数补给最大项」，保证不丢不重。
--
-- ★ 不修改任何现有表的任何数据（只读 prod_batch_record / prod_plan）。
-- =====================================================================

SET @OLD_SQL_SAFE_UPDATES = @@SQL_SAFE_UPDATES;

-- 幂等：重复执行先清空本脚本产生的数据
DELETE FROM `prod_defect_record`;

-- ---------------------------------------------------------------------
-- 拆分规则（按产线工艺特性分配，合计必须 = 100%）
--   注塑线 A：外观划伤 42% / 尺寸超差 26% / 色差 22% / 装配漏装 10%
--   冲压线 B：尺寸超差 45% / 外观划伤 30% / 装配漏装 15% / 色差 10%
--   装配线 C：装配漏装 44% / 外观划伤 32% / 尺寸超差 16% / 色差 8%
-- ---------------------------------------------------------------------
INSERT INTO `prod_defect_record` (`batch_id`, `equipment_id`, `defect_type`, `qty`, `record_date`)
SELECT t.batch_id, t.equipment_id, t.defect_type, t.qty, t.plan_date
FROM (
  SELECT
    b.id            AS batch_id,
    b.equipment_id  AS equipment_id,
    p.plan_date     AS plan_date,
    dt.defect_type  AS defect_type,
    -- 前三项向下取整，第 4 项补余数，保证四项之和 = defect_qty
    CASE
      WHEN dt.rn < 4 THEN FLOOR(b.defect_qty * dt.ratio)
      ELSE b.defect_qty
           - FLOOR(b.defect_qty * 0.42)
           - FLOOR(b.defect_qty * 0.26)
           - FLOOR(b.defect_qty * 0.22)
    END             AS qty,
    dt.rn
  FROM `prod_batch_record` b
  JOIN `prod_plan` p        ON p.id = b.plan_id AND p.is_deleted = 0
  JOIN `base_equipment` e   ON e.id = b.equipment_id
  JOIN (
    SELECT 1 AS rn, '外观划伤' AS defect_type, 0.42 AS ratio UNION ALL
    SELECT 2, '尺寸超差', 0.26 UNION ALL
    SELECT 3, '色差',     0.22 UNION ALL
    SELECT 4, '装配漏装', 0.10
  ) dt ON 1 = 1
  WHERE b.is_deleted = 0
    AND b.defect_qty > 0
    AND e.line_id = 1        -- A产线（注塑）
) t
WHERE t.qty > 0
ORDER BY t.batch_id, t.rn;

-- 冲压线 B：尺寸超差 45% / 外观划伤 30% / 装配漏装 15% / 色差 10%
INSERT INTO `prod_defect_record` (`batch_id`, `equipment_id`, `defect_type`, `qty`, `record_date`)
SELECT t.batch_id, t.equipment_id, t.defect_type, t.qty, t.plan_date
FROM (
  SELECT
    b.id AS batch_id, b.equipment_id AS equipment_id, p.plan_date AS plan_date,
    dt.defect_type AS defect_type,
    CASE
      WHEN dt.rn < 4 THEN FLOOR(b.defect_qty * dt.ratio)
      ELSE b.defect_qty
           - FLOOR(b.defect_qty * 0.45) - FLOOR(b.defect_qty * 0.30) - FLOOR(b.defect_qty * 0.15)
    END AS qty, dt.rn
  FROM `prod_batch_record` b
  JOIN `prod_plan` p ON p.id = b.plan_id AND p.is_deleted = 0
  JOIN `base_equipment` e ON e.id = b.equipment_id
  JOIN (
    SELECT 1 AS rn, '尺寸超差' AS defect_type, 0.45 AS ratio UNION ALL
    SELECT 2, '外观划伤', 0.30 UNION ALL
    SELECT 3, '装配漏装', 0.15 UNION ALL
    SELECT 4, '色差',     0.10
  ) dt ON 1 = 1
  WHERE b.is_deleted = 0 AND b.defect_qty > 0 AND e.line_id = 2
) t
WHERE t.qty > 0
ORDER BY t.batch_id, t.rn;

-- 装配线 C：装配漏装 44% / 外观划伤 32% / 尺寸超差 16% / 色差 8%
INSERT INTO `prod_defect_record` (`batch_id`, `equipment_id`, `defect_type`, `qty`, `record_date`)
SELECT t.batch_id, t.equipment_id, t.defect_type, t.qty, t.plan_date
FROM (
  SELECT
    b.id AS batch_id, b.equipment_id AS equipment_id, p.plan_date AS plan_date,
    dt.defect_type AS defect_type,
    CASE
      WHEN dt.rn < 4 THEN FLOOR(b.defect_qty * dt.ratio)
      ELSE b.defect_qty
           - FLOOR(b.defect_qty * 0.44) - FLOOR(b.defect_qty * 0.32) - FLOOR(b.defect_qty * 0.16)
    END AS qty, dt.rn
  FROM `prod_batch_record` b
  JOIN `prod_plan` p ON p.id = b.plan_id AND p.is_deleted = 0
  JOIN `base_equipment` e ON e.id = b.equipment_id
  JOIN (
    SELECT 1 AS rn, '装配漏装' AS defect_type, 0.44 AS ratio UNION ALL
    SELECT 2, '外观划伤', 0.32 UNION ALL
    SELECT 3, '尺寸超差', 0.16 UNION ALL
    SELECT 4, '色差',     0.08
  ) dt ON 1 = 1
  WHERE b.is_deleted = 0 AND b.defect_qty > 0 AND e.line_id = 3
) t
WHERE t.qty > 0
ORDER BY t.batch_id, t.rn;

-- ---------------------------------------------------------------------
-- 客诉数据（本周 2026-10-26 ~ 2026-11-01）
-- ---------------------------------------------------------------------
DELETE FROM `prod_complaint_record`;

INSERT INTO `prod_complaint_record`
  (`complaint_no`, `product_name`, `line_id`, `defect_type`, `qty`, `content`, `status`, `occur_date`)
VALUES
  ('CPL-20261027-001', '外壳组件 A-220', 1, '外观划伤', 12,
   '客户反馈外壳侧面有肉眼可见划痕，要求全检并说明原因', '已关闭', '2026-10-27'),
  ('CPL-20261029-001', '结构件 B-18', 2, '尺寸超差', 5,
   '来料抽检发现安装孔位偏移 0.3mm，装配时无法对位', '已关闭', '2026-10-29'),
  ('CPL-20261031-001', '外壳组件 A-220', 1, '色差', 8,
   '同批产品存在明显色差分层，客户拒收', '处理中', '2026-10-31'),
  ('CPL-20261101-001', '总成 C-09', 3, '装配漏装', 3,
   '总成缺少紧固垫片，客户产线停线 40 分钟', '处理中', '2026-11-01');

SET SQL_SAFE_UPDATES = @OLD_SQL_SAFE_UPDATES;

-- ---------------------------------------------------------------------
-- 校验：拆分后每一项之和必须等于原批次 defect_qty（结果应全部相等）
-- ---------------------------------------------------------------------
SELECT
  COUNT(*)                                              AS 批次总数,
  SUM(CASE WHEN d.sum_qty = b.defect_qty THEN 1 ELSE 0 END) AS 对得上的批次,
  SUM(CASE WHEN d.sum_qty <> b.defect_qty THEN 1 ELSE 0 END) AS 对不上的批次,
  SUM(b.defect_qty)                                     AS 原不良总数,
  SUM(d.sum_qty)                                        AS 拆分不良总数
FROM `prod_batch_record` b
LEFT JOIN (
  SELECT batch_id, SUM(qty) AS sum_qty FROM `prod_defect_record` GROUP BY batch_id
) d ON d.batch_id = b.id
WHERE b.is_deleted = 0 AND b.defect_qty > 0;