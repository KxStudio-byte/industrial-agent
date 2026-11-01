SET NAMES utf8mb4;

/* ============================================================
 * 设备中心模块：新增字段与表结构变更
 * ============================================================ */

/* 1. base_equipment 增加「最近异常描述」列（前端 Equipment.lastException 无来源） */
SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'industrial_agent'
                     AND TABLE_NAME = 'base_equipment'
                     AND COLUMN_NAME = 'last_exception');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE base_equipment ADD COLUMN last_exception VARCHAR(200) DEFAULT NULL COMMENT ''最近异常描述'' AFTER risk_level',
    'SELECT ''last_exception already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

/* 2. 设备故障预测表（h24/d7/d30 三个时间窗的概率） */
CREATE TABLE IF NOT EXISTS `eqp_fault_predict` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `h24` decimal(5,2) DEFAULT NULL COMMENT '24小时故障概率(%)',
  `d7` decimal(5,2) DEFAULT NULL COMMENT '7天故障概率(%)',
  `d30` decimal(5,2) DEFAULT NULL COMMENT '30天故障概率(%)',
  `model_version` varchar(50) DEFAULT NULL COMMENT '预测模型版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预测时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_id` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备故障预测表';

/* 3. 设备运行数据历史表（趋势图数据源；eqp_runtime_data 是唯一键只能存最新一条） */
CREATE TABLE IF NOT EXISTS `eqp_runtime_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `equipment_id` bigint NOT NULL COMMENT '设备ID',
  `temperature` decimal(5,2) DEFAULT NULL COMMENT '温度(℃)',
  `vibration` decimal(5,2) DEFAULT NULL COMMENT '振动(mm/s)',
  `current` decimal(5,2) DEFAULT NULL COMMENT '电流(A)',
  `record_time` datetime NOT NULL COMMENT '采集时间',
  PRIMARY KEY (`id`),
  KEY `idx_equipment_time` (`equipment_id`,`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备运行数据历史表';

/* ============================================================
 * 基础主数据：车间 + 产线
 * ============================================================ */

INSERT INTO `sys_workshop` (`id`, `workshop_name`) VALUES
  (1, '一车间'), (2, '二车间'), (3, '三车间')
ON DUPLICATE KEY UPDATE `workshop_name` = VALUES(`workshop_name`);

INSERT INTO `base_production_line` (`id`, `workshop_id`, `line_name`, `status`) VALUES
  (1, 1, 'A产线', 1),
  (2, 2, 'B产线', 1),
  (3, 3, 'C产线', 1)
ON DUPLICATE KEY UPDATE `line_name` = VALUES(`line_name`);

/* ============================================================
 * 设备台账：48 台（A/B/C 产线各 16 台）
 * 状态分布对齐原型 KPI：正常 42 / 预警 3 / 故障 1 / 离线 2
 * 风险分布：高 2 / 中 3 / 低 43
 * ============================================================ */

