package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.industrial.agent.entity.EnergyMeter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 能耗读表 Mapper。
 * <p>
 * 聚合全部走 MyBatis-Plus 的 {@code selectMaps} + QueryWrapper（groupBy / select），
 * 不写 XML —— 这些查询的条件是动态拼的，XML 里堆一堆 {@code <if>} 反而更难维护。
 */
@Mapper
public interface EnergyMeterMapper extends BaseMapper<EnergyMeter> {
}