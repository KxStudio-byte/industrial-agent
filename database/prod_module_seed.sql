-- =====================================================================
-- 生产运营模块 · 种子数据
-- 目标库：industrial_agent
--
-- 设计原则
--   1. 只写真实业务表 prod_plan / prod_batch_record / prod_oee_loss，
--      不建任何「演示用」的影子表。
--   2. 所有派生指标（开动率/性能/质量/OEE/达成率）都不落库，
--      由后端按 prod_* 三表实时计算 —— 页面上的每个数字都能用 SQL 复算出来。
--   3. 日期以 CURDATE() 为基准往前推 30 天，所以无论哪天执行，
--      「今日/昨日」永远有数据，环比不会空。
--   4. 使用固定种子（CRC32 + 日期），结果可重现；重跑前先清空三张表。
--
-- 可重复执行：脚本开头 TRUNCATE 三张业务表。
-- =====================================================================

SET NAMES utf8mb4;
SET SESSION group_concat_max_len = 1000000;

TRUNCATE TABLE prod_oee_loss;
TRUNCATE TABLE prod_batch_record;
TRUNCATE TABLE prod_plan;

-- ---------------------------------------------------------------------
-- 1. 生产计划：30 天 × 3 产线 × 1 班 = 90 条
--    每产线固定产品与节拍：
--      A产线 外壳组件 A-220  节拍 520 件/时  白班 12h
--      B产线 结构件 B-18     节拍 430 件/时  白班 12h
--      C产线 总成 C-09       节拍 360 件/时  白班 12h
-- ---------------------------------------------------------------------
INSERT INTO prod_plan
    (plan_date, line_id, product_name, target_qty, actual_qty,
     plan_hours, standard_qty_per_hour, shift_name, status, ai_explanation)
SELECT
    d.d,
    l.id,
    l.product_name,
    l.target_qty,
    NULL,
    l.plan_hours,
    l.standard_qty_per_hour,
    '白班',
    '待执行',
    NULL
FROM (
    -- 生成 0..29 天
    WITH RECURSIVE seq(n) AS (
        SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 29
    )
    SELECT DATE_SUB(CURDATE(), INTERVAL n DAY) AS d FROM seq
) d
CROSS JOIN (
    SELECT 1 AS id, '外壳组件 A-220' AS product_name, 6240 AS target_qty, 12.0 AS plan_hours, 520 AS standard_qty_per_hour
    UNION ALL SELECT 2, '结构件 B-18', 5160, 12.0, 430
    UNION ALL SELECT 3, '总成 C-09',    4320, 12.0, 360
) l;


-- ---------------------------------------------------------------------
-- 2. 每条计划的停机分钟与「扣停机后的可用产能」
--    OEE 的性能效率分母是「可用产能 = 节拍 × (计划工时 - 停机工时)」，
--    所以产量必须先知道停机多少才能生成 —— 否则产出按计划量生成、
--    分母却被停机削掉一块，性能效率必然算出 >100%。
--    停机占比按产线给固定基础值 + 日波动：
--       A 产线 3%~7%（设备稳定）  B 产线 7%~12%（换型频繁）  C 产线 4%~9%
-- ---------------------------------------------------------------------
-- 用一张真实中间表而不是 TEMPORARY TABLE：
-- MySQL 的临时表在同一条语句里被引用两次会报 `ERROR 1137 Can't reopen table`，
-- 而下面的 INSERT 需要用到它的两列，所以必须是可重复打开的普通表。脚本末尾会 DROP。
DROP TABLE IF EXISTS tmp_cap;
CREATE TABLE tmp_cap (
    id BIGINT PRIMARY KEY,
    line_id BIGINT,
    plan_date DATE,
    plan_min INT,
    down_min INT,
    avail_cap DECIMAL(12,2)
);
INSERT INTO tmp_cap (id, line_id, plan_date, plan_min, down_min, avail_cap)
SELECT
    p.id,
    p.line_id,
    p.plan_date,
    p.plan_hours * 60 AS plan_min,
    ROUND(p.plan_hours * 60 * CASE p.line_id
        WHEN 1 THEN 0.03 + (CRC32(CONCAT(p.plan_date, '-A-dt')) % 5) / 100.0
        WHEN 2 THEN 0.07 + (CRC32(CONCAT(p.plan_date, '-B-dt')) % 6) / 100.0
        ELSE        0.04 + (CRC32(CONCAT(p.plan_date, '-C-dt')) % 6) / 100.0
    END, 0) AS down_min,
    p.standard_qty_per_hour * (p.plan_hours - p.plan_hours * CASE p.line_id
        WHEN 1 THEN 0.03 + (CRC32(CONCAT(p.plan_date, '-A-dt')) % 5) / 100.0
        WHEN 2 THEN 0.07 + (CRC32(CONCAT(p.plan_date, '-B-dt')) % 6) / 100.0
        ELSE        0.04 + (CRC32(CONCAT(p.plan_date, '-C-dt')) % 6) / 100.0
    END) AS avail_cap
FROM prod_plan p;

