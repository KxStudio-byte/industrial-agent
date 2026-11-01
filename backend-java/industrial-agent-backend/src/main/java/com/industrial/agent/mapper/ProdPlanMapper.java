package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.industrial.agent.dto.PlanQuery;
import com.industrial.agent.entity.ProdPlan;
import com.industrial.agent.vo.OeeAggVO;
import com.industrial.agent.vo.OeeLossVO;
import com.industrial.agent.vo.PlanRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 生产计划 Mapper。
 * <p>
 * <b>OEE 全在 SQL 里算</b>，不把明细拉到 Java 里再循环 —— 三个维度都要 join
 * 批次表和损失表，拉回来做等于把数据库的活搬到应用层，还白白多传几万行。
 * <p>
 * 三个指标的口径（务必与页面文案一致）：
 * <pre>
 * 开动率   = (计划工时 - 停机分钟) / 计划工时
 * 性能效率 = 产出合计 / (标准节拍 × 可用工时)
 * 质量指数 = 良品 / 产出合计
 * OEE     = 三者相乘
 * 其中"停机"只算 设备停机 / 换线时间 / 小停机 三类，
 * 质量损失与速度降低已经体现在良品率和性能效率里，再算一次就重复扣减了。
 * </pre>
 */
@Mapper
public interface ProdPlanMapper extends BaseMapper<ProdPlan> {

    /** 停机类损失（计入开动率分母） */
    String DOWN_LOSS_TYPES = "'设备停机','换线时间','小停机'";

    /**
     * 计划主体的公共 SELECT：联产线名，并带出该计划的损失分钟合计。
     * <p>
     * {@code lossMinutes} 用相关子查询而不是 LEFT JOIN + GROUP BY：
     * 一条计划可能挂几十条损失，join 出来会放大行数，分页的 total 也跟着错。
     */
    String BASE_SELECT = """
            SELECT p.id, p.plan_date AS planDate, p.line_id AS lineId, l.line_name AS lineName,
                   p.product_name AS product, p.target_qty AS targetQty, p.actual_qty AS actualQty,
                   CASE WHEN p.target_qty > 0 AND p.actual_qty IS NOT NULL
                        THEN ROUND(p.actual_qty * 100.0 / p.target_qty, 1) END AS achieve,
                   p.status, p.shift_name AS shiftName, p.plan_hours AS planHours,
                   p.standard_qty_per_hour AS standardQtyPerHour, p.ai_explanation AS aiExplanation,
                   (SELECT COALESCE(SUM(o.duration_minutes), 0)
                      FROM prod_oee_loss o
                      JOIN prod_batch_record b2 ON b2.id = o.batch_id AND b2.is_deleted = 0
                     WHERE b2.plan_id = p.id AND o.is_deleted = 0) AS lossMinutes
              FROM prod_plan p
              LEFT JOIN base_production_line l ON l.id = p.line_id
            """;

    /** 逻辑删除条件 */
    String NOT_DELETED = "p.is_deleted = 0";

    /* ==================== 列表 / 详情 ==================== */

