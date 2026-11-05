package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.industrial.agent.entity.SysWorkshop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车间 Mapper（只读使用）。
 */
@Mapper
public interface SysWorkshopMapper extends BaseMapper<SysWorkshop> {
}