INSERT INTO `base_equipment`
  (`id`, `line_id`, `equipment_name`, `equipment_code`, `equipment_type`, `model`, `manufacturer`,
   `health_score`, `risk_level`, `last_exception`, `status`) VALUES
  /* ---------- A产线：注塑机 ---------- */
  ( 1, 1, '1号注塑机', 'EQ-IM-001', '注塑', 'IM-220T', '海天', 96, '低', NULL, '正常'),
  ( 2, 1, '2号注塑机', 'EQ-IM-002', '注塑', 'IM-220T', '海天', 94, '低', NULL, '正常'),
  ( 3, 1, '3号注塑机', 'EQ-IM-003', '注塑', 'IM-220T', '海天', 72, '高', '主轴轴承振动持续升高', '预警'),
  ( 4, 1, '4号注塑机', 'EQ-IM-004', '注塑', 'IM-380T', '震雄', 95, '低', NULL, '正常'),
  ( 5, 1, '5号注塑机', 'EQ-IM-005', '注塑', 'IM-380T', '震雄', 68, '高', '液压系统压力波动异常', '预警'),
  ( 6, 1, '6号注塑机', 'EQ-IM-006', '注塑', 'IM-380T', '震雄', 91, '低', NULL, '正常'),
  ( 7, 1, '7号注塑机', 'EQ-IM-007', '注塑', 'IM-530T', '海天', 93, '低', NULL, '正常'),
  ( 8, 1, '8号注塑机', 'EQ-IM-008', '注塑', 'IM-530T', '海天', 89, '中', '料筒加热圈温控偏差', '正常'),
  ( 9, 1, '9号注塑机', 'EQ-IM-009', '注塑', 'IM-530T', '海天', 92, '低', NULL, '正常'),
  (10, 1, '10号注塑机', 'EQ-IM-010', '注塑', 'IM-650T', '伊之密', 90, '低', NULL, '正常'),
  /* ---------- A产线：辅机 ---------- */
  (11, 1, '1号模温机', 'EQ-MT-011', '辅机', 'MT-300', '信易', 97, '低', NULL, '正常'),
  (12, 1, '2号模温机', 'EQ-MT-012', '辅机', 'MT-300', '信易', 96, '低', NULL, '正常'),
  (13, 1, '1号冷水机', 'EQ-CW-013', '辅机', 'CW-10HP', '信易', 95, '低', NULL, '正常'),
  (14, 1, 'A线机械手', 'EQ-RB-014', '机器人', 'RB-6A', '拓斯达', 88, '中', '抓取定位偏移 0.8mm', '正常'),
  (15, 1, 'A线空压机', 'EQ-AC-015', '动力', 'AC-75KW', '阿特拉斯', 94, '低', NULL, '正常'),
  /* ---------- A产线：清巍机（离线） ---------- */
  (16, 1, '1号清巍机', 'EQ-CL-016', '专用', 'CL-800', '国盛', 82, '中', '通讯中断，数据采集停止', '离线'),

  /* ---------- B产线：冲压机 ---------- */
  (17, 2, '1号冲压机', 'EQ-PR-017', '冲压', 'PR-160T', '扬力', 95, '低', NULL, '正常'),
  (18, 2, '2号冲压机', 'EQ-PR-018', '冲压', 'PR-160T', '扬力', 93, '低', NULL, '正常'),
  (19, 2, '3号冲压机', 'EQ-PR-019', '冲压', 'PR-250T', '扬力', 91, '低', NULL, '正常'),
  (20, 2, '4号冲压机', 'EQ-PR-020', '冲压', 'PR-250T', '扬力', 90, '低', NULL, '正常'),
  (21, 2, '5号冲压机', 'EQ-PR-021', '冲压', 'PR-400T', '金方圆', 86, '中', '滑块导柱润滑不足', '正常'),
  (22, 2, '6号冲压机', 'EQ-PR-022', '冲压', 'PR-400T', '金方圆', 94, '低', NULL, '正常'),
  (23, 2, '7号冲压机', 'EQ-PR-023', '冲压', 'PR-400T', '金方圆', 92, '低', NULL, '正常'),
  (24, 2, '8号冲压机', 'EQ-PR-024', '冲压', 'PR-630T', '扬力', 89, '低', NULL, '正常'),
  /* ---------- B产线：焊接 ---------- */
  (25, 2, '1号焊接机器人', 'EQ-WD-025', '焊接', 'WD-2000', '发那科', 96, '低', NULL, '正常'),
  (26, 2, '2号焊接机器人', 'EQ-WD-026', '焊接', 'WD-2000', '发那科', 95, '低', NULL, '正常'),
  (27, 2, '3号焊接机器人', 'EQ-WD-027', '焊接', 'WD-3000', '安川', 71, '高', '伺服电机过温告警', '故障'),
  (28, 2, '4号焊接机器人', 'EQ-WD-028', '焊接', 'WD-3000', '安川', 93, '低', NULL, '正常'),
  (29, 2, 'B线机械手', 'EQ-RB-029', '机器人', 'RB-8B', '拓斯达', 92, '低', NULL, '正常'),
  (30, 2, 'B线空压机', 'EQ-AC-030', '动力', 'AC-110KW', '开山', 90, '低', NULL, '正常'),
  (31, 2, '2号冷水机', 'EQ-CW-031', '辅机', 'CW-15HP', '信易', 94, '低', NULL, '正常'),
  (32, 2, '2号清巍机', 'EQ-CL-032', '专用', 'CL-800', '国盛', 87, '低', NULL, '正常'),

  /* ---------- C产线：装配 ---------- */
  (33, 3, '1号装配线', 'EQ-AS-033', '装配', 'AS-L1', '博众', 95, '低', NULL, '正常'),
  (34, 3, '2号装配线', 'EQ-AS-034', '装配', 'AS-L1', '博众', 94, '低', NULL, '正常'),
  (35, 3, '3号装配线', 'EQ-AS-035', '装配', 'AS-L2', '博众', 92, '低', NULL, '正常'),
  (36, 3, '4号装配线', 'EQ-AS-036', '装配', 'AS-L2', '博众', 91, '低', NULL, '正常'),
  (37, 3, '1号检测台', 'EQ-QC-037', '检测', 'QC-V3', '基恩士', 97, '低', NULL, '正常'),
  (38, 3, '2号检测台', 'EQ-QC-038', '检测', 'QC-V3', '基恩士', 96, '低', NULL, '正常'),
  (39, 3, '3号检测台', 'EQ-QC-039', '检测', 'QC-V4', '基恩士', 95, '低', NULL, '正常'),
  (40, 3, '1号包装机', 'EQ-PK-040', '包装', 'PK-200', '永创', 93, '低', NULL, '正常'),
  (41, 3, '2号包装机', 'EQ-PK-041', '包装', 'PK-200', '永创', 92, '低', NULL, '正常'),
  (42, 3, '1号贴标机', 'EQ-LB-042', '包装', 'LB-100', '永创', 94, '低', NULL, '正常'),
  (43, 3, '3号冷水机', 'EQ-CW-043', '辅机', 'CW-10HP', '信易', 95, '低', NULL, '正常'),
  (44, 3, '3号清巍机', 'EQ-CL-044', '专用', 'CL-1000', '国盛', 88, '低', NULL, '正常'),
  (45, 3, 'C线机械手', 'EQ-RB-045', '机器人', 'RB-6C', '拓斯达', 93, '低', NULL, '正常'),
  (46, 3, 'C线空压机', 'EQ-AC-046', '动力', 'AC-75KW', '阿特拉斯', 91, '低', NULL, '正常'),
  (47, 3, '周转车 A', 'EQ-CR-047', '物流', 'CR-01', '自制', 90, '低', NULL, '正常'),
  (48, 3, '周转车 B', 'EQ-CR-048', '物流', 'CR-01', '自制', 85, '中', '电量不足，待充电', '离线')