    /**
     * 分页/全量查生产计划。
     * <p>
     * {@code ORDER BY} 用 <b>choose 分支而不是直接拼字符串</b>：排序字段来自用户输入，
     * 拼进去就是 SQL 注入面；更关键的是 MySQL 的 ORDER BY 不吃 {@code #{}} 占位符，
     * 所以必须枚举出所有允许的列名。
     */
    @Select("""
            <script>
            """ + BASE_SELECT + " WHERE " + NOT_DELETED + """
              <if test="q.status != null and q.status != ''"> AND p.status = #{q.status} </if>
              <if test="q.lineId != null"> AND p.line_id = #{q.lineId} </if>
              <if test="q.lineName != null and q.lineName != ''"> AND l.line_name = #{q.lineName} </if>
              <if test="q.shiftName != null and q.shiftName != ''"> AND p.shift_name = #{q.shiftName} </if>
              <if test="q.product != null and q.product != ''">
                AND p.product_name LIKE CONCAT('%', #{q.product}, '%')
              </if>
              <if test="q.keyword != null and q.keyword != ''">
                AND (p.product_name LIKE CONCAT('%', #{q.keyword}, '%')
                     OR p.ai_explanation LIKE CONCAT('%', #{q.keyword}, '%'))
              </if>
              <if test="q.startDate != null"> AND p.plan_date &gt;= #{q.startDate} </if>
              <if test="q.endDate != null"> AND p.plan_date &lt;= #{q.endDate} </if>
             ORDER BY
             <choose>
               <when test='"achieve".equals(q.effectiveSortField())'>
                 (CASE WHEN p.target_qty > 0 AND p.actual_qty IS NOT NULL
                       THEN p.actual_qty * 1.0 / p.target_qty END)
               </when>
               <when test='"planDate".equals(q.effectiveSortField())'> p.plan_date </when>
               <when test='"targetQty".equals(q.effectiveSortField())'> p.target_qty </when>
               <when test='"actualQty".equals(q.effectiveSortField())'> p.actual_qty </when>
               <when test='"status".equals(q.effectiveSortField())'>
                 FIELD(p.status, '已延期', '执行中', '待执行', '已完成')
               </when>
               <otherwise> p.id </otherwise>
             </choose>
             <choose>
               <when test='"asc".equals(q.effectiveSortOrder())'> ASC </when>
               <otherwise> DESC </otherwise>
             </choose>
             , p.plan_date DESC, p.line_id ASC
            </script>
            """)
    IPage<PlanRowVO> selectPlanPage(IPage<PlanRowVO> page, @Param("q") PlanQuery q);

    @Select(BASE_SELECT + " WHERE " + NOT_DELETED + " AND p.id = #{id}")
    PlanRowVO selectPlanDetail(@Param("id") Long id);

    /**
     * 同产线 + 同日期 + 同班次是否已有计划（新建去重）。
     * <p>
     * 逻辑删除的记录不算占用 —— 用户删掉后应能重建同一条。
     */
    @Select("""
            SELECT COUNT(1) FROM prod_plan
             WHERE line_id = #{lineId}
               AND plan_date = #{planDate}
               AND shift_name = #{shiftName}
               AND is_deleted = 0
            """)
    Long countByLineDateShift(@Param("lineId") Long lineId,
                              @Param("planDate") java.time.LocalDate planDate,
                              @Param("shiftName") String shiftName);

    /* ==================== OEE 聚合 ==================== */

