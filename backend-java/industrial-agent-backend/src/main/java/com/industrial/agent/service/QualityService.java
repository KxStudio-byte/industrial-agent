package com.industrial.agent.service;

import com.industrial.agent.dto.QualityQuery;
import com.industrial.agent.exception.BusinessException;
import com.industrial.agent.mapper.QualityMapper;
import com.industrial.agent.vo.QualityDefectVO;
import com.industrial.agent.vo.QualitySummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 质量中心统计服务。
 *
 * <p>只做「读 + 计算口径」，不写任何业务数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityService {

    private final QualityMapper qualityMapper;

    /** AI 分析不可用的统一说明，前端原样展示 */
    private static final String AI_UNAVAILABLE =
            "AI 质量分析需要调用工业大模型服务（ai-service），该服务尚未部署，本期暂不提供自动归因结论。";

    // ==================== 质量总览 KPI ====================

    /**
     * 质量总览顶部 4 个 KPI。
     *
     * <p><b>口径：</b>
     * <ul>
     *   <li>良品率 / 缺陷数 → 单日（{@code query.date}），数据源
     *       {@code prod_batch_record} 的数量口径</li>
     *   <li>批次合格率 → 单日，批次口径（{@code COUNT} 之比）</li>
     *   <li>客诉 → 本周（周一至周日），数据源 {@code prod_complaint_record}</li>
     * </ul>
     */
    public QualitySummaryVO summary(QualityQuery query) {
        query.resolveDefaults();
        LocalDate today = query.getToDate();
        LocalDate yesterday = query.getYesterday();

        Map<String, Object> qty = qualityMapper.selectQtyByDate(today);
        long good = longOf(qty, "good");
        long defect = longOf(qty, "defect");

        // ---------- 良品率 = 良品 / (良品 + 不良)，数量口径 ----------
        BigDecimal yieldRate = rate(good, good + defect);
        QuantityQuality yesterdayQty = yesterdayQuality(yesterday);
        String yieldDelta = delta(yieldRate, yesterdayQty.yieldRate());

        // ---------- 缺陷数 ----------
        Map<String, Object> yq = qualityMapper.selectQtyByDate(yesterday);
        long yDefect = longOf(yq, "defect");
        String defectsNote = String.format("今日 %s 件，较昨日 %s %s 件",
                fmt(defect), diffSign(defect, yDefect), fmt(Math.abs(defect - yDefect)));

        // ---------- 批次合格率 = 合格批次 / 总批次，批次口径（按阈值判定） ----------
        BigDecimal threshold = query.getBatchPassThreshold();
        Map<String, Object> batch = qualityMapper.selectBatchByDate(today, threshold);
        long totalBatch = longOf(batch, "total");
        long qualified = longOf(batch, "qualified");
        BigDecimal batchRate = rate(qualified, totalBatch);
        String batchRateNote = totalBatch == 0
                ? "当日无批次"
                : String.format("合格 %d / 共 %d 批 · 不良率 ≤ %s%% 判定合格", qualified, totalBatch, plain(threshold));

        // ---------- 客诉：本周 ----------
        LocalDate weekStart = query.getWeekStart();
        LocalDate weekEnd = query.getWeekEnd();
        Map<String, Object> complaint = qualityMapper.selectComplaintByRange(weekStart, weekEnd);
        long complaintTotal = longOf(complaint, "total");
        long complaintProcessing = longOf(complaint, "processing");
        String complaintsNote = complaintTotal == 0
                ? "本周无客诉"
                : String.format("本周 %d 条，处理中 %d 条", complaintTotal, complaintProcessing);

        QualitySummaryVO vo = new QualitySummaryVO();
        vo.setYieldRate(yieldRate);
        vo.setYieldDelta(yieldDelta);
        vo.setDefects((int) defect);
        vo.setDefectsNote(defectsNote);
        // 合格率与良品率在数量口径下是同一个算式（良品 / 总检），保留独立字段是为了
        // 将来若引入「免检/复检」概念时只改一处，不牵动良品率
        vo.setPassRate(yieldRate);
        vo.setBatchRate(batchRate);
        vo.setBatchRateNote(batchRateNote);
        vo.setBatchPassThreshold(threshold);
        vo.setComplaints((int) complaintTotal);
        vo.setComplaintsNote(complaintsNote);
        vo.setAiSummary(null);
        vo.setAiSummaryUnavailableReason(AI_UNAVAILABLE);
        vo.setScope(String.format("良品率/缺陷数/批次合格率：%s · 客诉：%s ~ %s", today, weekStart, weekEnd));
        log.info("质量总览 KPI：date={} good={} defect={} yieldRate={} batchRate={} complaints={}",
                today, good, defect, yieldRate, batchRate, complaintTotal);
        return vo;
    }

    // ==================== 缺陷统计 ====================

    /**
     * 缺陷 Top + 产线/产品/设备三个维度。
     *
     * <p>窗口为 {@code query.date} 往前 {@code days} 天（含当天）。
     */
    public QualityDefectVO defects(QualityQuery query) {
        query.resolveDefaults();
        LocalDate from = query.getFromDate();
        LocalDate to = query.getToDate();

        Integer total = qualityMapper.selectTotalDefectQty(from, to);
        int totalDefect = total == null ? 0 : total;

        List<QualityDefectVO.Top> top = qualityMapper.selectTopDefects(from, to);
        // Top.rate 的语义是「不良构成占比」，分母是窗口内总缺陷件数
        if (top != null) {
            for (QualityDefectVO.Top t : top) {
                t.setRate(rate(t.getQty() == null ? 0 : t.getQty(), totalDefect));
            }
        }

        List<QualityDefectVO.ByLine> byLine =
                qualityMapper.selectByLine(from, to, to, to.minusDays(1), query.getBatchPassThreshold());
        List<QualityDefectVO.ByProduct> byProduct = qualityMapper.selectByProduct(from, to);
        List<QualityDefectVO.ByEquipment> byEquipment = qualityMapper.selectByEquipment(from, to, totalDefect);

        QualityDefectVO vo = new QualityDefectVO();
        vo.setTop(top);
        vo.setByLine(byLine);
        vo.setByProduct(byProduct);
        vo.setByEquipment(byEquipment);
        vo.setScope(String.format("统计范围 %s ~ %s（共 %d 件缺陷）", from, to, totalDefect));
        return vo;
    }

    // ==================== 内部工具 ====================

    /** 昨日数量口径的良品率，用于「较昨日」 */
    private QuantityQuality yesterdayQuality(LocalDate yesterday) {
        Map<String, Object> m = qualityMapper.selectQtyByDate(yesterday);
        long g = longOf(m, "good");
        long d = longOf(m, "defect");
        if (g + d == 0) {
            return new QuantityQuality(null);
        }
        return new QuantityQuality(rate(g, g + d));
    }

    private record QuantityQuality(BigDecimal yieldRate) {
    }

    /** 百分比，保留 2 位；分母为 0 时返回 0 而不是 null（前端表格不做空值判断） */
    private static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /** 「较昨日」文案；昨日无数据时明确说明，不拿今日数据糊弄 */
    private static String delta(BigDecimal today, BigDecimal yesterday) {
        if (yesterday == null) {
            return "昨日无数据";
        }
        BigDecimal d = today.subtract(yesterday).setScale(2, RoundingMode.HALF_UP);
        String sign = d.signum() > 0 ? "+" : "";
        return String.format("较昨日 %s%spct", sign, d.toPlainString());
    }

    private static String diffSign(long today, long yesterday) {
        long d = today - yesterday;
        if (d > 0) {
            return "+";
        }
        return d < 0 ? "-" : "±";
    }

    /** 千分位：1234 → 1,234 */
    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    /** 去掉无意义的小数尾巴：4.0 → 4，4.5 → 4.5 */
    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static long longOf(Map<String, Object> m, String key) {
        if (m == null) {
            return 0L;
        }
        Object v = m.get(key);
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}