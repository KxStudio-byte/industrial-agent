package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.industrial.agent.entity.BaseEquipment;
import com.industrial.agent.vo.EquipmentRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备台账 Mapper。
 * <p>
 * 列表与详情都走「一条 SQL 把产线名、实时值、预测值、最近诊断 JOIN 进来」的路子，
 * 而不是查完主表再循环补字段 —— 48 台设备时 N+1 就是 48×4 条 SQL，
 * 等到设备上千台时接口会直接超时。
 */
@Mapper
public interface BaseEquipmentMapper extends BaseMapper<BaseEquipment> {

    /**
     * 联表查询用的公共投影。
     * <p>
     * <b>最近诊断</b>用相关子查询而不是 JOIN：{@code ai_insight} 里一台设备可能有多条洞察，
     * JOIN 会让主表行数翻倍、分页总数算错。这里取「最新一条」即可。
     * <p>
     * <b>为什么要手写 {@code e.is_deleted = 0}</b>：{@code @TableLogic} 只对 MyBatis-Plus 自动
     * 生成的方法（{@code selectById} / {@code selectPage} / {@code deleteById}…）生效，
     * 对 {@code @Select} 手写 SQL <b>完全不起作用</b>。不加这个条件，删除过的设备在列表和
     * 详情里照样查得出来 —— 表现为「删除成功但还能看到」。
     */
    String BASE_SELECT = """
            SELECT e.id,
                   e.equipment_code  AS code,
                   e.equipment_name  AS name,
                   e.equipment_type  AS type,
                   e.model,
                   e.manufacturer    AS vendor,
                   e.line_id         AS lineId,
                   l.line_name       AS lineName,
                   e.status,
                   e.health_score    AS health,
                   e.risk_level      AS riskLevel,
                   e.last_exception  AS lastException,
                   e.create_time     AS createTime,
                   e.update_time     AS updateTime,
                   r.temperature,
                   r.vibration,
                   r.current,
                   r.record_time     AS recordTime,
                   p.h24,
                   p.d7,
                   p.d30,
                   p.model_version   AS predictModel,
                   (SELECT i.ai_diagnosis FROM ai_insight i
                     WHERE i.object_type = '设备' AND i.object_id = e.id
                     ORDER BY i.create_time DESC, i.id DESC LIMIT 1) AS diagnosis
              FROM base_equipment e
              LEFT JOIN base_production_line l ON l.id = e.line_id
              LEFT JOIN eqp_runtime_data r     ON r.equipment_id = e.id
              LEFT JOIN eqp_fault_predict p    ON p.equipment_id = e.id
             """;

    /**
     * 逻辑删除过滤条件。
     * <p>
     * <b>为什么要手写这一句</b>：{@code @TableLogic} 只对 MyBatis-Plus 自动生成的方法
     * （{@code selectById} / {@code selectPage} / {@code deleteById}…）生效，
     * 对 {@code @Select} 手写 SQL <b>完全不起作用</b>。不加这个条件，删除过的设备在列表和
     * 详情里照样查得出来 —— 表现为「删除成功但还能看到」。
     * <p>
     * <b>为什么单独抽成常量而不是直接写进 {@link #BASE_SELECT}</b>：
     * {@code <where>} 标签会吃掉紧随其后的第一个 {@code AND}/{@code OR}，
     * 如果 BASE_SELECT 自带 WHERE，后面再跟 {@code <where>} 拼出来的就是
     * {@code WHERE e.is_deleted=0 line_id=...} —— 缺了 AND，直接语法错误。
     * 所以 BASE_SELECT 只管 JOIN，删除过滤统一由各查询在 {@code <where>} 的第一个分支里带上。
     */
    String NOT_DELETED = "e.is_deleted = 0";