    /**
     * OEE 集合口径聚合。
     * <p>
     * <b>为什么不直接 AVG(每日 OEE)</b>：见 {@link OeeAggVO} 的说明。
     * 这里把分子分母分别累加，Service 再相除。
     * <p>
     * {@code lineId}/{@code from}/{@code to} 全为 null 时统计全厂全时段。
     */
    @Select("""
            <script>
            SELECT
              COALESCE(SUM(agg.plan_min), 0)      AS planMinutes,
              COALESCE(SUM(agg.down_min), 0)      AS downMinutes,
              COALESCE(SUM(agg.plan_min) - SUM(agg.down_min), 0) AS availableMinutes,
              COALESCE(SUM(agg.cap_qty), 0)       AS capacityQty,
              COALESCE(SUM(agg.output_qty), 0)    AS outputQty,
              COALESCE(SUM(agg.good_qty), 0)      AS goodQty,
              COALESCE(SUM(agg.defect_qty), 0)    AS defectQty,
              COALESCE(SUM(agg.target_qty), 0)    AS targetQty,
              COALESCE(SUM(agg.actual_qty), 0)    AS actualQty
            FROM (
              SELECT
                p.plan_hours * 60 AS plan_min,
                COALESCE((SELECT SUM(o.duration_minutes) FROM prod_oee_loss o
                           JOIN prod_batch_record b2 ON b2.id = o.batch_id AND b2.is_deleted = 0
                          WHERE b2.plan_id = p.id AND o.is_deleted = 0
                            AND o.loss_type IN ('设备停机','换线时间','小停机')), 0) AS down_min,
                p.standard_qty_per_hour
                  * (p.plan_hours - COALESCE((SELECT SUM(o.duration_minutes) FROM prod_oee_loss o
                           JOIN prod_batch_record b2 ON b2.id = o.batch_id AND b2.is_deleted = 0
                          WHERE b2.plan_id = p.id AND o.is_deleted = 0
                            AND o.loss_type IN ('设备停机','换线时间','小停机')), 0) / 60.0) AS cap_qty,
                COALESCE((SELECT SUM(b.good_qty + b.defect_qty) FROM prod_batch_record b
                          WHERE b.plan_id = p.id AND b.is_deleted = 0), 0) AS output_qty,
                COALESCE((SELECT SUM(b.good_qty) FROM prod_batch_record b
                          WHERE b.plan_id = p.id AND b.is_deleted = 0), 0) AS good_qty,
                COALESCE((SELECT SUM(b.defect_qty) FROM prod_batch_record b
                          WHERE b.plan_id = p.id AND b.is_deleted = 0), 0) AS defect_qty,
                p.target_qty, COALESCE(p.actual_qty, 0) AS actual_qty
              FROM prod_plan p
              WHERE p.is_deleted = 0
              <if test="lineId != null"> AND p.line_id = #{lineId} </if>
              <if test="from != null"> AND p.plan_date &gt;= #{from} </if>
              <if test="to != null"> AND p.plan_date &lt;= #{to} </if>
            ) agg
            </script>
            """)
    OeeAggVO selectOeeAgg(@Param("lineId") Long lineId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    /**
     * 按产线分组的 OEE 聚合（生产总览 / OEE 分析的分产线表）。
     * <p>
     * 内层先按计划算好分子分母（与 {@link #selectOeeAgg} 同一套口径），
     * 外层再按产线累加 —— 算完再分组，避免分组后才发现口径不一致。
     */
    @Select("""
            <script>
            SELECT agg.line_id AS lineId, agg.line_name AS lineName,
              COALESCE(SUM(agg.plan_min), 0)      AS planMinutes,
              COALESCE(SUM(agg.down_min), 0)      AS downMinutes,
              COALESCE(SUM(agg.plan_min) - SUM(agg.down_min), 0) AS availableMinutes,
              COALESCE(SUM(agg.cap_qty), 0)       AS capacityQty,
              COALESCE(SUM(agg.output_qty), 0)    AS outputQty,
              COALESCE(SUM(agg.good_qty), 0)      AS goodQty,
              COALESCE(SUM(agg.defect_qty), 0)    AS defectQty,
              COALESCE(SUM(agg.target_qty), 0)    AS targetQty,
              COALESCE(SUM(agg.actual_qty), 0)    AS actualQty
            FROM (
              SELECT p.line_id, l.line_name,
                p.plan_hours * 60 AS plan_min,
                COALESCE((SELECT SUM(o.duration_minutes) FROM prod_oee_loss o
                           JOIN prod_batch_record b2 ON b2.id = o.batch_id AND b2.is_deleted = 0
                          WHERE b2.plan_id = p.id AND o.is_deleted = 0
                            AND o.loss_type IN ('设备停机','换线时间','小停机')), 0) AS down_min,
                p.standard_qty_per_hour
                  * (p.plan_hours - COALESCE((SELECT SUM(o.duration_minutes) FROM prod_oee_loss o
                           JOIN prod_batch_record b2 ON b2.id = o.batch_id AND b2.is_deleted = 0
                          WHERE b2.plan_id = p.id AND o.is_deleted = 0
                            AND o.loss_type IN ('设备停机','换线时间','小停机')), 0) / 60.0) AS cap_qty,
                COALESCE((SELECT SUM(b.good_qty + b.defect_qty) FROM prod_batch_record b
                          WHERE b.plan_id = p.id AND b.is_deleted = 0), 0) AS output_qty,
                COALESCE((SELECT SUM(b.good_qty) FROM prod_batch_record b
                          WHERE b.plan_id = p.id AND b.is_deleted = 0), 0) AS good_qty,
                COALESCE((SELECT SUM(b.defect_qty) FROM prod_batch_record b
                          WHERE b.plan_id = p.id AND b.is_deleted = 0), 0) AS defect_qty,
                p.target_qty, COALESCE(p.actual_qty, 0) AS actual_qty
              FROM prod_plan p
              LEFT JOIN base_production_line l ON l.id = p.line_id
              WHERE p.is_deleted = 0
              <if test="from != null"> AND p.plan_date &gt;= #{from} </if>
              <if test="to != null"> AND p.plan_date &lt;= #{to} </if>
            ) agg
            GROUP BY agg.line_id, agg.line_name
            ORDER BY agg.line_id
            </script>
            """)
    List<OeeAggVO> selectOeeAggByLine(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /**
     * 按时段（2 小时桶）分组的产出与产能，用于 OEE 走势图。
     * <p>
     * 时段从批次 {@code start_time} 取小时数再整除 2 —— 批次是唯一带时刻的数据，
     * 损失明细的 {@code occur_time} 虽然也有时刻，但它只覆盖"出问题的时段"，
     * 用它做分母会导致没问题的小时直接缺失。
     */
    @Select("""
            <script>
            SELECT b.equipment_id AS equipmentId, p.line_id AS lineId, l.line_name AS lineName,
                   HOUR(b.start_time) DIV 2 AS slot,
                   b.good_qty AS goodQty, b.defect_qty AS defectQty,
                   b.changeover_minutes AS changeoverMinutes
              FROM prod_batch_record b
              JOIN prod_plan p ON p.id = b.plan_id AND p.is_deleted = 0
              LEFT JOIN base_production_line l ON l.id = p.line_id
             WHERE b.is_deleted = 0
               AND b.start_time IS NOT NULL
               <if test="from != null"> AND p.plan_date &gt;= #{from} </if>
               <if test="to != null"> AND p.plan_date &lt;= #{to} </if>
            </script>
            """)
    List<java.util.Map<String, Object>> selectShiftBuckets(@Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);

    /**
     * 损失明细 TOP 榜（按时长倒序）。
     */
    @Select("""
            <script>
            SELECT o.id, o.loss_type AS lossType, o.attribution AS `trigger`,
                   o.duration_minutes AS minutes, o.impact_pct AS impactValue,
                   o.description, p.line_id AS lineId, l.line_name AS lineName,
                   e.equipment_name AS equipment, o.occur_time AS occurTime
              FROM prod_oee_loss o
              JOIN prod_batch_record b ON b.id = o.batch_id AND b.is_deleted = 0
              JOIN prod_plan p ON p.id = b.plan_id AND p.is_deleted = 0
              LEFT JOIN base_production_line l ON l.id = p.line_id
              LEFT JOIN base_equipment e ON e.id = b.equipment_id AND e.is_deleted = 0
             WHERE o.is_deleted = 0
               <if test="from != null"> AND p.plan_date &gt;= #{from} </if>
               <if test="to != null"> AND p.plan_date &lt;= #{to} </if>
               <if test="lineId != null"> AND p.line_id = #{lineId} </if>
             ORDER BY o.duration_minutes DESC, o.id DESC
             LIMIT #{limit}
            </script>
            """)
    List<OeeLossVO> selectLossTop(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("lineId") Long lineId,
                                  @Param("limit") int limit);

    /**
     * 按损失类型汇总（OEE 分析的损失构成饼图）。
     */
    @Select("""
            <script>
            SELECT o.loss_type AS lossType,
                   COALESCE(SUM(o.duration_minutes), 0) AS minutes,
                   COALESCE(SUM(o.impact_pct), 0) AS impactValue,
                   COUNT(*) AS cnt
              FROM prod_oee_loss o
              JOIN prod_batch_record b ON b.id = o.batch_id AND b.is_deleted = 0
              JOIN prod_plan p ON p.id = b.plan_id AND p.is_deleted = 0
             WHERE o.is_deleted = 0
               <if test="from != null"> AND p.plan_date &gt;= #{from} </if>
               <if test="to != null"> AND p.plan_date &lt;= #{to} </if>
               <if test="lineId != null"> AND p.line_id = #{lineId} </if>
             GROUP BY o.loss_type
             ORDER BY minutes DESC
            </script>
            """)
    List<java.util.Map<String, Object>> selectLossByType(@Param("from") LocalDate from,
                                                        @Param("to") LocalDate to,
                                                        @Param("lineId") Long lineId);

    /**
     * 某计划下的损失合计分钟（分类型），用于产线卡片的损失构成。
     */
    @Select("""
            SELECT o.loss_type AS lossType, COALESCE(SUM(o.duration_minutes), 0) AS minutes
              FROM prod_oee_loss o
              JOIN prod_batch_record b ON b.id = o.batch_id AND b.is_deleted = 0
             WHERE o.is_deleted = 0 AND b.plan_id = #{planId}
             GROUP BY o.loss_type
            """)
    List<java.util.Map<String, Object>> selectLossByTypeForPlan(@Param("planId") Long planId);
}