ON DUPLICATE KEY UPDATE `equipment_name` = VALUES(`equipment_name`);

/* ============================================================
 * 实时运行数据（每台一条，与 status 自洽）
 * ============================================================ */
INSERT INTO `eqp_runtime_data` (`equipment_id`, `temperature`, `vibration`, `current`, `record_time`)
SELECT e.id,
       ROUND(CASE WHEN e.status = '预警' THEN 68 + RAND() * 6
                  WHEN e.status = '故障' THEN 78 + RAND() * 8
                  WHEN e.status = '离线' THEN 40 + RAND() * 5
                  ELSE 48 + RAND() * 14 END, 2),
       ROUND(CASE WHEN e.status = '预警' THEN 7.0 + RAND() * 2
                  WHEN e.status = '故障' THEN 10.0 + RAND() * 3
                  WHEN e.status = '离线' THEN 4.0 + RAND() * 2
                  ELSE 3.0 + RAND() * 3 END, 2),
       ROUND(CASE WHEN e.status IN ('正常', '预警') THEN 38 + RAND() * 12
                  ELSE RAND() * 3 END, 2),
       NOW() - INTERVAL FLOOR(RAND() * 5) MINUTE
FROM `base_equipment` e
ON DUPLICATE KEY UPDATE `temperature` = VALUES(`temperature`),
                        `vibration`   = VALUES(`vibration`),
                        `current`     = VALUES(`current`),
                        `record_time` = VALUES(`record_time`);

/* ============================================================
 * 故障预测
 * ============================================================ */
INSERT INTO `eqp_fault_predict` (`equipment_id`, `h24`, `d7`, `d30`, `model_version`)
SELECT e.id,
       CASE WHEN e.risk_level = '高' AND e.status = '故障' THEN 92.50
            WHEN e.risk_level = '高' THEN 12.00
            WHEN e.risk_level = '中' THEN 8.50
            ELSE 1.20 END,
       CASE WHEN e.risk_level = '高' AND e.status = '故障' THEN 98.00
            WHEN e.risk_level = '高' THEN 45.00
            WHEN e.risk_level = '中' THEN 22.00
            ELSE 6.40 END,
       CASE WHEN e.risk_level = '高' AND e.status = '故障' THEN 99.20
            WHEN e.risk_level = '高' THEN 68.00
            WHEN e.risk_level = '中' THEN 41.00
            ELSE 18.30 END,
       'fault-lgbm-v1.2'
