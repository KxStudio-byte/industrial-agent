package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.industrial.agent.dto.WorkOrderQuery;
import com.industrial.agent.entity.EqpWorkOrder;
import com.industrial.agent.vo.WorkOrderRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 维修工单 Mapper。
 * <p>
 * 和设备台账同样的路子：一条 SQL 把设备、产线、负责人 JOIN 进来，
 * 而不是查完工单再循环补字段。工单列表一页 10 条问题不大，
 * 但「全部工单」导出和不分页模式会一次拉几百条，N+1 立刻变成几百次往返。
 */
@Mapper
public interface EqpWorkOrderMapper extends BaseMapper<EqpWorkOrder> {

    /**
     * 联表投影。
     * <p>
     * <b>设备用 LEFT JOIN 而不是 INNER JOIN</b>：设备可能被逻辑删除，
     * 但历史工单仍然需要能打开 —— 用 INNER JOIN 会让这些工单从列表里凭空消失，
     * 现场拿着工单号却查不到，这比显示「设备已删除」糟得多。
     * <p>
     * <b>负责人信息由主查询的调用方补</b>（见 WorkOrderService.fillAssigneeNames），
     * 因为 {@code sys_user} 需要过滤 {@code is_deleted = 0}，这里 JOIN 进来却查到已删除用户时
     * 会显示 null 姓名，而工单本身是好的。改成单独批量查更可控。
     */
    String BASE_SELECT = """
            SELECT w.id,
                   w.wo_no           AS woNo,
                   w.equipment_id    AS equipmentId,
                   e.equipment_name  AS equipmentName,
                   e.equipment_code  AS equipmentCode,
                   e.model,
                   e.manufacturer    AS vendor,
                   e.status          AS equipmentStatus,
                   e.health_score    AS healthScore,
                   e.risk_level      AS riskLevel,
                   l.line_name       AS lineName,
                   w.fault_type      AS faultType,
                   w.priority,
                   w.description,
                   w.source,
                   w.status,
                   w.assignee_id     AS assigneeId,
                   w.create_time     AS createTime,
                   w.update_time     AS updateTime
              FROM eqp_work_order w
              LEFT JOIN base_equipment e       ON e.id = w.equipment_id
              LEFT JOIN base_production_line l ON l.id = e.line_id
             """;

    /**
     * 逻辑删除过滤条件。
     * <p>
     * <b>必须手写</b>：{@code @TableLogic} 只对 MyBatis-Plus 自动生成的方法生效，
     * 对 {@code @Select} 手写 SQL 完全不起作用。这条曾经在设备模块踩过一次
     * （删除了还能查到），工单这里不能再踩。
     */
    String NOT_DELETED = "w.is_deleted = 0";

