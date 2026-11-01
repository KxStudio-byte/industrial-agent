package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.industrial.agent.entity.BaseProductionLine;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产线 Mapper。
 */
@Mapper
public interface BaseProductionLineMapper extends BaseMapper<BaseProductionLine> {
}