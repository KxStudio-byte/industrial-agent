package com.industrial.agent.mapper;

import com.industrial.agent.vo.QualityDefectVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 质量中心统计 Mapper。
 *
 * <p><b>不继承 {@code BaseMapper}</b>：本 Mapper 只做只读统计，没有任何按主键增删改的场景，
 * 继承后会凭空多出 20 多个用不上的 CRUD 方法，反而容易被人误以为这里能写业务数据。
 * 缺陷明细的写入走 {@code prod_defect_record} 的种子脚本 / 后续 MES 对接，不经过本接口。
 *
 * <p><b>三个口径必须分清，任何改动不得混用：</b>
 * <ol>
 *   <li><b>数量口径</b>：{@code prod_batch_record} 的 {@code SUM(good_qty)} /
 *       {@code SUM(defect_qty)} — 良品率、合格率、产量都走这条</li>
 *   <li><b>批次口径</b>：{@code COUNT(*)} 与「合格批次 = defect_qty = 0 的条数」之比 —
 *       批次合格率走这条</li>
 *   <li><b>缺陷明细口径</b>：{@code prod_defect_record} 的 {@code SUM(qty)} 按
 *       {@code defect_type} 分组 — 缺陷 Top、分维度统计走这条</li>
 * </ol>
 *
 * <p><b>{@code prod_batch_record} 的日期必须从 {@code prod_plan.plan_date} 取</b>，
 * 不能用 {@code prod_batch_record.create_time}：实测 360 条批次的 create_time
 * 全是同一个种子脚本执行时刻（2026-11-01 13:23:58），按它过滤「今日」会把整表都算进来。
 *
 * <p>所有产线/设备/产品名称统一从 {@code base_equipment} /
 * {@code base_production_line} / {@code prod_plan} 关联取得，
 * 不在 {@code prod_defect_record} 里冗余存储，避免改名后两边不一致。
 */
@Mapper
public interface QualityMapper {

    // ==================== 1. 数量口径（prod_batch_record ⨝ prod_plan） ====================

    /**
     * 单日数量汇总。
     *
     * @return {@code {good, defect, qty}}；无数据时 {@code good/defect} 为 0、{@code qty} 为 null
     */
    @Select("""
            SELECT COALESCE(SUM(b.good_qty), 0)    AS good,
                   COALESCE(SUM(b.defect_qty), 0)  AS defect,
                   SUM(b.good_qty + b.defect_qty)  AS qty
              FROM prod_batch_record b
              JOIN prod_plan p ON p.id = b.plan_id AND p.is_deleted = 0
             WHERE b.is_deleted = 0
               AND p.plan_date = #{date}
            """)
    Map<String, Object> selectQtyByDate(@Param("date") LocalDate date);

    /**
     * 单日批次汇总（批次口径）。
     *
     * <p>合格判定：批次不良率 {@code defect/(good+defect)*100 <= threshold}。
     * <b>不是</b>「defect_qty = 0 才算合格」——实测 360 条批次全部有不良，
     * 零缺陷口径会让批次合格率恒为 0%（详见 {@code QualityQuery#batchPassThreshold}）。
     *
     * @param threshold 合格阈值(%)，含等号
     * @return {@code {total, qualified}}；无数据时两者为 0
     */
    @Select("""
            SELECT COUNT(1) AS total,
                   COALESCE(SUM(CASE
                       WHEN b.defect_qty / NULLIF(b.good_qty + b.defect_qty, 0) * 100 <= #{threshold}
                       THEN 1 ELSE 0 END), 0) AS qualified
              FROM prod_batch_record b
              JOIN prod_plan p ON p.id = b.plan_id AND p.is_deleted = 0
             WHERE b.is_deleted = 0
               AND p.plan_date = #{date}
            """)
    Map<String, Object> selectBatchByDate(@Param("date") LocalDate date,
                                          @Param("threshold") BigDecimal threshold);

    // ==================== 2. 缺陷明细口径（prod_defect_record 按类型） ====================