FROM `base_equipment` e
ON DUPLICATE KEY UPDATE `h24` = VALUES(`h24`), `d7` = VALUES(`d7`), `d30` = VALUES(`d30`);

/* ============================================================
 * 最近 24 小时历史数据（趋势图用，每台 24 点）
 * ============================================================ */
INSERT INTO `eqp_runtime_history` (`equipment_id`, `temperature`, `vibration`, `current`, `record_time`)
SELECT r.equipment_id,
       r.temperature, r.vibration, r.current, r.record_time
FROM (
  SELECT e.id AS equipment_id,
         0 AS equipment_id_dup,
         ROUND(CASE WHEN e.status = '预警' THEN 66 + RAND() * 8
                    WHEN e.status = '故障' THEN 76 + RAND() * 10
                    WHEN e.status = '离线' THEN 38 + RAND() * 4
                    ELSE 46 + RAND() * 16 END, 2) AS temperature,
         ROUND(CASE WHEN e.status = '预警' THEN 6.5 + RAND() * 3
                    WHEN e.status = '故障' THEN 9.5 + RAND() * 3
                    WHEN e.status = '离线' THEN 3.0 + RAND() * 2
                    ELSE 2.5 + RAND() * 4 END, 2) AS vibration,
         ROUND(CASE WHEN e.status IN ('正常', '预警') THEN 36 + RAND() * 14
                    ELSE RAND() * 3 END, 2) AS current,
         NOW() - INTERVAL h.n HOUR AS record_time
  FROM `base_equipment` e
  CROSS JOIN (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
    UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
  ) h
) r
WHERE NOT EXISTS (
  SELECT 1 FROM `eqp_runtime_history` h2
  WHERE h2.equipment_id = r.equipment_id AND h2.record_time = r.record_time
);

/* ============================================================
 * AI 洞察（设备诊断/最近异常的数据来源）
 * ============================================================ */
INSERT INTO `ai_insight` (`title`, `object_type`, `object_id`, `probability`, `risk_level`, `ai_diagnosis`, `status`)
SELECT CONCAT(e.equipment_name, '存在异常'), '设备', e.id,
       CASE WHEN e.risk_level = '高' THEN 87.00 WHEN e.risk_level = '中' THEN 62.00 ELSE 23.00 END,
       e.risk_level,
       CASE e.equipment_code
         WHEN 'EQ-IM-003' THEN '主轴轴承磨损概率 87%，振动值 7.8mm/s 已超阈值 6.5，建议 48 小时内更换轴承。'
         WHEN 'EQ-IM-005' THEN '液压系统压力波动 ±0.8MPa，疑似液压泵内泄，建议检查密封件与油液清洁度。'
         WHEN 'EQ-WD-027' THEN '伺服电机温度 82℃ 超上限 75℃，散热风扇转速异常，已触发停机保护。'
         WHEN 'EQ-IM-008' THEN '料筒加热圈温控偏差 6℃，疑似热电偶接触不良，建议紧固接线。'
         WHEN 'EQ-RB-014' THEN '抓取定位偏移 0.8mm，疑似吸盘老化，建议更换吸盘并重新标定。'
         WHEN 'EQ-PR-021' THEN '滑块导柱润滑不足，建议补充润滑脂并检查油路。'
         WHEN 'EQ-CL-016' THEN '设备通讯中断超过 30 分钟，数据采集停止，请检查网关与网线。'
         ELSE '设备运行平稳，各项参数在正常区间。'
       END,
       CASE WHEN e.status = '预警' THEN '待处理' ELSE '已忽略' END
FROM `base_equipment` e
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_insight` i
  WHERE i.object_type = '设备' AND i.object_id = e.id
);

SELECT (SELECT COUNT(*) FROM base_equipment)       AS equipment_total,
       (SELECT COUNT(*) FROM eqp_runtime_data)     AS runtime_total,
       (SELECT COUNT(*) FROM eqp_fault_predict)    AS predict_total,
       (SELECT COUNT(*) FROM eqp_runtime_history)  AS history_total,
       (SELECT COUNT(*) FROM ai_insight)           AS insight_total;