    /**
     * 条件分页查询。
     * <p>
     * 第一个条件<b>不带</b> {@code AND}：{@code <where>} 会剥掉紧随其后的 AND，
     * 带了反而会把 WHERE 弄丢。
     * <p>
     * 排序：{@code priority} 不能按字符串排（"紧急" &gt; "高" &gt; "中" &gt; "低" 的字典序是乱的），
     * 用 {@code FIELD()} 显式给顺序，让「紧急」永远在最前面。
     */
    @Select("<script>"
            + BASE_SELECT
            + "<where>"
            + "  " + NOT_DELETED
            + "  <if test=\"q.status != null and q.status != ''\"> AND w.status = #{q.status} </if>"
            + "  <if test=\"q.priority != null and q.priority != ''\"> AND w.priority = #{q.priority} </if>"
            + "  <if test=\"q.faultType != null and q.faultType != ''\"> AND w.fault_type = #{q.faultType} </if>"
            + "  <if test=\"q.equipmentId != null\"> AND w.equipment_id = #{q.equipmentId} </if>"
            + "  <if test=\"q.assigneeId != null\"> AND w.assignee_id = #{q.assigneeId} </if>"
            + "  <if test=\"q.lineName != null and q.lineName != ''\"> AND l.line_name = #{q.lineName} </if>"
            + "  <if test=\"q.source != null and q.source != ''\"> AND w.source LIKE CONCAT('%', #{q.source}, '%') </if>"
            + "  <if test=\"q.startTime != null\"> AND w.create_time &gt;= #{q.startTime} </if>"
            + "  <if test=\"q.endTime != null\"> AND w.create_time &lt;= #{q.endTime} </if>"
            + "  <if test=\"q.keyword != null and q.keyword != ''\">"
            + "    AND (w.wo_no LIKE CONCAT('%', #{q.keyword}, '%')"
            + "      OR e.equipment_name LIKE CONCAT('%', #{q.keyword}, '%')"
            + "      OR e.equipment_code LIKE CONCAT('%', #{q.keyword}, '%'))"
            + "  </if>"
            + "</where>"
            + " ORDER BY "
            + "  <choose>"
            + "    <when test=\"q.sortField == 'priority'\"> FIELD(w.priority,'紧急','高','中','低') </when>"
            + "    <when test=\"q.sortField == 'status'\"> FIELD(w.status,'待处理','维修中','待验证','已完成') </when>"
            + "    <when test=\"q.sortField == 'id'\"> w.id </when>"
            + "    <otherwise> w.create_time </otherwise>"
            + "  </choose>"
            + "  <choose>"
            + "    <when test=\"q.sortOrder == 'asc'\"> ASC </when>"
            + "    <otherwise> DESC </otherwise>"
            + "  </choose>"
            + "  , w.id DESC"
            + "</script>")
    IPage<WorkOrderRowVO> selectWorkOrderPage(IPage<WorkOrderRowVO> page, @Param("q") WorkOrderQuery q);

    /**
     * 单张工单（与列表同一套投影，保证字段完全一致）。
     * <p>
     * {@link #BASE_SELECT} 只到 JOIN 为止，这里必须显式补 {@code WHERE}：
     * 少写的话 {@code AND w.id = #{id}} 会被并进最后那个 LEFT JOIN 的 ON 条件里。
     */
    @Select(BASE_SELECT + " WHERE " + NOT_DELETED + " AND w.id = #{id} ")
    WorkOrderRowVO selectWorkOrderDetail(@Param("id") Long id);

    /**
     * 按日期前缀统计已生成的工单数（用于生成流水号）。
     * <p>
     * 统计的是 {@code wo_no LIKE 'WO-20260205-%'} 的全部行，<b>包含已逻辑删除的</b>——
     * 因为 {@code uk_wo_no} 是物理唯一键，删掉的编号照样占位，跳过已删除的会导致撞号。
     * 所以这里直接用 {@code is_deleted} 不过滤的 COUNT。
     */
    @Select("SELECT COUNT(*) FROM eqp_work_order WHERE wo_no LIKE CONCAT(#{prefix}, '%')")
    int countByNoPrefix(@Param("prefix") String prefix);

    /** 当天最大流水号（返回形如 WO-20260205-018；没有则 null） */
    @Select("SELECT MAX(wo_no) FROM eqp_work_order WHERE wo_no LIKE CONCAT(#{prefix}, '%')")
    String maxNoByPrefix(@Param("prefix") String prefix);

    /** 按编号计数（唯一性兜底校验，逻辑删除的记录也计入） */
    @Select("SELECT COUNT(*) FROM eqp_work_order WHERE wo_no = #{woNo}")
    int countByWoNo(@Param("woNo") String woNo);

    /**
     * 今日工单统计。
     * <p>
     * 一次查回四个数，不做四次 COUNT —— 工单页的 KPI 卡在每次刷新都会调这个接口。
     * 用 {@code DATE(create_time) = CURDATE()}，虽然会让索引失效，
     * 但工单表是按天滚动的，单日数据量很小，用区间写法反而更啰嗦。
     */
    @Select("SELECT COUNT(*) FROM eqp_work_order WHERE is_deleted = 0 AND DATE(create_time) = #{day}")
    int countByDay(@Param("day") LocalDate day);

    /** 按状态计数（全量，不限日期） */
    @Select("SELECT COUNT(*) FROM eqp_work_order WHERE is_deleted = 0 AND status = #{status}")
    int countByStatus(@Param("status") String status);
}