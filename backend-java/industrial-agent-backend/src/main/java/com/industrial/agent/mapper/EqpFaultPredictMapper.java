package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.industrial.agent.entity.EqpFaultPredict;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 故障预测 Mapper。
 */
@Mapper
public interface EqpFaultPredictMapper extends BaseMapper<EqpFaultPredict> {

    /** 删除设备时清理其预测记录 */
    @Delete("DELETE FROM eqp_fault_predict WHERE equipment_id = #{equipmentId}")
    int deleteByEquipmentId(@Param("equipmentId") Long equipmentId);
}