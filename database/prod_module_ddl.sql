-- =====================================================================
-- 生产运营模块 · 表结构补充
-- 目标库：industrial_agent（MySQL 8.0.28+）
-- 执行前提：prod_plan / prod_batch_record / prod_oee_loss 三张表已存在（当前均为 0 行）
--
-- 为什么需要这些列：
--   OEE = 开动率 × 性能效率 × 质量指数。原三张表只够算「质量」（良品/不良），
--   算不了开动率（缺计划工时）和性能效率（缺标准节拍），也拿不到损失明细的归因维度。
--   这些是 OEE 的标准输入量，不是为凑页面而加。
--
-- 幂等：全部用存储过程判断列是否存在，可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS prod_add_col;

DELIMITER $$
CREATE PROCEDURE prod_add_col(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_ddl VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE st FROM @s;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. prod_plan：补计划工时、标准节拍、班次
-- ---------------------------------------------------------------------
-- 计划工时(小时)：开动率分母 = 计划工时 - 停机工时
CALL prod_add_col('prod_plan', 'plan_hours',
    '`plan_hours` decimal(4,1) NOT NULL DEFAULT 10.0 COMMENT "计划生产工时(小时)，开动率分母" AFTER `actual_qty`');

-- 标准节拍(件/小时)：性能效率分母 = 标准节拍 × 实际运行工时
CALL prod_add_col('prod_plan', 'standard_qty_per_hour',
    '`standard_qty_per_hour` int NOT NULL DEFAULT 0 COMMENT "标准节拍(件/小时)，性能效率分母" AFTER `plan_hours`');

-- 班次：白班 08:00-20:00 / 夜班 20:00-08:00
CALL prod_add_col('prod_plan', 'shift_name',
    '`shift_name` varchar(20) NOT NULL DEFAULT "白班" COMMENT "班次：白班/夜班" AFTER `standard_qty_per_hour`');

-- 逻辑删除（与全库其它表一致，MyBatis-Plus 全局 logic-delete-field=isDeleted 要求此列）
CALL prod_add_col('prod_plan', 'is_deleted',
    '`is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT "逻辑删除：0-正常 1-已删除" AFTER `update_time`');

-- ---------------------------------------------------------------------
-- 2. prod_batch_record：补设备归属与换型时长
-- ---------------------------------------------------------------------
-- 损失明细要能答「这 146 分钟停机是哪台设备」，需要批次关联设备
CALL prod_add_col('prod_batch_record', 'equipment_id',
    '`equipment_id` bigint DEFAULT NULL COMMENT "关联设备ID（逻辑外键 base_equipment.id），可空" AFTER `plan_id`');

-- 换型时长(分钟)：产线卡片「换型」字段
CALL prod_add_col('prod_batch_record', 'changeover_minutes',
    '`changeover_minutes` int NOT NULL DEFAULT 0 COMMENT "本条批次的换型耗时(分钟)" AFTER `defect_qty`');

-- ---------------------------------------------------------------------
-- 3. prod_oee_loss：补归因维度与影响值
-- ---------------------------------------------------------------------
-- 归因维度：故障/换型/不良等二级归因，页面「归因维度」列
CALL prod_add_col('prod_oee_loss', 'attribution',
    '`attribution` varchar(100) DEFAULT NULL COMMENT "归因维度，如：故障 / 更换模具" AFTER `loss_type`');

-- 影响(百分点)：该损失对 OEE 的拉低百分点，页面「影响」列
CALL prod_add_col('prod_oee_loss', 'impact_pct',
    '`impact_pct` decimal(4,2) NOT NULL DEFAULT 0.00 COMMENT "对 OEE 的影响(百分点)，页面展示为 -2.0pct" AFTER `duration_minutes`');

-- 损失发生时刻（用于分时段趋势），取批次开始时间 + 偏移
CALL prod_add_col('prod_oee_loss', 'occur_time',
    '`occur_time` datetime DEFAULT NULL COMMENT "损失发生时刻" AFTER `impact_pct`');

-- 逻辑删除
CALL prod_add_col('prod_batch_record', 'is_deleted',
    '`is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT "逻辑删除：0-正常 1-已删除" AFTER `create_time`');
CALL prod_add_col('prod_oee_loss', 'is_deleted',
    '`is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT "逻辑删除：0-正常 1-已删除" AFTER `create_time`');

DROP PROCEDURE IF EXISTS prod_add_col;

-- ---------------------------------------------------------------------
-- 4. 索引（重复创建会报错，故先判断）
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS prod_add_idx;
DELIMITER $$
CREATE PROCEDURE prod_add_idx(IN p_table VARCHAR(64), IN p_idx VARCHAR(64), IN p_cols VARCHAR(128))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_idx
    ) THEN
        SET @s = CONCAT('CREATE INDEX `', p_idx, '` ON `', p_table, '` (', p_cols, ')');
        PREPARE st FROM @s;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

CALL prod_add_idx('prod_plan', 'idx_plan_date', '`plan_date`');
CALL prod_add_idx('prod_plan', 'idx_status', '`status`');
CALL prod_add_idx('prod_batch_record', 'idx_equipment_id', '`equipment_id`');
CALL prod_add_idx('prod_oee_loss', 'idx_occur_time', '`occur_time`');

DROP PROCEDURE IF EXISTS prod_add_idx;

-- ---------------------------------------------------------------------
-- 5. 校验
-- ---------------------------------------------------------------------
SELECT table_name, column_name, column_type, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('prod_plan', 'prod_batch_record', 'prod_oee_loss')
ORDER BY table_name, ordinal_position;