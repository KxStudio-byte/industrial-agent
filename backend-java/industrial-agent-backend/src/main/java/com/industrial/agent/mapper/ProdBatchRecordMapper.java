package com.industrial.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.industrial.agent.entity.ProdBatchRecord;
import com.industrial.agent.vo.BatchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 生产批次 Mapper。
 */
@Mapper
public interface ProdBatchRecordMapper extends BaseMapper<ProdBatchRecord> {

    /**
     * 某计划下的批次明细（带设备名、不良率、时长）。
     * <p>
     * 不良率在 SQL 里算：分母是「良品 + 不良」而不是计划量，
     * 因为批次可能因为停机而少产，用计划量做分母会把停机算成不良。
     */
    @Select("""
            SELECT b.id, b.plan_id AS planId, b.batch_no AS batchNo,
                   b.equipment_id AS equipmentId,
                   COALESCE(e.equipment_name, '—') AS equipmentName,
                   b.good_qty AS goodQty, b.defect_qty AS defectQty,
                   (b.good_qty + b.defect_qty) AS totalQty,
                   CASE WHEN (b.good_qty + b.defect_qty) > 0
                        THEN ROUND(b.defect_qty * 100.0 / (b.good_qty + b.defect_qty), 2)
                        ELSE 0 END AS defectRate,
                   b.changeover_minutes AS changeoverMinutes,
                   b.start_time AS startTime, b.end_time AS endTime,
                   CASE WHEN b.start_time IS NOT NULL AND b.end_time IS NOT NULL
                        THEN TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time) END AS durationMinutes
              FROM prod_batch_record b
              LEFT JOIN base_equipment e ON e.id = b.equipment_id AND e.is_deleted = 0
             WHERE b.is_deleted = 0 AND b.plan_id = #{planId}
             ORDER BY b.id
            """)
    List<BatchVO> selectByPlanId(@Param("planId") Long planId);

    /** 某计划的批次数量（产线卡片上的批次进度） */
    @Select("SELECT COUNT(*) FROM prod_batch_record WHERE plan_id = #{planId} AND is_deleted = 0")
    int countByPlanId(@Param("planId") Long planId);
}