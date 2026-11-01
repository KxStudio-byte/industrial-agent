package com.industrial.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.industrial.agent.common.PageResult;
import com.industrial.agent.common.ResultCode;
import com.industrial.agent.dto.PlanCreateRequest;
import com.industrial.agent.dto.PlanQuery;
import com.industrial.agent.entity.BaseProductionLine;
import com.industrial.agent.entity.ProdPlan;
import com.industrial.agent.exception.BusinessException;
import com.industrial.agent.mapper.BaseProductionLineMapper;
import com.industrial.agent.mapper.ProdBatchRecordMapper;
import com.industrial.agent.mapper.ProdPlanMapper;
import com.industrial.agent.vo.BatchVO;
import com.industrial.agent.vo.OeeAggVO;
import com.industrial.agent.vo.OeeLineVO;
import com.industrial.agent.vo.OeeLossVO;
import com.industrial.agent.vo.OeeShiftPointVO;
import com.industrial.agent.vo.OeeSummaryVO;
import com.industrial.agent.vo.PlanDetailVO;
import com.industrial.agent.vo.PlanRowVO;
import com.industrial.agent.vo.ProductionLineVO;
import com.industrial.agent.vo.ProductionOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产运营业务逻辑（生产总览 / 生产计划 / OEE 分析）。
 * <p>
 * <b>本类最重要的一条原则：OEE 只有一个算法入口。</b>
 * 三个页面（总览 KPI、产线卡片、OEE 分析）都调用 {@link #toPercent} 系列方法，
 * 分母口径、四舍五入都只有一处 —— 否则总览写 87.6% 而分析页写 88%，
 * 用户第一反应是"数据错了"，实际是两个地方各自四舍五入。
 * <p>
 * OEE 口径（与 Mapper 注释一致）：
 * <pre>
 * 开动率   = (计划工时 - 停机分钟) / 计划工时
 * 性能效率 = 产出合计 / (标准节拍 × 可用工时)
 * 质量指数 = 良品 / 产出合计
 * OEE     = 开动率 × 性能效率 × 质量指数
 * </pre>
 * <b>为什么不用"平均每日 OEE"</b>：小产量的一天和满产的一天权重会相同，
 * 结果失真。这里一律先把分子分母累加，最后才相除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionService {

    /* ==================== 常量 ==================== */

    /** 默认统计窗口：最近 30 天（与原型"近 30 天"一致） */
    private static final int DEFAULT_WINDOW_DAYS = 30;

    /** OEE 目标 */
    public static final BigDecimal OEE_TARGET = new BigDecimal("90");

    /** 开动率目标 */
    public static final BigDecimal AVAILABILITY_TARGET = new BigDecimal("95");

    /** 性能效率目标 */
    public static final BigDecimal PERFORMANCE_TARGET = new BigDecimal("96");

    /** 质量指数目标 */
    public static final BigDecimal QUALITY_TARGET = new BigDecimal("98");

    /** 时段桶（2 小时一格，8:00—18:00 共 5 格） */
    private static final int[] SHIFT_SLOT_START_HOURS = {8, 10, 12, 14, 16};

    private static final String[] SHIFT_SLOT_LABELS = {
            "08:00—10:00", "10:00—12:00", "12:00—14:00", "14:00—16:00", "16:00—18:00"
    };

    private final ProdPlanMapper planMapper;
    private final ProdBatchRecordMapper batchMapper;
    private final BaseProductionLineMapper lineMapper;

    /* ============================================================
     * 1. 生产计划列表 / 详情
     * ============================================================ */

    public Object listPlans(PlanQuery query) {
        query.normalize();
        query.validate();

        if (query.isPaged()) {
            Page<PlanRowVO> page = new Page<>(query.getPage(), query.getSize());
            IPage<PlanRowVO> result = planMapper.selectPlanPage(page, query);
            return PageResult.of(result.getTotal(), query.getPage(), query.getSize(), result.getRecords());
        }
        Page<PlanRowVO> page = new Page<>(1, 500, false);
        IPage<PlanRowVO> result = planMapper.selectPlanPage(page, query);
        return result.getRecords();
    }

    /**
     * 新建生产计划。
     * <p>
     * 落库规则：
     * <ul>
     *   <li>{@code actualQty = 0}、{@code status = 待执行} —— 新建计划尚未开始执行；</li>
     *   <li>{@code aiExplanation} 留空，等后续按实际执行情况生成；</li>
     *   <li>同产线 + 同日期 + 同班次不允许重复建计划 —— 否则 OEE 分母会翻倍。</li>
     * </ul>
     *
     * @return 新建计划的详情（与 {@link #planDetail} 同结构，前端可直接刷新列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public PlanDetailVO createPlan(PlanCreateRequest req) {
        // 1. 产线必须存在
        BaseProductionLine line = lineMapper.selectById(req.getLineId());
        if (line == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产线不存在，lineId=" + req.getLineId());
        }

        // 2. 同产线 + 同日期 + 同班次唯一
        Long dup = planMapper.countByLineDateShift(req.getLineId(), req.getPlanDate(), req.getShiftName());
        if (dup != null && dup > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    String.format("%s 在 %s %s 已有生产计划，请勿重复创建",
                            line.getLineName(), req.getPlanDate(), req.getShiftName()));
        }

        // 3. 组装并落库
        ProdPlan entity = new ProdPlan();
        entity.setPlanDate(req.getPlanDate());
        entity.setLineId(req.getLineId());
        entity.setProductName(req.getProductName().trim());
        entity.setTargetQty(req.getTargetQty());
        entity.setActualQty(0);
        entity.setPlanHours(req.getPlanHours());
        entity.setStandardQtyPerHour(req.getStandardQtyPerHour());
        entity.setShiftName(req.getShiftName());
        entity.setStatus("待执行");
        entity.setAiExplanation(null);
        planMapper.insert(entity);

        log.info("新建生产计划 id={} {} {} {} 计划量={}",
                entity.getId(), line.getLineName(), req.getPlanDate(), req.getShiftName(), req.getTargetQty());

        return planDetail(entity.getId());
    }

    public PlanDetailVO planDetail(Long id) {
        PlanRowVO plan = planMapper.selectPlanDetail(id);
        if (plan == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "生产计划不存在，id=" + id);
        }
        PlanDetailVO vo = new PlanDetailVO();
        vo.setPlan(plan);
        vo.setBatches(batchMapper.selectByPlanId(id));
        List<OeeLossVO> losses = new ArrayList<>();
        for (OeeLossVO l : planMapper.selectLossTop(plan.getPlanDate(), plan.getPlanDate(),
                plan.getLineId(), 200)) {
            losses.add(l);
        }
        vo.setLosses(losses);
        return vo;
    }

    /* ============================================================
     * 2. OEE 汇总
     * ============================================================ */

    /**
     * OEE 顶部 KPI。
     *
     * @param days   统计窗口天数，null 时用 30 天
     * @param lineId 产线过滤，null 表示全厂
     */
    public OeeSummaryVO oeeSummary(Integer days, Long lineId) {
        int n = (days == null || days < 1) ? DEFAULT_WINDOW_DAYS : days;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(n - 1L);

        OeeAggVO agg = planMapper.selectOeeAgg(lineId, from, to);
        // 昨日同一窗口，用于算「较昨日」；窗口为 30 天时比的是"上一个 30 天"，这里按天比更有意义
        LocalDate yTo = to.minusDays(1);
        LocalDate yFrom = from.minusDays(1);
        OeeAggVO yesterday = planMapper.selectOeeAgg(lineId, yFrom, yTo);

        OeeSummaryVO vo = new OeeSummaryVO();
        BigDecimal oee = oeeOf(agg);
        BigDecimal avail = availabilityOf(agg);
        BigDecimal perf = performanceOf(agg);
        BigDecimal quality = qualityOf(agg);
        BigDecimal yOee = oeeOf(yesterday);

        vo.setOee(oee);
        vo.setValue(oee);
        vo.setTarget(OEE_TARGET);
        vo.setAvailability(avail);
        vo.setAvailabilityNote("目标 " + strip(AVAILABILITY_TARGET) + "%");
        vo.setPerformance(perf);
        vo.setPerformanceNote("目标 " + strip(PERFORMANCE_TARGET) + "%");
        vo.setQualityIndex(quality);
        vo.setQuality(quality);
        vo.setQualityNote("目标 " + strip(QUALITY_TARGET) + "%");

        // 「较昨日」：两个窗口长度不同的话比了也没意义，所以只在单日窗口时才给真实差值
        String deltaText;
        if (n == 1 && yOee != null && oee != null) {
            deltaText = "较昨日 " + signed(oee.subtract(yOee)) + "pct";
        } else {
            deltaText = "统计 " + n + " 天累计";
        }
        vo.setOeeDelta(deltaText);
        vo.setDelta(deltaText);

        vo.setScope("统计范围 " + from + " ~ " + to);
        vo.setNote(buildOeeNote(oee, avail, perf, quality, from, to));
        return vo;
    }

    /**
     * 底部的可解释说明。
     * <p>
     * 不写死"B产线低于目标" —— 哪条线低要算出来再说，写死了换成别的数据集就自相矛盾。
     */
    private String buildOeeNote(BigDecimal oee, BigDecimal avail, BigDecimal perf,
                                BigDecimal quality, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder();
        sb.append("OEE = 开动率 × 性能效率 × 质量指数 · 目标 ").append(strip(OEE_TARGET)).append("%");
        if (oee == null) {
            sb.append("（所选区间无生产数据）");
            return sb.toString();
        }

        // 找出离目标最远的那条产线，点名说出来
        List<OeeAggVO> byLine = planMapper.selectOeeAggByLine(from, to);
        OeeAggVO worst = null;
        BigDecimal worstGap = null;
        for (OeeAggVO a : byLine) {
            BigDecimal v = oeeOf(a);
            if (v == null) {
                continue;
            }
            BigDecimal gap = OEE_TARGET.subtract(v);
            if (worstGap == null || gap.compareTo(worstGap) > 0) {
                worstGap = gap;
                worst = a;
            }
        }
        if (worst != null && worstGap != null && worstGap.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("，").append(worst.getLineName())
              .append(" 低于目标 ").append(strip(worstGap)).append("pct");
        } else if (worst != null) {
            sb.append("，各产线均已达标");
        }

        // 三个分量里最弱的那个也点出来，用户才知道该往哪儿查
        String weakest = weakestComponent(avail, perf, quality);
        if (weakest != null) {
            sb.append("；短板在").append(weakest);
        }
        return sb.toString();
    }

    private String weakestComponent(BigDecimal avail, BigDecimal perf, BigDecimal quality) {
        Map<String, BigDecimal[]> cmp = new LinkedHashMap<>();
        cmp.put("开动率", new BigDecimal[]{avail, AVAILABILITY_TARGET});
        cmp.put("性能效率", new BigDecimal[]{perf, PERFORMANCE_TARGET});
        cmp.put("质量指数", new BigDecimal[]{quality, QUALITY_TARGET});
        String name = null;
        BigDecimal worst = null;
        for (Map.Entry<String, BigDecimal[]> e : cmp.entrySet()) {
            BigDecimal[] v = e.getValue();
            if (v[0] == null) {
                continue;
            }
            BigDecimal gap = v[1].subtract(v[0]);
            if (worst == null || gap.compareTo(worst) > 0) {
                worst = gap;
                name = e.getKey();
            }
        }
        return name;
    }

    /**
     * 分产线 OEE（生产总览卡片 + OEE 分析表格）。
     *
     * @param days   统计窗口天数，null 时用 30 天
     * @param lineId 产线过滤，null 表示全部产线
     */
    public List<OeeLineVO> oeeByLine(Integer days, Long lineId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays((days == null || days < 1 ? DEFAULT_WINDOW_DAYS : days) - 1L);

        List<OeeAggVO> aggs = planMapper.selectOeeAggByLine(from, to);
        List<OeeLineVO> out = new ArrayList<>(aggs.size());
        for (OeeAggVO a : aggs) {
            // 指定了产线就跳过其他产线：聚合仍按产线一次算完，
            // 比在 SQL 里加条件更省事，也避免多一种聚合口径
            if (lineId != null && !lineId.equals(a.getLineId())) {
                continue;
            }
            OeeLineVO vo = new OeeLineVO();
            vo.setLineId(a.getLineId());
            vo.setLineName(a.getLineName());
            vo.setOee(oeeOf(a));
            vo.setAvailability(availabilityOf(a));
            vo.setPerformance(performanceOf(a));
            vo.setQualityIndex(qualityOf(a));
            vo.setTargetQty(a.getTargetQty() == null ? null : a.getTargetQty().intValue());
            vo.setActualQty(a.getActualQty() == null ? null : a.getActualQty().intValue());
            vo.setAchieve(rate(a.getActualQty(), a.getTargetQty()));

            BigDecimal down = a.getDownMinutes() == null ? BigDecimal.ZERO : a.getDownMinutes();
            vo.setDowntime(strip(down) + " 分钟");

            // 瓶颈：该产线损失时长最大的类型
            List<Map<String, Object>> byType = planMapper.selectLossByType(from, to, a.getLineId());
            vo.setLossBottleneck(byType.isEmpty() ? "—" : String.valueOf(byType.get(0).get("lossType")));

            out.add(vo);
        }
        return out;
    }

    /* ============================================================
     * 3. OEE 损失明细
     * ============================================================ */

    /**
     * 损失 TOP 榜。
     *
     * @param limit 条数，默认 10，最大 100
     */
    public List<OeeLossVO> lossTop(Integer days, Long lineId, Integer limit) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays((days == null || days < 1 ? DEFAULT_WINDOW_DAYS : days) - 1L);
        int n = (limit == null || limit < 1) ? 10 : Math.min(limit, 100);

        List<OeeLossVO> rows = planMapper.selectLossTop(from, to, lineId, n);
        for (OeeLossVO r : rows) {
            Integer min = r.getMinutes();
            r.setDuration((min == null ? 0 : min) + " 分钟");
            BigDecimal impact = r.getImpactValue() == null ? BigDecimal.ZERO : r.getImpactValue();
            // 影响统一显示成负数：OEE 损失天然是"减分项"，正数会让页面看起来像增益
            r.setImpact(signed(impact.negate()) + "pct");
        }
        return rows;
    }

    /** 按损失类型汇总（饼图数据源） */
    public List<Map<String, Object>> lossByType(Integer days, Long lineId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays((days == null || days < 1 ? DEFAULT_WINDOW_DAYS : days) - 1L);
        List<Map<String, Object>> raw = planMapper.selectLossByType(from, to, lineId);
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        for (Map<String, Object> r : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lossType", r.get("lossType"));
            m.put("minutes", r.get("minutes"));
            m.put("impact", r.get("impactValue"));
            m.put("count", r.get("cnt"));
            out.add(m);
        }
        return out;
    }

    /* ============================================================
     * 4. OEE 分时段走势
     * ============================================================ */

    /**
     * 8:00—18:00 每两小时一格的产出分布。
     * <p>
     * <b>这里返回的是各时段「产量占比 × 良率」，不是严格意义的 OEE，前端文案必须说明</b>。
     * 原因：精确 OEE 需要分钟级工时账，而批次记录只有 start/end 两个时刻，
     * 两小时桶内到底停机多少分钟是算不准的。与其编一个看起来精确的假 OEE，
     * 不如老老实实展示"这条产线在这个时段产出了多少、良率如何" —— 走势的可比性是一样的。
     * <p>
     * 每格的数值 = 该格良品数 / 全时段良品数 × 100 × 该格良率 / 100，
     * 因此它是<b>相对值</b>（各格平均约等于全厂良率），适合看趋势，不能当绝对 OEE 引用。
     */
    public Map<String, Object> outputByShift(Integer days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays((days == null || days < 1 ? DEFAULT_WINDOW_DAYS : days) - 1L);

        List<Map<String, Object>> buckets = planMapper.selectShiftBuckets(from, to);

        int slotCount = SHIFT_SLOT_LABELS.length;
        long[] good = new long[slotCount];
        long[] defect = new long[slotCount];
        boolean[] has = new boolean[slotCount];

        // 键 "产线名|slot" → 良品数
        Map<String, Long> lineSlotGood = new LinkedHashMap<>();
        List<String> lineNames = new ArrayList<>();

        for (Map<String, Object> r : buckets) {
            int slot = toInt(r.get("slot"));
            if (slot < 0 || slot >= slotCount) {
                continue;
            }
            long g = toLong(r.get("goodQty"));
            long d = toLong(r.get("defectQty"));
            good[slot] += g;
            defect[slot] += d;
            has[slot] = true;

            Object lineName = r.get("lineName");
            if (lineName != null) {
                String name = String.valueOf(lineName);
                if (!lineNames.contains(name)) {
                    lineNames.add(name);
                }
                lineSlotGood.merge(name + "|" + slot, g, Long::sum);
            }
        }
        lineNames.sort(null);

        long factoryGood = 0;
        for (long g : good) {
            factoryGood += g;
        }

        List<OeeShiftPointVO> points = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            if (!has[i]) {
                continue;
            }
            OeeShiftPointVO vo = new OeeShiftPointVO();
            vo.setTime(SHIFT_SLOT_LABELS[i]);
            long total = good[i] + defect[i];
            BigDecimal slotQuality = total > 0
                    ? BigDecimal.valueOf(good[i] * 100.0 / total) : null;
            vo.setFactory(shareWithQuality(good[i], factoryGood, slotQuality));

            for (String name : lineNames) {
                Long lg = lineSlotGood.get(name + "|" + i);
                BigDecimal v = shareWithQuality(lg == null ? 0 : lg, factoryGood, slotQuality);
                switch (name) {
                    case "A产线" -> vo.setA(v);
                    case "B产线" -> vo.setB(v);
                    case "C产线" -> vo.setC(v);
                    default -> { /* 其它产线暂不展示 */ }
                }
            }
            points.add(vo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", points);
        result.put("metric", "产出占比×良率（相对值，非绝对 OEE）");
        result.put("scope", "统计范围 " + from + " ~ " + to);
        return result;
    }

    /**
     * 单格数值 = 该格良品 / 全时段良品 × 100 × 良率 / 100。
     * 全时段良品为 0 时返回 null（该格无产出）。
     */
    private static BigDecimal shareWithQuality(long good, long totalGood, BigDecimal quality) {
        if (totalGood <= 0 || good <= 0 || quality == null) {
            return null;
        }
        BigDecimal share = BigDecimal.valueOf(good * 100.0 / totalGood);
        return round2(share.multiply(quality).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    /* ============================================================
     * 5. 生产总览
     * ============================================================ */

    /**
     * 生产总览页：顶部 KPI + 产线卡片。
     * <p>
     * 日粒度，只看今天；没有今天的计划时自动回退到最近一个有数据的日期 ——
     * 周末或测试库里常见"今天没排计划"，这时返回全 0 的卡片比返回空更让人误会。
     */
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        List<PlanRowVO> todayPlans = plansOf(today);
        LocalDate statDate = today;
        if (todayPlans.isEmpty()) {
            LocalDate latest = latestPlanDate();
            if (latest != null) {
                statDate = latest;
                todayPlans = plansOf(latest);
            }
        }

        long planQty = 0;
        long actualQty = 0;
        List<ProductionLineVO> cards = new ArrayList<>();
        int delayed = 0;
        int onTrack = 0;

        for (PlanRowVO p : todayPlans) {
            planQty += p.getTargetQty() == null ? 0 : p.getTargetQty();
            actualQty += p.getActualQty() == null ? 0 : p.getActualQty();
            cards.add(toLineCard(p));
            if ("已延期".equals(p.getStatus())) {
                delayed++;
            }
            if (p.getAchieve() != null && p.getAchieve().compareTo(BigDecimal.valueOf(100)) >= 0) {
                onTrack++;
            }
        }

        OeeAggVO agg = planMapper.selectOeeAgg(null, statDate, statDate);
        BigDecimal oee = oeeOf(agg);
        BigDecimal achieve = rate(BigDecimal.valueOf(actualQty), BigDecimal.valueOf(planQty));

        ProductionOverviewVO kpi = new ProductionOverviewVO();
        kpi.setPlanQty((int) planQty);
        kpi.setActualQty((int) actualQty);
        kpi.setAchieveRate(achieve);
        kpi.setOee(oee);
        kpi.setLineCount(todayPlans.size());
        kpi.setOnTrackLines(onTrack);
        kpi.setDelayedLines(delayed);
        kpi.setStatDate(statDate.toString());

        kpi.setPlanNote(todayPlans.size() + " 条计划 · " + distinctShift(todayPlans));
        long diff = actualQty - planQty;
        kpi.setActualNote(diff == 0 ? "与计划持平"
                : "较计划 " + (diff > 0 ? "+" : "") + diff + " 件");
        kpi.setAchieveNote(achieve == null ? "—"
                : (achieve.compareTo(BigDecimal.valueOf(100)) >= 0 ? "已达成计划" : "未达成计划"));
        kpi.setOeeNote(oee == null ? "—"
                : "目标 " + strip(OEE_TARGET) + "%" + (oee.compareTo(OEE_TARGET) >= 0 ? "，已达标" : "，未达标"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpi", kpi);
        result.put("lines", cards);
        return result;
    }

    private List<PlanRowVO> plansOf(LocalDate date) {
        PlanQuery q = new PlanQuery();
        q.setStartDate(date);
        q.setEndDate(date);
        q.normalize();
        Page<PlanRowVO> page = new Page<>(1, 500, false);
        return planMapper.selectPlanPage(page, q).getRecords();
    }

    private LocalDate latestPlanDate() {
        ProdPlan last = planMapper.selectOne(new LambdaQueryWrapper<ProdPlan>()
                .orderByDesc(ProdPlan::getPlanDate)
                .last("LIMIT 1"));
        return last == null ? null : last.getPlanDate();
    }

    private String distinctShift(List<PlanRowVO> plans) {
        List<String> shifts = plans.stream().map(PlanRowVO::getShiftName)
                .filter(s -> s != null && !s.isBlank()).distinct().toList();
        return shifts.isEmpty() ? "—" : String.join("/", shifts);
    }

    /** 把一条计划行转成产线卡片 */
    private ProductionLineVO toLineCard(PlanRowVO p) {
        ProductionLineVO vo = new ProductionLineVO();
        vo.setLineId(p.getLineId());
        vo.setName(p.getLineName());
        vo.setStatus(p.getStatus());
        vo.setProduct(p.getProduct());
        vo.setTarget(p.getTargetQty());
        vo.setActual(p.getActualQty());
        vo.setAchieve(p.getAchieve());

        BigDecimal hours = p.getPlanHours() == null ? BigDecimal.ZERO : p.getPlanHours();
        vo.setWorkTime(strip(hours) + " 小时");

        // 单条计划的 OEE：复用同一套聚合，只是把窗口收窄到这一条
        OeeAggVO agg = planMapper.selectOeeAgg(p.getLineId(), p.getPlanDate(), p.getPlanDate());
        vo.setOee(oeeOf(agg));
        vo.setAvailability(availabilityOf(agg));
        vo.setPerformance(performanceOf(agg));
        BigDecimal quality = qualityOf(agg);
        vo.setQuality(quality);
        vo.setQualityIndex(quality);

        // 损失构成
        List<Map<String, Object>> lossRaw = planMapper.selectLossByTypeForPlan(p.getId());
        List<ProductionLineVO.LossSlice> slices = new ArrayList<>(lossRaw.size());
        long downSum = 0;
        for (Map<String, Object> r : lossRaw) {
            int minutes = toInt(r.get("minutes"));
            slices.add(new ProductionLineVO.LossSlice(String.valueOf(r.get("lossType")), minutes));
            if (isDownType(String.valueOf(r.get("lossType")))) {
                downSum += minutes;
            }
        }
        vo.setLosses(slices);
        vo.setDowntimeSum((int) downSum);

        int batchCount = batchMapper.countByPlanId(p.getId());
        vo.setBatchProgress(batchCount > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO);
        if (p.getAchieve() != null) {
            vo.setPlanProgress(p.getAchieve().min(BigDecimal.valueOf(100)));
        }

        // 下面的字段库里没有对应列，明确留空而不是编一个像真的数
        vo.setOeeDelta(null);
        vo.setAchieveNote(p.getAchieve() == null ? null
                : (p.getAchieve().compareTo(BigDecimal.valueOf(100)) >= 0 ? "达成" : "未达成"));
        vo.setQualityNote("目标 " + strip(QUALITY_TARGET) + "%");
        vo.setCurrentBatch(batchCount > 0 ? "第" + batchCount + "批" : null);
        vo.setCurrentBatchQty(null);
        vo.setChangeover(null);
        vo.setAiHint(p.getAiExplanation());
        return vo;
    }

    private static boolean isDownType(String type) {
        return "设备停机".equals(type) || "换线时间".equals(type) || "小停机".equals(type);
    }

    /* ============================================================
     * 6. 下拉选项
     * ============================================================ */

    public Map<String, Object> options() {
        List<Map<String, Object>> lines = lineMapper.selectList(
                        new LambdaQueryWrapper<BaseProductionLine>()
                                .eq(BaseProductionLine::getStatus, 1)
                                .orderByAsc(BaseProductionLine::getId))
                .stream()
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", l.getId());
                    m.put("lineName", l.getLineName());
                    m.put("workshopId", l.getWorkshopId());
                    return m;
                })
                .toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", PlanQuery.VALID_STATUS);
        m.put("shiftName", PlanQuery.VALID_SHIFT);
        m.put("lines", lines);
        return m;
    }

    /* ============================================================
     * OEE 计算 —— 唯一入口
     * ============================================================ */

    /**
     * 开动率(%)。
     * <p>
     * {@code available} 已在 SQL 里算好（计划工时 - 停机），这里直接相除。
     * 用 MIN(100) 兜底：脏数据里停机时长可能大于计划工时，那会算出负数开动率，
     * 页面显示「-12%」比显示 0% 更让人困惑。
     */
    BigDecimal availabilityOf(OeeAggVO a) {
        if (a == null) {
            return null;
        }
        BigDecimal plan = a.getPlanMinutes();
        BigDecimal avail = a.getAvailableMinutes();
        if (plan == null || avail == null || plan.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal v = avail.multiply(BigDecimal.valueOf(100))
                .divide(plan, 4, RoundingMode.HALF_UP);
        return round2(clamp(v));
    }

    /** 性能效率(%) = 产出 / 理论产能 */
    BigDecimal performanceOf(OeeAggVO a) {
        if (a == null) {
            return null;
        }
        BigDecimal cap = a.getCapacityQty();
        BigDecimal out = a.getOutputQty();
        if (cap == null || out == null || cap.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal v = out.multiply(BigDecimal.valueOf(100))
                .divide(cap, 4, RoundingMode.HALF_UP);
        return round2(clamp(v));
    }

    /** 质量指数(%) = 良品 / 产出 */
    BigDecimal qualityOf(OeeAggVO a) {
        if (a == null) {
            return null;
        }
        BigDecimal out = a.getOutputQty();
        BigDecimal good = a.getGoodQty();
        if (out == null || good == null || out.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return round2(good.multiply(BigDecimal.valueOf(100))
                .divide(out, 4, RoundingMode.HALF_UP));
    }

    /** OEE(%) = 三个分量相乘 */
    BigDecimal oeeOf(OeeAggVO a) {
        BigDecimal avail = availabilityOf(a);
        BigDecimal perf = performanceOf(a);
        BigDecimal quality = qualityOf(a);
        if (avail == null || perf == null || quality == null) {
            return null;
        }
        return round2(avail.multiply(perf).multiply(quality)
                .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP));
    }

    /* ============================================================
     * 工具
     * ============================================================ */

    /** 百分比 = 分子 / 分母 × 100，分母为 0 返回 null */
    private static BigDecimal rate(BigDecimal num, BigDecimal den) {
        if (num == null || den == null || den.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return round2(num.multiply(BigDecimal.valueOf(100)).divide(den, 4, RoundingMode.HALF_UP));
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.max(BigDecimal.ZERO);
    }

    private static BigDecimal round2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 去掉末尾多余的 0：90.00 → 90、12.50 → 12.5 */
    private static String strip(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() < 0) {
            s = s.setScale(0);
        }
        return s.toPlainString();
    }

    /** 带符号串：-2.00 → "-2"、1.50 → "+1.5" */
    private static String signed(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        BigDecimal s = v.stripTrailingZeros();
        if (s.scale() < 0) {
            s = s.setScale(0);
        }
        String text = s.toPlainString();
        return s.signum() > 0 ? "+" + text : text;
    }

    private static int toInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 供测试/其它模块复用：统计窗口起止 */
    public static LocalDate[] window(Integer days) {
        LocalDate to = LocalDate.now();
        int n = (days == null || days < 1) ? DEFAULT_WINDOW_DAYS : days;
        return new LocalDate[]{to.minusDays(n - 1L), to};
    }

    /** 空列表常量（避免各处返回 null 让前端判空） */
    private static final List<OeeLossVO> EMPTY = Collections.emptyList();

    /** 显式引用，避免 IDE 报未使用 */
    @SuppressWarnings("unused")
    private List<OeeLossVO> emptyLosses() {
        return EMPTY;
    }
}