    /** 窗口内缺陷件数按类型分组，降序。附该类型缺陷最多的产线名称 */
    @Select("""
            SELECT d.defect_type                AS type,
                   SUM(d.qty)                   AS qty,
                   (SELECT l.line_name
                      FROM prod_defect_record dd
                      JOIN base_equipment e      ON e.id = dd.equipment_id
                      JOIN base_production_line l ON l.id = e.line_id
                     WHERE dd.record_date BETWEEN #{from} AND #{to}
                       AND dd.defect_type = d.defect_type
                     GROUP BY l.line_name
                     ORDER BY SUM(dd.qty) DESC
                     LIMIT 1)                   AS lineName
              FROM prod_defect_record d
             WHERE d.record_date BETWEEN #{from} AND #{to}
             GROUP BY d.defect_type
             ORDER BY qty DESC
            """)
    List<QualityDefectVO.Top> selectTopDefects(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 窗口内总缺陷件数（Top 占比的分母） */
    @Select("""
            SELECT COALESCE(SUM(qty), 0)
              FROM prod_defect_record
             WHERE record_date BETWEEN #{from} AND #{to}
            """)
    Integer selectTotalDefectQty(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 窗口内各产线的良品率 / 批次合格率 / 缺陷件数 / 主要缺陷。
     *
     * <p>口径（三者周期与分母都不同，故意分三个子查询，不做一次 GROUP BY）：
     * <ul>
     *   <li>{@code rate}：数量口径，{@code plan_date = today} 单日</li>
     *   <li>{@code delta}：今日良品率 − 昨日良品率，带符号的十进制字符串；
     *       昨日无数据时为 {@code null}（前端显示「—」而不是 0）</li>
     *   <li>{@code batchRate}：批次口径 + 阈值判定，单日</li>
     *   <li>{@code defectQty / mainDefect}：缺陷明细口径，窗口 {@code from ~ to}</li>
     * </ul>
     */
    @Select("""
            SELECT l.line_name                    AS lineName,
                   l.id                           AS lineId,
                   t.rate                         AS rate,
                   CASE WHEN y.rate IS NULL OR t.rate IS NULL THEN NULL
                        ELSE CAST(ROUND(t.rate - y.rate, 2) AS CHAR) END AS delta,
                   t.batchRate                    AS batchRate,
                   d.defectQty                    AS defectQty,
                   d.mainDefect                   AS mainDefect,
                   d.mainDefectQty                AS mainDefectQty
              FROM base_production_line l
              LEFT JOIN (
                    SELECT e.line_id AS lineId,
                           ROUND(SUM(b.good_qty) / NULLIF(SUM(b.good_qty + b.defect_qty), 0) * 100, 2) AS rate,
                           ROUND(SUM(CASE
                               WHEN b.defect_qty / NULLIF(b.good_qty + b.defect_qty, 0) * 100 <= #{threshold}
                               THEN 1 ELSE 0 END) / NULLIF(COUNT(1), 0) * 100, 2) AS batchRate
                      FROM prod_batch_record b
                      JOIN prod_plan p      ON p.id = b.plan_id AND p.is_deleted = 0
                      JOIN base_equipment e ON e.id = b.equipment_id
                     WHERE b.is_deleted = 0
                       AND p.plan_date = #{today}
                     GROUP BY e.line_id
              ) t ON t.lineId = l.id
              LEFT JOIN (
                    SELECT e.line_id AS lineId,
                           ROUND(SUM(b.good_qty) / NULLIF(SUM(b.good_qty + b.defect_qty), 0) * 100, 2) AS rate
                      FROM prod_batch_record b
                      JOIN prod_plan p      ON p.id = b.plan_id AND p.is_deleted = 0
                      JOIN base_equipment e ON e.id = b.equipment_id
                     WHERE b.is_deleted = 0
                       AND p.plan_date = #{yesterday}
                     GROUP BY e.line_id
              ) y ON y.lineId = l.id
              LEFT JOIN (
                    SELECT e.line_id                                  AS lineId,
                           SUM(dd.qty)                                AS defectQty,
                           (SELECT dd2.defect_type
                              FROM prod_defect_record dd2
                              JOIN base_equipment e2 ON e2.id = dd2.equipment_id
                             WHERE e2.line_id = e.line_id
                               AND dd2.record_date BETWEEN #{from} AND #{to}
                             GROUP BY dd2.defect_type
                             ORDER BY SUM(dd2.qty) DESC
                             LIMIT 1)                                 AS mainDefect,
                           (SELECT COALESCE(MAX(s.sumQty), 0)
                              FROM (SELECT SUM(dd3.qty) AS sumQty
                                      FROM prod_defect_record dd3
                                      JOIN base_equipment e3 ON e3.id = dd3.equipment_id
                                     WHERE e3.line_id = e.line_id
                                       AND dd3.record_date BETWEEN #{from} AND #{to}
                                     GROUP BY dd3.defect_type) s) AS mainDefectQty
                      FROM prod_defect_record dd
                      JOIN base_equipment e ON e.id = dd.equipment_id
                     WHERE dd.record_date BETWEEN #{from} AND #{to}
                     GROUP BY e.line_id
              ) d ON d.lineId = l.id
             WHERE l.status = 1
             ORDER BY l.id
            """)
    List<QualityDefectVO.ByLine> selectByLine(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("today") LocalDate today,
                                             @Param("yesterday") LocalDate yesterday,
                                             @Param("threshold") BigDecimal threshold);

    /**
     * 窗口内各产品的良品率 / 产量 / 缺陷件数。
     * <p>产品名在 {@code prod_plan.product_name}，良品来自 {@code prod_batch_record}，
     * 缺陷件数来自 {@code prod_defect_record}（经 {@code batch_id} 回到 {@code prod_plan} 取产品名）。
     */
    @Select("""
            SELECT p.product_name                                        AS product,
                   ROUND(SUM(b.good_qty) / NULLIF(SUM(b.good_qty + b.defect_qty), 0) * 100, 2) AS rate,
                   SUM(b.good_qty + b.defect_qty)                        AS qty,
                   (SELECT COALESCE(SUM(dd.qty), 0)
                      FROM prod_defect_record dd
                      JOIN prod_batch_record b2 ON b2.id = dd.batch_id
                      JOIN prod_plan p2         ON p2.id = b2.plan_id AND p2.is_deleted = 0
                     WHERE p2.product_name = p.product_name
                       AND dd.record_date BETWEEN #{from} AND #{to})      AS defectQty
              FROM prod_batch_record b
              JOIN prod_plan p ON p.id = b.plan_id AND p.is_deleted = 0
             WHERE b.is_deleted = 0
               AND p.plan_date BETWEEN #{from} AND #{to}
             GROUP BY p.product_name
             ORDER BY qty DESC
            """)
    List<QualityDefectVO.ByProduct> selectByProduct(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 窗口内各设备的缺陷件数 + 主要缺陷 + 健康度/风险/状态。
     * <p>只返回窗口内确实有缺陷记录的设备 —— 原型「缺陷来源设备」表里就是这个范围，
     * 把 48 台全列出来会让没缺陷的设备占满表格。
     */
    @Select("""
            SELECT e.equipment_name                                      AS equipment,
                   e.id                                                  AS equipmentId,
                   l.line_name                                           AS lineName,
                   (SELECT dd2.defect_type
                      FROM prod_defect_record dd2
                     WHERE dd2.equipment_id = e.id
                       AND dd2.record_date BETWEEN #{from} AND #{to}
                     GROUP BY dd2.defect_type
                     ORDER BY SUM(dd2.qty) DESC
                     LIMIT 1)                                            AS defectType,
                   SUM(d.qty)                                            AS qty,
                   ROUND(SUM(d.qty) / NULLIF(#{total}, 0) * 100, 2)      AS share,
                   e.health_score                                        AS health,
                   e.risk_level                                          AS riskLevel,
                   e.status                                              AS status
              FROM prod_defect_record d
              JOIN base_equipment e       ON e.id = d.equipment_id
              LEFT JOIN base_production_line l ON l.id = e.line_id
             WHERE d.record_date BETWEEN #{from} AND #{to}
               AND d.equipment_id IS NOT NULL
             GROUP BY e.id, e.equipment_name, l.line_name, e.health_score, e.risk_level, e.status
             ORDER BY qty DESC
            """)
    List<QualityDefectVO.ByEquipment> selectByEquipment(@Param("from") LocalDate from,
                                                        @Param("to") LocalDate to,
                                                        @Param("total") Integer total);

    // ==================== 3. 客诉 ====================

    /** 区间内客诉条数与处理中条数 */
    @Select("""
            SELECT COUNT(1)                                                  AS total,
                   COALESCE(SUM(CASE WHEN status = '处理中' THEN 1 ELSE 0 END), 0) AS processing
              FROM prod_complaint_record
             WHERE occur_date BETWEEN #{from} AND #{to}
            """)
    Map<String, Object> selectComplaintByRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}