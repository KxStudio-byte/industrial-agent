SET NAMES utf8mb4;

/* 调整状态与风险分布，使其同时对齐原型 KPI：
 *   状态 正常 42 / 预警 3 / 故障 1 / 离线 2  （合计 48）
 *   风险 高 2 / 中 3 / 低 43
 * 之前的分布是 正常43/预警2、高3/中5，差在 3 台设备上。
 */

/* 8号注塑机：由「正常/中」改为「预警/中」→ 正常-1、预警+1 */
UPDATE `base_equipment`
   SET `status` = '预警', `health_score` = 79,
       `last_exception` = '料筒加热圈温控偏差 6℃'
 WHERE `equipment_code` = 'EQ-IM-008';

/* 1号冲压机：由「正常/低」改为「预警/低」→ 正常-1、预警+1 */
UPDATE `base_equipment`
   SET `status` = '预警', `health_score` = 84,
       `last_exception` = '飞轮轴承温度偏高'
 WHERE `equipment_code` = 'EQ-PR-017';

/* A线机械手：风险「中」→「低」→ 中-1、低+1 */
UPDATE `base_equipment` SET `risk_level` = '低' WHERE `equipment_code` = 'EQ-RB-014';
/* 5号冲压机：风险「中」→「低」→ 中-1、低+1 */
UPDATE `base_equipment` SET `risk_level` = '低' WHERE `equipment_code` = 'EQ-PR-021';

/* 2号冲压机：风险「低」→「高」→ 高+1、低-1（使高风险回到 2 台） */
UPDATE `base_equipment`
   SET `risk_level` = '高', `health_score` = 77,
       `last_exception` = '曲轴间隙超差，存在卡死风险'
 WHERE `equipment_code` = 'EQ-PR-018';

/* 同步实时数据：状态为「正常」但实际有异常的 8 号机，温度拉高 */
UPDATE `eqp_runtime_data` SET `temperature` = 69.30, `vibration` = 7.10
 WHERE `equipment_id` = (SELECT id FROM `base_equipment` WHERE `equipment_code` = 'EQ-IM-008');
UPDATE `eqp_runtime_data` SET `temperature` = 67.80, `vibration` = 6.90
 WHERE `equipment_id` = (SELECT id FROM `base_equipment` WHERE `equipment_code` = 'EQ-PR-017');

/* 补 1号冲压机 的异常洞察（它刚变成预警） */
INSERT INTO `ai_insight` (`title`, `object_type`, `object_id`, `probability`, `risk_level`, `ai_diagnosis`, `status`)
SELECT '1号冲压机存在异常', '设备', e.id, 58.00, '低',
       '飞轮轴承温度 67.8℃ 高于基线 12℃，建议检查轴承润滑状态。', '待处理'
FROM `base_equipment` e
WHERE e.equipment_code = 'EQ-PR-017'
  AND NOT EXISTS (SELECT 1 FROM `ai_insight` i WHERE i.object_type = '设备' AND i.object_id = e.id);

/* 让洞察文案与调整后的风险等级一致 */
UPDATE `ai_insight` SET `risk_level` = '低', `probability` = 23.00
 WHERE `object_type` = '设备' AND `object_id` = (SELECT id FROM `base_equipment` WHERE `equipment_code` = 'EQ-RB-014');
UPDATE `ai_insight` SET `risk_level` = '低', `probability` = 23.00
 WHERE `object_type` = '设备' AND `object_id` = (SELECT id FROM `base_equipment` WHERE `equipment_code` = 'EQ-PR-021');

/* 同步预测值 */
INSERT INTO `eqp_fault_predict` (`equipment_id`, `h24`, `d7`, `d30`, `model_version`)
SELECT e.id,
       CASE WHEN e.risk_level = '高' AND e.status = '故障' THEN 92.50
            WHEN e.risk_level = '高' THEN 12.00
            WHEN e.risk_level = '中' THEN 8.50 ELSE 1.20 END,
       CASE WHEN e.risk_level = '高' AND e.status = '故障' THEN 98.00
            WHEN e.risk_level = '高' THEN 45.00
            WHEN e.risk_level = '中' THEN 22.00 ELSE 6.40 END,
       CASE WHEN e.risk_level = '高' AND e.status = '故障' THEN 99.20
            WHEN e.risk_level = '高' THEN 68.00
            WHEN e.risk_level = '中' THEN 41.00 ELSE 18.30 END,
       'fault-lgbm-v1.2'
FROM `base_equipment` e
ON DUPLICATE KEY UPDATE `h24` = VALUES(`h24`), `d7` = VALUES(`d7`), `d30` = VALUES(`d30`);

SELECT status, COUNT(*) c FROM base_equipment GROUP BY status;
SELECT risk_level, COUNT(*) c FROM base_equipment GROUP BY risk_level;