    /**
     * 条件分页查询。
     * <p>
     * 条件里每个参数都做了 {@code null} 判断，所以 {@code lineId} / {@code status} 这些
     * 即使不传也不影响结果。{@code keyword} 同时匹配编号 → 用 LIKE 而不是 =，
     * 前端搜索框是「边输边搜」的语义。
     * <p>
     * 第一个条件 <b>不带</b> {@code AND}：{@code <where>} 会自动补 {@code WHERE}，
     * 带 {@code AND} 反而会被它剥掉，导致 {@code WHERE} 丢失。
     */
    @Select("<script>"
            + BASE_SELECT
            + "<where>"
            + "  " + NOT_DELETED
            + "  <if test=\"q.lineId != null\"> AND e.line_id = #{q.lineId} </if>"
            + "  <if test=\"q.lineName != null and q.lineName != ''\"> AND l.line_name = #{q.lineName} </if>"
            + "  <if test=\"q.status != null and q.status != ''\"> AND e.status = #{q.status} </if>"
            + "  <if test=\"q.riskLevel != null and q.riskLevel != ''\"> AND e.risk_level = #{q.riskLevel} </if>"
            + "  <if test=\"q.equipmentType != null and q.equipmentType != ''\"> AND e.equipment_type = #{q.equipmentType} </if>"
            + "  <if test=\"q.keyword != null and q.keyword != ''\">"
            + "    AND (e.equipment_name LIKE CONCAT('%', #{q.keyword}, '%')"
            + "      OR e.equipment_code LIKE CONCAT('%', #{q.keyword}, '%')"
            + "      OR e.manufacturer  LIKE CONCAT('%', #{q.keyword}, '%'))"
            + "  </if>"
            + "  <if test=\"q.minHealth != null\"> AND e.health_score &gt;= #{q.minHealth} </if>"
            + "  <if test=\"q.maxHealth != null\"> AND e.health_score &lt;= #{q.maxHealth} </if>"
            + "</where>"
            + " ORDER BY "
            + "  <choose>"
            + "    <when test=\"q.sortField == 'health'\"> e.health_score </when>"
            + "    <when test=\"q.sortField == 'code'\"> e.equipment_code </when>"
            + "    <when test=\"q.sortField == 'updateTime'\"> e.update_time </when>"
            + "    <otherwise> e.id </otherwise>"
            + "  </choose>"
            + "  <choose>"
            + "    <when test=\"q.sortOrder == 'asc'\"> ASC </when>"
            + "    <otherwise> DESC </otherwise>"
            + "  </choose>"
            + "  , e.id ASC"
            + "</script>")
    IPage<EquipmentRowVO> selectEquipmentPage(IPage<EquipmentRowVO> page,
                                              @Param("q") com.industrial.agent.dto.EquipmentQuery q);

    /**
     * 单台设备详情（与列表同一套投影，保证字段完全一致）。
     * <p>
     * {@link #BASE_SELECT} 只到 JOIN 为止，这里必须显式补 {@code WHERE}：
     * 少写的话 {@code AND e.id = #{id}} 会被并进最后那个 LEFT JOIN 的 ON 条件里，
     * 变成「整表扫 + JOIN 时过滤」，查无关设备也会命中，逻辑删除的设备也能查出来。
     */
    @Select(BASE_SELECT + " WHERE " + NOT_DELETED + " AND e.id = #{id} ")
    EquipmentRowVO selectEquipmentDetail(@Param("id") Long id);

    /** 按编号计数（新增/修改时做唯一性校验，逻辑删除的数据也计入，因为库上有唯一键） */
    @Select("<script>SELECT COUNT(*) FROM base_equipment WHERE equipment_code = #{code}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    int countByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** 按时段统计（运行数据分析用） */
    @Select("SELECT COUNT(*) FROM eqp_runtime_history WHERE equipment_id = #{equipmentId} "
            + "AND record_time &gt;= #{from} AND record_time &lt;= #{to}")
    int countHistory(@Param("equipmentId") Long equipmentId,
                     @Param("from") java.time.LocalDateTime from,
                     @Param("to") java.time.LocalDateTime to);
}