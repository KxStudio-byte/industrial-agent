-- =====================================================================
-- 质量中心 · 数据库迁移脚本
-- 目标库：industrial_agent
-- 执行方式：
--   mysql --default-character-set=utf8mb4 -h 192.168.100.128 -P 3306 \
--         -u root -p industrial_agent < quality_migration.sql
--
-- 约束（严格遵守）：
--   1. 只新增表，不修改、不删除任何现有业务表
--   2. 不引入物理外键（与现有 22 张表的「逻辑外键」规范一致）
--   3. 索引不与现有表冲突（均为新表）
--   4. 脚本可重复执行（IF NOT EXISTS + 幂等插入）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 生产缺陷明细表
--    记录每一次缺陷的类型、责任设备与发生日期，是「缺陷 Top / 缺陷类型
--    统计 / 设备缺陷贡献」三个维度的唯一数据来源。
--
--    与现有表的关联（逻辑外键，无物理约束）：
--      batch_id     → prod_batch_record.id
--      equipment_id → base_equipment.id（可空：现场返工等无责任设备的缺陷）
--
--    口径约定：
--      qty 是「缺陷件数」，不是记录条数。同一批次同一天同一类型可能有多条
--      记录，统计时必须 SUM(qty) 而不是 COUNT(*)。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `prod_defect_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id`      bigint       NOT NULL                COMMENT '批次ID（逻辑外键 prod_batch_record.id）',
  `equipment_id`  bigint       DEFAULT NULL            COMMENT '责任设备ID（逻辑外键 base_equipment.id），可空',
  `defect_type`   varchar(50)  NOT NULL                COMMENT '缺陷类型：外观划伤/尺寸超差/装配漏装/色差',
  `qty`           int          NOT NULL DEFAULT '0'    COMMENT '缺陷数量（件），不是记录条数',
  `record_date`   date         NOT NULL                COMMENT '发生日期',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_date_type` (`record_date`, `defect_type`)   COMMENT '缺陷 Top / 类型统计',
  KEY `idx_equipment` (`equipment_id`)                 COMMENT '设备维度统计',
  KEY `idx_batch`     (`batch_id`)                     COMMENT '批次关联查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产缺陷明细表';

-- ---------------------------------------------------------------------
-- 2. 客户投诉记录表
--    质量总览 KPI 的「客诉」指标来源。
--
--    status 口径：
--      '处理中' / '已关闭'  —— 「本周客诉」统计 status 不限，只按 occur_date 过滤
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `prod_complaint_record` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `complaint_no` varchar(50)  NOT NULL                COMMENT '客诉单号（如 CPL-20261101-001）',
  `product_name` varchar(100) DEFAULT NULL            COMMENT '涉及产品',
  `line_id`      bigint       DEFAULT NULL            COMMENT '涉及产线ID（逻辑外键 base_production_line.id）',
  `defect_type`  varchar(50)  DEFAULT NULL            COMMENT '投诉对应的缺陷类型',
  `qty`          int          NOT NULL DEFAULT '0'    COMMENT '涉及数量（件）',
  `content`      varchar(255) DEFAULT NULL            COMMENT '投诉内容摘要',
  `status`       varchar(20)  NOT NULL DEFAULT '处理中' COMMENT '状态：处理中/已关闭',
  `occur_date`   date         NOT NULL                COMMENT '投诉发生日期',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_complaint_no` (`complaint_no`),
  KEY `idx_occur_date` (`occur_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户投诉记录表';