-- ---------------------------------------------------------------------
-- 3. 生产批次：每条计划拆成 4 个批次（每批约 3 小时）
--    单批产出 = 可用产能 ÷ 4 × 产线表现系数（只向下扣，绝不超过可用产能）
--    良品/不良按产线基础不良率：A 2.4% / B 3.1% / C 2.0%
-- ---------------------------------------------------------------------
INSERT INTO prod_batch_record
    (plan_id, equipment_id, batch_no, good_qty, defect_qty, changeover_minutes, start_time, end_time)
SELECT
    c.id,
    eq.id,
    CONCAT('第', k.idx + 1, '批'),
    GREATEST(0, t.qty - ROUND(t.qty * t.defect_rate / 100)),
    ROUND(t.qty * t.defect_rate / 100),
    -- B 产线每天中午有一次换型
    CASE WHEN c.line_id = 2 AND k.idx = 2 THEN 25 + (CRC32(CONCAT(c.plan_date, c.line_id)) % 25) ELSE 0 END,
    TIMESTAMP(DATE_ADD(c.plan_date, INTERVAL (8 + k.idx * 3) HOUR)),
    TIMESTAMP(DATE_ADD(c.plan_date, INTERVAL (11 + k.idx * 3) HOUR))
FROM tmp_cap c
JOIN (
    SELECT 0 AS idx UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
) k
JOIN (
    -- 产线表现系数：乘在「可用产能」上，反映真实的效率差距
    --   A 产线 0.98 ~ 1.00（注塑线稳定）→ 达成率约 97%~101%，基本全部「已完成」
    --   B 产线 0.86 ~ 0.95（冲压/焊接，换型频繁）→ 达成率约 85%~95%，有延期有完成
    --   C 产线 0.93 ~ 0.99（装配/检测）→ 达成率约 92%~99%，多数「已完成」
    -- 因基数已是 avail_cap（已扣停机），乘上 <=1 的系数后单批产出必然 <= 可用产能，
    -- 性能效率天然落在 100% 以内，不会再出现物理上不成立的 >100%。
    SELECT c2.id,
           c2.avail_cap / 4 * (CASE c2.line_id
               WHEN 1 THEN 0.980 + (CRC32(CONCAT(c2.plan_date, '-A')) % 3) / 100.0
               WHEN 2 THEN 0.860 + (CRC32(CONCAT(c2.plan_date, '-B')) % 10) / 100.0
               ELSE        0.930 + (CRC32(CONCAT(c2.plan_date, '-C')) % 7) / 100.0
           END) AS qty,
           CASE c2.line_id
               WHEN 1 THEN 2.4
               WHEN 2 THEN 3.1
               ELSE 2.0
           END + (CRC32(CONCAT(c2.plan_date, c2.line_id)) % 15) / 10.0 AS defect_rate
    FROM tmp_cap c2
) t ON t.id = c.id
JOIN (
    -- 主设备类型按产线区分：A 产线是注塑线、B 产线是冲压/焊接线、C 产线是装配/检测线。
    -- 不按类型硬编码，而是取本产线设备数最多的类型，再按 id 编 line_seq，
    -- 让「第 k 个批次」落到本产线的第 k 台主设备上。
    -- 注意：MySQL 的 LIMIT ... OFFSET 不接受列表达式（会报 1064），
    -- 所以这里用窗口函数预先编号，而不是在子查询里写 OFFSET。
    SELECT id, line_id, line_seq FROM (
        SELECT e.id, e.line_id,
               ROW_NUMBER() OVER (PARTITION BY e.line_id ORDER BY e.id) AS line_seq
        FROM base_equipment e
        WHERE e.is_deleted = 0
          AND e.equipment_type = (
              SELECT e3.equipment_type
              FROM base_equipment e3
              WHERE e3.line_id = e.line_id AND e3.is_deleted = 0
              GROUP BY e3.equipment_type
              ORDER BY COUNT(*) DESC, e3.equipment_type
              LIMIT 1
          )
    ) ranked
) eq ON eq.line_id = c.line_id AND eq.line_seq = ((k.idx % 3) + 1);

-- ---------------------------------------------------------------------
-- 4. 回填实际产量：prod_plan.actual_qty = 当日所有批次良品数之和
-- ---------------------------------------------------------------------
UPDATE prod_plan p
SET p.actual_qty = (
        SELECT COALESCE(SUM(b.good_qty), 0) FROM prod_batch_record b WHERE b.plan_id = p.id
    );

-- ---------------------------------------------------------------------
-- 4. 计划状态：今天及以后待执行/执行中，历史按下达成率定状态
-- ---------------------------------------------------------------------
UPDATE prod_plan p
SET p.status = CASE
        WHEN p.plan_date > CURDATE() THEN '待执行'
        WHEN p.plan_date = CURDATE() THEN '执行中'
        WHEN p.actual_qty * 100.0 / p.target_qty >= 100 THEN '已完成'
        WHEN p.actual_qty * 100.0 / p.target_qty < 88 THEN '已延期'
        ELSE '已完成'
    END;

