-- ============================================================
-- 能源中心模块建表脚本
-- 目标库：industrial_agent
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，可反复执行
-- ============================================================

-- ------------------------------------------------------------
-- 1. 能耗读表 energy_meter
-- ------------------------------------------------------------
-- 设计说明（与 docs/API接口清单.md §22 的差异，均为必要修正）：
--
--   a) 增加 granularity 字段区分三种统计粒度：
--        'hour'   按小时（record_hour 0-23 有值，equipment_id 指向具体设备）
--        'day'    按天·车间级汇总（record_hour 为 NULL，workshop_id 有值，equipment_id 为 NULL）
--        'day_eq' 按天·设备级明细（record_hour 为 NULL，equipment_id 有值）
--      原文档只用 record_hour 是否为空来区分粒度，导致「车间日汇总」和「设备日明细」
--      无法区分，SUM 时会重复计数（车间汇总 + 设备明细加两遍）。
--
--   b) 单位统一由 unit 字段承载，接口层按 medium 归一到需求的展示单位：
--        电 -> kWh      水 -> t       气 -> Nm³      蒸汽 -> t
--
--   c) workshop_id 在下游统计时通过 base_production_line.workshop_id 关联得到，
--      但设备级记录直接冗余存一份，避免每次都要 JOIN 产线表。
--
CREATE TABLE IF NOT EXISTS `energy_meter` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workshop_id`  BIGINT       DEFAULT NULL COMMENT '车间ID（逻辑外键 sys_workshop.id）',
  `line_id`      BIGINT       DEFAULT NULL COMMENT '产线ID（逻辑外键 base_production_line.id）',
  `equipment_id` BIGINT       DEFAULT NULL COMMENT '设备ID（逻辑外键 base_equipment.id），车间级汇总时为空',
  `medium`       VARCHAR(20)  NOT NULL COMMENT '能源介质：电/水/气/蒸汽',
  `value`        DECIMAL(12,2) NOT NULL COMMENT '用量（按 unit 计量）',
  `unit`         VARCHAR(20)  NOT NULL COMMENT '计量单位：kWh/t/Nm³',
  `granularity`  VARCHAR(10)  NOT NULL DEFAULT 'hour' COMMENT '统计粒度：hour 按小时 / day 车间日汇总 / day_eq 设备日明细',
  `record_date`  DATE         NOT NULL COMMENT '统计日期',
  `record_hour`  TINYINT      DEFAULT NULL COMMENT '小时 0-23，仅 granularity=hour 时有值',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_date_medium` (`record_date`, `medium`) COMMENT '按日期+介质统计（趋势/占比）',
  KEY `idx_gran_date` (`granularity`, `record_date`) COMMENT '按粒度+日期过滤',
  KEY `idx_workshop_date` (`workshop_id`, `record_date`) COMMENT '分车间对比',
  KEY `idx_equipment_date` (`equipment_id`, `record_date`) COMMENT '设备能耗排行',
  KEY `idx_hour` (`record_date`, `record_hour`) COMMENT '分时电耗 / 白班夜班'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='能耗读表（分介质/分粒度）';

-- ------------------------------------------------------------
-- 2. AI 节能优化方案 energy_saving_plan
-- ------------------------------------------------------------
-- 说明：
--   saving 存的是「每月节约金额（元）」，不是年度值。
--   原型列表列头「预计节约」显示「¥28,600 / 月」，故以月为单位存储。
--   reason/advice/scope 对应原型抽屉的「异常判断 / AI建议 / 影响范围」。
--
CREATE TABLE IF NOT EXISTS `energy_saving_plan` (
  `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_code`  VARCHAR(50)   NOT NULL COMMENT '方案编号，如 EO-20260907-012',
  `plan_name`  VARCHAR(200)  NOT NULL COMMENT '方案名称',
  `location`   VARCHAR(100)  DEFAULT NULL COMMENT '位置，如 A车间 / 4号空压机',
  `medium`     VARCHAR(20)   DEFAULT NULL COMMENT '能源介质：电/水/气/蒸汽',
  `status`     VARCHAR(20)   NOT NULL DEFAULT '待执行' COMMENT '执行状态：待执行/已完成/执行失败',
  `saving`     DECIMAL(12,2) DEFAULT NULL COMMENT '预计每月节约金额（元）',
  `reason`     TEXT          COMMENT 'AI 判断原因（异常判断）',
  `advice`     TEXT          COMMENT '建议措施（AI建议）',
  `scope`      VARCHAR(100)  DEFAULT NULL COMMENT '影响范围',
  `result`     VARCHAR(200)  DEFAULT NULL COMMENT '执行结果',
  `exec_time`  DATETIME      DEFAULT NULL COMMENT '执行时间',
  `source`     VARCHAR(50)   DEFAULT NULL COMMENT '来源：人工/AI Agent',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`),
  KEY `idx_status` (`status`),
  KEY `idx_medium` (`medium`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI节能优化方案表';

-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
SELECT table_name, table_comment
  FROM information_schema.tables
 WHERE table_schema = 'industrial_agent'
   AND table_name IN ('energy_meter', 'energy_saving_plan');