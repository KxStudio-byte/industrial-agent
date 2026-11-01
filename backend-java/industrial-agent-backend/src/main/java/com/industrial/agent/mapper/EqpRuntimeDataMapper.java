package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.industrial.agent.entity.EqpRuntimeData;
import com.industrial.agent.vo.RuntimePointVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实时运行数据 Mapper。
 */
@Mapper
public interface EqpRuntimeDataMapper extends BaseMapper<EqpRuntimeData> {

    /** 按设备 ID 查最新实时值 */
    @Select("SELECT equipment_id AS equipmentId, temperature, vibration, current, "
            + "record_time AS recordTime FROM eqp_runtime_data WHERE equipment_id = #{equipmentId}")
    RuntimePointVO selectLatest(@Param("equipmentId") Long equipmentId);

    /**
     * 上报运行数据（幂等）。
     * <p>
     * 表上有 {@code uk_equipment_id} 唯一键，同一台设备重复上报只保留最新一条，
     * 用 {@code ON DUPLICATE KEY UPDATE} 一步到位，避免「先查后插」的并发重复。
     */
    @Insert("INSERT INTO eqp_runtime_data (equipment_id, temperature, vibration, current, record_time) "
            + "VALUES (#{d.equipmentId}, #{d.temperature}, #{d.vibration}, #{d.current}, "
            + "IFNULL(#{d.recordTime}, NOW())) "
            + "ON DUPLICATE KEY UPDATE temperature = VALUES(temperature), "
            + "vibration = VALUES(vibration), current = VALUES(current), "
            + "record_time = VALUES(record_time), update_time = NOW()")
    int upsert(@Param("d") RuntimePointVO d);

    /** 顺带写一条历史，供趋势图使用 */
    @Insert("INSERT INTO eqp_runtime_history (equipment_id, temperature, vibration, current, record_time) "
            + "SELECT equipment_id, temperature, vibration, current, record_time "
            + "FROM eqp_runtime_data WHERE equipment_id = #{equipmentId}")
    int archiveLatest(@Param("equipmentId") Long equipmentId);

    /** 直接追加一条历史点（补录数据用） */
    @Insert("INSERT INTO eqp_runtime_history (equipment_id, temperature, vibration, current, record_time) "
            + "VALUES (#{p.equipmentId}, #{p.temperature}, #{p.vibration}, #{p.current}, #{p.recordTime})")
    int insertHistory(@Param("p") RuntimePointVO p);

    /** 删除设备时清理运行数据（物理删除，表无 is_deleted 列） */
    @Insert("DELETE FROM eqp_runtime_data WHERE equipment_id = #{equipmentId}")
    int deleteByEquipmentId(@Param("equipmentId") Long equipmentId);

    /**
     * 某时段的历史点，升序。
     * <p>
     * 注意：这里的 {@code >=} / {@code <=} 必须写原始符号，<b>不能</b>写成 XML 的
     * {@code &gt;} / {@code &lt;} —— 那些转义只在 {@code <script>} 标签内部才会被解析，
     * 直接写 {@code @Select("... &gt;= ...")} 会原样拼进 SQL，MySQL 报语法错误，
     * 接口表现为 {@code code=500 服务器内部错误}。
     */
    @Select("SELECT record_time AS recordTime, temperature, vibration, current "
            + "FROM eqp_runtime_history WHERE equipment_id = #{equipmentId} "
            + "AND record_time >= #{from} AND record_time <= #{to} "
            + "ORDER BY record_time ASC")
    List<RuntimePointVO> selectHistory(@Param("equipmentId") Long equipmentId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}