-- ---------------------------------------------------------------------
-- 5. 损失明细：按批次生成 0~2 条损失，记录时长、影响、归因、发生时刻
--    损失类型池：设备停机 / 换线时间 / 质量损失 / 速度降低 / 小停机 / 其他
--
--    时长按「占计划工时的比例」给，而不是给固定绝对值 —— 一条计划 720 分钟，
--    停机 35 分钟只有 5% 不可用，开动率会算出 99%，那是数据假象不是产线好。
--    这里让停机类损失（设备停机/换线/小停机）合计吃掉 8%~20% 的计划工时，
--    开动率才会落在 OEE 常识区间（85%~95%）里。
-- ---------------------------------------------------------------------
INSERT INTO prod_oee_loss
    (batch_id, loss_type, attribution, duration_minutes, impact_pct, occur_time, description)
SELECT
    b.id,
    x.loss_type,
    x.attribution,
    -- 时长 = 计划总分钟 × 该条损失份额；用 batch+类型 的 CRC 产生波动
    ROUND(p.plan_hours * 60 * x.share
          * (8 + CRC32(CONCAT(b.id, x.loss_type)) % 9) / 12.0) AS duration,
    -- 影响(百分点)：停机类直接吃开动率；速度类吃性能；质量类吃质量
    ROUND(x.share * (8 + CRC32(CONCAT(b.id, x.loss_type)) % 9) / 12.0 * 100 * x.impact_weight, 2),
    TIMESTAMP(DATE_ADD(b.start_time, INTERVAL (b.id % 60) MINUTE)),
    x.description
FROM prod_batch_record b
JOIN prod_plan p ON p.id = b.plan_id
JOIN (
    SELECT '设备停机' AS loss_type, '故障 / 更换模具' AS attribution, '主轴轴承振动超阈值' AS description, 0.040 AS share, 1.00 AS impact_weight
    UNION ALL SELECT '换线时间', '换型 / 调机',   '模具更换与首件确认', 0.035, 1.00
    UNION ALL SELECT '质量损失', '不良 / 返工',   '尺寸超差返工',       0.020, 0.60
    UNION ALL SELECT '速度降低', '降速运行',     '为控制料温主动降速', 0.025, 0.80
    UNION ALL SELECT '小停机',   '卡料 / 卡模',   '料斗架桥，人工清理', 0.012, 1.00
    UNION ALL SELECT '其他',     '待料 / 交接',   '上游来料延迟',       0.008, 0.50
) x ON (CRC32(CONCAT(b.id, 'loss')) % 6) = (FIELD(x.loss_type, '设备停机','换线时间','质量损失','速度降低','小停机','其他') - 1)
WHERE (CRC32(b.id) % 10) < 7;   -- 约 70% 的批次有一条损失，其余批次无损失

-- B 产线的换线损失与批次的换型时长对齐（换型 25~50 分钟，不低于实际换型耗时）
UPDATE prod_oee_loss l
JOIN prod_batch_record b ON b.id = l.batch_id
SET l.duration_minutes = GREATEST(l.duration_minutes, b.changeover_minutes)
WHERE l.loss_type = '换线时间' AND b.changeover_minutes > 0;

-- ---------------------------------------------------------------------
-- 6. AI 说明：按状态给出可解释文案（不是随机字符串，与数据一致）
-- ---------------------------------------------------------------------
UPDATE prod_plan p
SET p.ai_explanation = (
    SELECT CASE
        WHEN p.status = '已完成' AND p.actual_qty * 100.0 / p.target_qty >= 100
            THEN CONCAT('计划达成 ', ROUND(p.actual_qty * 100.0 / p.target_qty, 1), '%，产线运行平稳')
        WHEN p.status = '已延期'
            THEN CONCAT('实际 ', p.actual_qty, ' / 计划 ', p.target_qty,
                        '，达成 ', ROUND(p.actual_qty * 100.0 / p.target_qty, 1),
                        '%；主要受设备停机与换型超时影响，建议 OEE 分析定位')
        WHEN p.status = '执行中'
            THEN CONCAT('当班进行中，已完成 ', p.actual_qty, ' 件，进度 ',
                        ROUND(p.actual_qty * 100.0 / p.target_qty, 1), '%')
        ELSE '已下达，等待开班执行'
    END
);

-- ---------------------------------------------------------------------
-- 7. 校验
-- ---------------------------------------------------------------------
SELECT 'prod_plan' AS t, COUNT(*) AS n FROM prod_plan
UNION ALL SELECT 'prod_batch_record', COUNT(*) FROM prod_batch_record
UNION ALL SELECT 'prod_oee_loss', COUNT(*) FROM prod_oee_loss;

-- 中间表用完即删，避免污染业务库
DROP TABLE IF EXISTS tmp_cap;

SELECT plan_date, line_id, product_name, target_qty, actual_qty,
       ROUND(actual_qty * 100.0 / target_qty, 1) AS achieve, status, shift_name
FROM prod_plan
WHERE plan_date >= DATE_SUB(CURDATE(), INTERVAL 2 DAY)
ORDER BY plan_date DESC, line_id;
