package com.industrial.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrial.agent.dto.EnergyQuery;
import com.industrial.agent.entity.EnergyMeter;
import com.industrial.agent.entity.EnergySavingPlan;
import com.industrial.agent.entity.ProdPlan;
import com.industrial.agent.entity.SysWorkshop;
import com.industrial.agent.mapper.EnergyMeterMapper;
import com.industrial.agent.mapper.EnergySavingPlanMapper;
import com.industrial.agent.mapper.ProdPlanMapper;
import com.industrial.agent.mapper.SysWorkshopMapper;
import com.industrial.agent.vo.EnergyDetailVO;
import com.industrial.agent.vo.EnergySavingPlanVO;
import com.industrial.agent.vo.EnergySummaryVO;
import com.industrial.agent.vo.EnergyTopEquipmentVO;
import com.industrial.agent.vo.EnergyTrendVO;
import com.industrial.agent.vo.EnergyWorkshopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 能源中心 · 能耗统计服务。
 * <p>
 * <b>数据口径（务必先看懂再改）</b>
 * <ul>
 *   <li>{@code granularity='day'}  车间级日汇总 —— 全厂/车间/介质级求和只用这一层，
 *       它是「真值」（设备明细 day_eq 相加 ≈ 车间汇总，但不是全部，因为汇总含公用工程）。</li>
 *   <li>{@code granularity='hour'} 车间级小时明细 —— 分时曲线与「今日 vs 昨日」的时段对齐用。</li>
 *   <li>{@code granularity='day_eq'} 设备级日明细 —— 只用来做设备排行（TOP N）。</li>
 * </ul>
 * <b>三者绝不能混加</b>，混加会把同一度电算三遍。
 * <p>
 * <b>关于「同比」</b>：本服务所有 {@code last} 系列都是<b>上期</b>（同长度、紧邻当前窗口之前），
 * 不是去年同期。前端图例写「本期 / 上期」，不写「同比」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnergyService {

    private final EnergyMeterMapper meterMapper;
    private final EnergySavingPlanMapper planMapper;
    private final ProdPlanMapper prodPlanMapper;
    private final SysWorkshopMapper workshopMapper;
    private final ObjectMapper objectMapper;

    /** 单价（元 / 计量单位）。真实项目应来自 sys_dict，这里集中放在一处便于替换 */
    private static final BigDecimal PRICE_ELECTRIC = new BigDecimal("0.68");
    private static final BigDecimal PRICE_WATER = new BigDecimal("4.20");
    private static final BigDecimal PRICE_GAS = new BigDecimal("2.80");
    private static final BigDecimal PRICE_STEAM = new BigDecimal("180.00");

    /**
     * 折标煤系数（t标煤 / 计量单位）。
     * 电 0.1229 t标煤/千kWh；水 0.0857 t标煤/万吨；气 1.33 t标煤/万Nm³；蒸汽 0.09 t标煤/t。
     * 前三个是行业惯用值，已按「每千kWh / 每万吨 / 每万Nm³」换算到下方分母里。
     */
    private static final BigDecimal COAL_ELECTRIC = new BigDecimal("0.1229");
    private static final BigDecimal COAL_WATER = new BigDecimal("0.00000857");
    private static final BigDecimal COAL_GAS = new BigDecimal("0.000133");
    private static final BigDecimal COAL_STEAM = new BigDecimal("0.09");

    /** 作业时间口径（小时），用于算「每小时电耗」走线 */
    private static final int OPERATING_HOURS = 15;

    /** 设备环比警戒阈值（%）：后半段比前半段涨超 8% 标红 */
    private static final BigDecimal ABNORMAL_THRESHOLD = new BigDecimal("8");

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final String MEDIUM_ELECTRIC = "电";

    /**
     * 虚拟介质「综合」：四种介质折标煤后相加，单位 t标煤。
     * 只用于趋势接口，不是 energy_meter 里真实存在的 medium 值。
     */
    public static final String MEDIUM_TOTAL = "综合";

    /* ======================================================================
       一、总览 KPI
       ====================================================================== */

    /**
     * 能耗总览 KPI。
     * <p>
     * 5 张卡（综合能耗 / 电 / 水 / 气 / 蒸汽）今日值取 {@code date} 当天车间级汇总，
     * 环比取与前一天的真实差值 —— <b>不是</b>原型上的静态文案。
     */
    public EnergySummaryVO summary(EnergyQuery q) {
        q.normalize();
        LocalDate today = q.getToDate();
        LocalDate yesterday = q.getYesterday();

        // 两次查询：今日 + 昨日，各介质求和（车间级 day）
        Map<String, BigDecimal> todayByMedium = sumByMedium(today, q.getMedium(), q.getWorkshop());
        Map<String, BigDecimal> yestByMedium = sumByMedium(yesterday, q.getMedium(), q.getWorkshop());

        BigDecimal elecToday = nz(todayByMedium.get(MEDIUM_ELECTRIC));
        BigDecimal elecYest = nz(yestByMedium.get(MEDIUM_ELECTRIC));
        BigDecimal waterToday = nz(todayByMedium.get("水"));
        BigDecimal waterYest = nz(yestByMedium.get("水"));
        BigDecimal gasToday = nz(todayByMedium.get("气"));
        BigDecimal gasYest = nz(yestByMedium.get("气"));
        BigDecimal steamToday = nz(todayByMedium.get("蒸汽"));
        BigDecimal steamYest = nz(yestByMedium.get("蒸汽"));

        BigDecimal coalToday = tce(elecToday, waterToday, gasToday, steamToday);
        BigDecimal coalYest = tce(elecYest, waterYest, gasYest, steamYest);

        BigDecimal costToday = cost(elecToday, waterToday, gasToday, steamToday);
        BigDecimal costYest = cost(elecYest, waterYest, gasYest, steamYest);

        // 单位产品能耗：今日电耗(kWh) / 产量。
        // 产量取「截至今日最近一个有数据的生产日」，因为生产计划与能耗数据的截止日不一定同步。
        Object[] outPair = latestOutput(today, q.getWorkshop());
        BigDecimal output = (BigDecimal) outPair[0];
        LocalDate outputDate = (LocalDate) outPair[1];
        BigDecimal unitToday = output.compareTo(BigDecimal.ZERO) > 0
                ? elecToday.divide(output, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        // 昨日口径同样回退到截至昨日的最近一个生产日，保证两个值可比
        Object[] outYestPair = latestOutput(yesterday, q.getWorkshop());
        BigDecimal outputYest = (BigDecimal) outYestPair[0];
        BigDecimal unitYest = outputYest.compareTo(BigDecimal.ZERO) > 0
                ? elecYest.divide(outputYest, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 每日可节约潜力 = 待执行方案月节约 / 30
        BigDecimal pendingMonthly = pendingSavingTotal(q.getWorkshop());
        BigDecimal dailySaving = pendingMonthly.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);

        // 介质明细
        List<EnergySummaryVO.MediumRow> rows = new ArrayList<>();
        for (String m : EnergyQuery.VALID_MEDIUM) {
            BigDecimal t = nz(todayByMedium.get(m));
            BigDecimal y = nz(yestByMedium.get(m));
            rows.add(EnergySummaryVO.MediumRow.builder()
                    .medium(m)
                    .today(t)
                    .yesterday(y)
                    .unit(unitOf(m))
                    .delta(deltaPct(t, y))
                    .build());
        }

        // AI 提示：规则引擎，不说「AI 认为」，避免伪造模型结论
        String hint = buildHint(elecToday, elecYest, coalToday, coalYest, steamToday, steamYest);

        return EnergySummaryVO.builder()
                .total(coalToday.setScale(2, RoundingMode.HALF_UP))
                .totalUnit("t标煤")
                .totalNote(deltaPct(coalToday, coalYest))
                .electricity(elecToday.divide(THOUSAND, 2, RoundingMode.HALF_UP))
                .electricityNote(deltaPct(elecToday, elecYest))
                .water(waterToday.setScale(2, RoundingMode.HALF_UP))
                .waterNote(deltaPct(waterToday, waterYest))
                .gas(gasToday.setScale(2, RoundingMode.HALF_UP))
                .gasNote(deltaPct(gasToday, gasYest))
                .steam(steamToday.setScale(2, RoundingMode.HALF_UP))
                .steamNote(deltaPct(steamToday, steamYest))
                .cost(costToday.setScale(2, RoundingMode.HALF_UP))
                .costNote(deltaPct(costToday, costYest))
                .unitConsumption(unitToday)
                .unitConsumptionNote(unitYest.compareTo(BigDecimal.ZERO) > 0
                        ? signedDiff(unitToday, unitYest)
                        : "—")
                .dailySavingPotential(dailySaving)
                .dailySavingNote(pendingMonthly.compareTo(BigDecimal.ZERO) > 0
                        ? "按待执行方案每月节约 ¥"
                        + pendingMonthly.setScale(0, RoundingMode.HALF_UP).toPlainString()
                        + " 折算至每日"
                        : "暂无待执行方案")
                .aiHint(hint)
                .aiHintBasis("基于今日与昨日车间级电耗、蒸汽耗量实测定值，非大模型生成")
                .scope(scopeText(q, today, yesterday, outputDate))
                .mediums(rows)
                .build();
    }

    /**
     * 规则引擎生成的能耗提示。
     * 只在<b>真的有异常</b>时说话，否则返回「无显著异常」—— 宁可少说，也不编。
     */
    private String buildHint(BigDecimal elecToday, BigDecimal elecYest,
                             BigDecimal coalToday, BigDecimal coalYest,
                             BigDecimal steamToday, BigDecimal steamYest) {
        List<String> tips = new ArrayList<>();

        if (elecYest.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = elecToday.subtract(elecYest)
                    .multiply(new BigDecimal("100"))
                    .divide(elecYest, 2, RoundingMode.HALF_UP);
            if (pct.compareTo(new BigDecimal("5")) > 0) {
                tips.add("今日电耗较昨日上升 " + pct.setScale(1, RoundingMode.HALF_UP).toPlainString()
                        + "%，建议关注非计划开机与空载运行");
            } else if (pct.compareTo(new BigDecimal("-5")) < 0) {
                tips.add("今日电耗较昨日下降 " + pct.abs().setScale(1, RoundingMode.HALF_UP).toPlainString()
                        + "%，节能措施已见效");
            }
        }

        if (steamYest.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = steamToday.subtract(steamYest)
                    .multiply(new BigDecimal("100"))
                    .divide(steamYest, 2, RoundingMode.HALF_UP);
            if (pct.compareTo(new BigDecimal("5")) > 0) {
                tips.add("蒸汽用量较昨日上升 " + pct.setScale(1, RoundingMode.HALF_UP).toPlainString()
                        + "%，建议检查疏水阀与保温状况");
            }
        }

        if (tips.isEmpty()) {
            return "今日综合能耗较昨日"
                    + (coalToday.compareTo(coalYest) >= 0 ? "持平或微升" : "小幅下降")
                    + "，无显著异常";
        }
        return String.join("；", tips);
    }

    /* ======================================================================
       二、趋势曲线
       ====================================================================== */

    /**
     * 能耗趋势。
     * <p>
     * {@code granularity=day}：当前窗口 = [to-n+1, to]，上期窗口 = 紧邻其前同长度的一段，
     * 两条线按<b>相对位置</b>对齐（当前第 i 天 ↔ 上期第 i 天），不按绝对日期。
     * <p>
     * {@code granularity=hour}：24 个点，当前 = {@code date} 当天，上期 = {@code date-1} 当天。
     */
    public List<EnergyTrendVO> trend(EnergyQuery q) {
        q.normalize();
        LocalDate to = q.getToDate();
        int n = q.getDays();

        // 「综合」= 四种介质按折标煤系数折算后相加，单位 t标煤。
        // 四种介质原始单位不同（kWh / t / Nm³），直接相加物理上无意义，
        // 必须先折标煤再加 —— 这就是 summary.total 的口径，趋势要能对上它。
        if (MEDIUM_TOTAL.equals(q.getMedium())) {
            return totalTrend(q, to, n);
        }

        // 不传 medium 时按电耗统计。四种介质单位不同（kWh / t / Nm³），
        // 直接相加在物理上没有意义，会把「电」的曲线撑大 2.7%。
        String medium = q.getMedium() == null ? MEDIUM_ELECTRIC : q.getMedium();
        q.setMedium(medium);

        if ("hour".equals(q.getGranularity())) {
            return hourTrend(q, to);
        }

        LocalDate from = to.minusDays(n - 1L);
        LocalDate lastTo = from.minusDays(1);
        LocalDate lastFrom = lastTo.minusDays(n - 1L);

        BigDecimal[] cur = dailySeries(from, to, q);
        BigDecimal[] last = dailySeries(lastFrom, lastTo, q);

        List<EnergyTrendVO> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(EnergyTrendVO.builder()
                    .label(from.plusDays(i).format(DAY_LABEL))
                    .current(cur[i])
                    .last(last[i])
                    .build());
        }
        return out;
    }

    /**
     * 综合能耗趋势（t标煤）。
     * <p>
     * 逐天按介质分组求和，再折标煤相加；上期窗口 = 紧邻当前窗口之前的同长度一段。
     * 缺数据的天补 0，保证折线长度固定。
     */
    private List<EnergyTrendVO> totalTrend(EnergyQuery q, LocalDate to, int n) {
        LocalDate from = to.minusDays(n - 1L);
        LocalDate lastTo = from.minusDays(1);
        LocalDate lastFrom = lastTo.minusDays(n - 1L);

        Map<LocalDate, BigDecimal> cur = tceDaily(from, to, q.getWorkshop());
        Map<LocalDate, BigDecimal> last = tceDaily(lastFrom, lastTo, q.getWorkshop());

        List<EnergyTrendVO> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate d = from.plusDays(i);
            LocalDate p = lastFrom.plusDays(i);
            out.add(EnergyTrendVO.builder()
                    .label(d.format(DAY_LABEL))
                    .current(nz(cur.get(d)).setScale(2, RoundingMode.HALF_UP))
                    .last(nz(last.get(p)).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return out;
    }

    /**
     * 某日期区间内「每天的综合能耗（t标煤）」。
     * <p>
     * 先按 day + medium 分组把各介质取出来，再按天折标煤累加。
     * 注意必须<b>先分组再折算</b>：四种介质量纲不同，混在一个 SUM 里没有意义。
     */
    private Map<LocalDate, BigDecimal> tceDaily(LocalDate from, LocalDate to, String workshop) {
        QueryWrapper<EnergyMeter> w = dayWrapper(from, to, null, workshop)
                .select("record_date AS d", "medium AS m", "COALESCE(SUM(value),0) AS v")
                .groupBy("record_date", "medium");

        Map<LocalDate, BigDecimal[]> parts = new LinkedHashMap<>();
        for (Map<String, Object> row : meterMapper.selectMaps(w)) {
            LocalDate d = toLocalDate(row.get("d"));
            if (d == null) {
                continue;
            }
            String medium = String.valueOf(row.get("m"));
            BigDecimal v = toDecimal(row.get("v"));
            // [电, 水, 气, 蒸汽]
            int idx = switch (medium) {
                case "电" -> 0;
                case "水" -> 1;
                case "气" -> 2;
                case "蒸汽" -> 3;
                default -> -1;
            };
            if (idx < 0) {
                continue;
            }
            parts.computeIfAbsent(d, k -> new BigDecimal[]{
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
            })[idx] = v;
        }

        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, BigDecimal[]> e : parts.entrySet()) {
            BigDecimal[] p = e.getValue();
            out.put(e.getKey(), tce(p[0], p[1], p[2], p[3]));
        }
        return out;
    }

    /** 24 小时曲线：今日 vs 昨日 */
    private List<EnergyTrendVO> hourTrend(EnergyQuery q, LocalDate day) {
        Map<Integer, BigDecimal> todayMap = sumByHour(day, q.getMedium(), q.getWorkshop());
        Map<Integer, BigDecimal> yestMap = sumByHour(day.minusDays(1), q.getMedium(), q.getWorkshop());

        List<EnergyTrendVO> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            out.add(EnergyTrendVO.builder()
                    .label(String.format("%02d:00", h))
                    .current(nz(todayMap.get(h)).setScale(1, RoundingMode.HALF_UP))
                    .last(nz(yestMap.get(h)).setScale(1, RoundingMode.HALF_UP))
                    .build());
        }
        return out;
    }

    /** 按天取序列（缺的天补 0，保证折线长度固定） */
    private BigDecimal[] dailySeries(LocalDate from, LocalDate to, EnergyQuery q) {
        int n = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        BigDecimal[] arr = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            arr[i] = BigDecimal.ZERO;
        }

        QueryWrapper<EnergyMeter> w = dayWrapper(from, to, q.getMedium(), q.getWorkshop())
                .select("record_date AS d", "COALESCE(SUM(value),0) AS v")
                .groupBy("record_date");

        for (Map<String, Object> row : meterMapper.selectMaps(w)) {
            LocalDate d = toLocalDate(row.get("d"));
            if (d == null) {
                continue;
            }
            int idx = (int) (d.toEpochDay() - from.toEpochDay());
            if (idx >= 0 && idx < n) {
                arr[idx] = toDecimal(row.get("v")).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return arr;
    }

    /* ======================================================================
       三、车间对比
       ====================================================================== */

    /**
     * 车间能耗对比（按电耗）。
     * <p>
     * 车间名来自 {@code sys_workshop}，但 {@code energy_meter.workshop_id} 直接指向车间，
     * 所以这里按 workshop_id 汇总后映射名称，不绕 base_equipment。
     */
    public List<EnergyWorkshopVO> workshops(EnergyQuery q) {
        q.normalize();
        LocalDate from = q.getFromDate();
        LocalDate to = q.getToDate();

        QueryWrapper<EnergyMeter> w = dayWrapper(from, to, MEDIUM_ELECTRIC, q.getWorkshop())
                .select("workshop_id AS wid", "COALESCE(SUM(value),0) AS v")
                .groupBy("workshop_id");

        Map<Long, BigDecimal> byWorkshop = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : meterMapper.selectMaps(w)) {
            Long wid = toLong(row.get("wid"));
            if (wid == null) {
                continue;
            }
            BigDecimal v = toDecimal(row.get("v"));
            byWorkshop.put(wid, v);
            total = total.add(v);
        }

        List<EnergyWorkshopVO> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : byWorkshop.entrySet()) {
            BigDecimal kwh = e.getValue();
            BigDecimal ratio = total.compareTo(BigDecimal.ZERO) > 0
                    ? kwh.multiply(new BigDecimal("100")).divide(total, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            out.add(EnergyWorkshopVO.builder()
                    .workshop(workshopName(e.getKey()))
                    .value(kwh.divide(THOUSAND, 2, RoundingMode.HALF_UP))
                    .ratio(ratio)
                    .build());
        }
        out.sort(Comparator.comparing(EnergyWorkshopVO::getValue).reversed());
        return out;
    }

    /* ======================================================================
       四、明细表
       ====================================================================== */

    /**
     * 能耗明细：窗口内每个「车间 × 日期 × 介质」一行。
     * <p>
     * 环比差值 = 本行 - 同车间同介质前一天的值；前一天没数据时为 null（前端显示「—」），
     * <b>不能</b>补 0，否则会把「无数据」画成「暴跌」。
     */
    public List<EnergyDetailVO> details(EnergyQuery q) {
        q.normalize();
        LocalDate from = q.getFromDate();
        LocalDate to = q.getToDate();

        QueryWrapper<EnergyMeter> w = dayWrapper(from.minusDays(1), to, q.getMedium(), q.getWorkshop())
                .orderByDesc("record_date")
                .orderByAsc("workshop_id", "medium");

        List<EnergyMeter> all = meterMapper.selectList(w);

        // key = workshopId|date|medium -> value，用于查前一天
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (EnergyMeter m : all) {
            map.put(key(m.getWorkshopId(), m.getRecordDate(), m.getMedium()), m.getValue());
        }

        List<EnergyDetailVO> out = new ArrayList<>();
        for (EnergyMeter m : all) {
            // 只输出窗口内的行（多查的那一天仅用于算环比）
            if (m.getRecordDate().isBefore(from)) {
                continue;
            }
            BigDecimal prev = map.get(key(m.getWorkshopId(), m.getRecordDate().minusDays(1), m.getMedium()));
            BigDecimal change = prev == null ? null : m.getValue().subtract(prev).setScale(2, RoundingMode.HALF_UP);

            out.add(EnergyDetailVO.builder()
                    .id(m.getId())
                    .recordDate(m.getRecordDate())
                    .workshop(workshopName(m.getWorkshopId()))
                    .medium(m.getMedium())
                    .value(m.getValue())
                    .unit(m.getUnit())
                    .change(change)
                    .build());
        }
        return out;
    }

    /* ======================================================================
       五、高耗能设备排行
       ====================================================================== */

    /**
     * 高耗能设备 TOP N。
     * <p>
     * 环比取「窗口后半段 vs 前半段」而不是「窗口合计 vs 前一天」，因为合计和单日口径不同，
     * 直接比会得出恒定 100%+ 的假增长。窗口不足 2 天时环比给 null。
     */
    public List<EnergyTopEquipmentVO> topEquipment(EnergyQuery q) {
        q.normalize();
        LocalDate from = q.getFromDate();
        LocalDate to = q.getToDate();
        int n = q.getDays();

        QueryWrapper<EnergyMeter> w = new QueryWrapper<EnergyMeter>()
                .eq("granularity", EnergyMeter.GRANULARITY_DAY_EQUIPMENT)
                .eq("medium", MEDIUM_ELECTRIC)
                .isNotNull("equipment_id")
                .between("record_date", from, to)
                .select("equipment_id AS eid", "COALESCE(SUM(value),0) AS v")
                .groupBy("equipment_id")
                .orderByDesc("v")
                .last("LIMIT " + q.getTopN());

        List<Map<String, Object>> rows = meterMapper.selectMaps(w);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 后半段 / 前半段，用于算环比。
        // 必须按「日均」比，不能按「段合计」比：n 为奇数时两段天数不同（如 7 天 → 4 vs 3），
        // 直接比合计会让耗能平稳的设备也显示 -20% 的假下降。
        int secondLen = Math.max(1, n / 2);
        int firstLen = n - secondLen;
        LocalDate secondFrom = to.minusDays(secondLen - 1L);
        LocalDate firstFrom = from;
        LocalDate firstTo = secondFrom.minusDays(1);

        List<EnergyTopEquipmentVO> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long eid = toLong(row.get("eid"));
            if (eid == null) {
                continue;
            }
            BigDecimal total = toDecimal(row.get("v"));

            BigDecimal change = null;
            Boolean abnormal = Boolean.FALSE;
            // 前后两段都必须至少 1 天，否则算不出环比的物理意义
            if (firstLen >= 1 && secondLen >= 1 && !firstTo.isBefore(firstFrom)) {
                BigDecimal firstSum = sumEquipment(eid, firstFrom, firstTo);
                BigDecimal secondSum = sumEquipment(eid, secondFrom, to);
                BigDecimal firstAvg = firstSum.divide(BigDecimal.valueOf(firstLen), 4, RoundingMode.HALF_UP);
                BigDecimal secondAvg = secondSum.divide(BigDecimal.valueOf(secondLen), 4, RoundingMode.HALF_UP);
                if (firstAvg.compareTo(BigDecimal.ZERO) > 0) {
                    change = secondAvg.subtract(firstAvg)
                            .multiply(new BigDecimal("100"))
                            .divide(firstAvg, 1, RoundingMode.HALF_UP);
                    abnormal = change.compareTo(ABNORMAL_THRESHOLD) > 0;
                }
            }

            EquipmentInfo info = equipmentInfo(eid);
            out.add(EnergyTopEquipmentVO.builder()
                    .equipmentCode(info.code)
                    .equipmentName(info.name)
                    .workshop(info.workshop)
                    .value(total.setScale(0, RoundingMode.HALF_UP))
                    .change(change)
                    .abnormal(abnormal)
                    .build());
        }
        return out;
    }

    private BigDecimal sumEquipment(Long eid, LocalDate from, LocalDate to) {
        QueryWrapper<EnergyMeter> w = new QueryWrapper<EnergyMeter>()
                .eq("granularity", EnergyMeter.GRANULARITY_DAY_EQUIPMENT)
                .eq("medium", MEDIUM_ELECTRIC)
                .eq("equipment_id", eid)
                .between("record_date", from, to)
                .select("COALESCE(SUM(value),0) AS v");
        List<Map<String, Object>> r = meterMapper.selectMaps(w);
        return r.isEmpty() ? BigDecimal.ZERO : toDecimal(r.get(0).get("v"));
    }

    /** 设备基础信息（连表取编号 / 名称 / 所属车间） */
    private EquipmentInfo equipmentInfo(Long eid) {
        // 用一次子查询把三列带出来，避免为排行单独写 Mapper 方法 + XML
        List<Map<String, Object>> r = meterMapper.selectMaps(new QueryWrapper<EnergyMeter>()
                .eq("granularity", EnergyMeter.GRANULARITY_DAY_EQUIPMENT)
                .eq("equipment_id", eid)
                .select("(SELECT b.equipment_code FROM base_equipment b WHERE b.id = energy_meter.equipment_id) AS ecode",
                        "(SELECT b.equipment_name FROM base_equipment b WHERE b.id = energy_meter.equipment_id) AS ename",
                        "(SELECT w2.workshop_name FROM base_equipment b"
                                + " JOIN base_production_line l ON l.id = b.line_id"
                                + " JOIN sys_workshop w2 ON w2.id = l.workshop_id"
                                + " WHERE b.id = energy_meter.equipment_id) AS wname")
                .last("LIMIT 1"));

        if (r.isEmpty()) {
            return new EquipmentInfo(String.valueOf(eid), "设备 " + eid, "-");
        }
        Map<String, Object> m = r.get(0);
        return new EquipmentInfo(
                str(m.get("ecode"), String.valueOf(eid)),
                str(m.get("ename"), "设备 " + eid),
                str(m.get("wname"), "-"));
    }

    private record EquipmentInfo(String code, String name, String workshop) {
    }

    /* ======================================================================
       六、AI 节能优化方案
       ====================================================================== */

    /**
     * 方案列表 + 状态计数。
     * <p>
     * 计数与合计节约<b>永远</b>是全表统计（不受 status / keyword / 分页影响），
     * 否则前端筛「已完成」时顶部的「待执行 N 条」会变成 0，容易误判。
     */
    public EnergySavingPlanVO.PlanListVO plans(EnergyQuery q) {
        q.normalize();

        QueryWrapper<EnergySavingPlan> w = new QueryWrapper<>();
        if (q.getStatus() != null) {
            w.eq("status", q.getStatus());
        }
        if (q.getKeyword() != null) {
            String kw = q.getKeyword();
            w.and(x -> x.like("plan_code", kw)
                    .or().like("plan_name", kw)
                    .or().like("location", kw));
        }
        w.orderByAsc("status").orderByDesc("saving").orderByAsc("id");

        List<EnergySavingPlan> list;
        long total;
        if (q.isPaged()) {
            int size = q.getSize() == null ? 10 : q.getSize();
            List<EnergySavingPlan> all = planMapper.selectList(w);
            total = all.size();
            int fromIdx = Math.min((q.getPage() - 1) * size, all.size());
            int toIdx = Math.min(fromIdx + size, all.size());
            list = all.subList(fromIdx, toIdx);
        } else {
            list = planMapper.selectList(w);
            total = list.size();
        }

        // 全表计数
        long pending = planMapper.selectCount(new QueryWrapper<EnergySavingPlan>()
                .eq("status", EnergySavingPlan.STATUS_PENDING));
        long done = planMapper.selectCount(new QueryWrapper<EnergySavingPlan>()
                .eq("status", EnergySavingPlan.STATUS_DONE));
        long failed = planMapper.selectCount(new QueryWrapper<EnergySavingPlan>()
                .eq("status", EnergySavingPlan.STATUS_FAILED));

        return EnergySavingPlanVO.PlanListVO.builder()
                .list(list.stream().map(this::toPlanVO).toList())
                .total(total)
                .pendingCount(pending)
                .doneCount(done)
                .failedCount(failed)
                .pendingSaving(pendingSavingTotal(null))
                .build();
    }

    /** 单个方案详情 */
    public EnergySavingPlanVO planDetail(Long id) {
        EnergySavingPlan p = planMapper.selectById(id);
        if (p == null) {
            return null;
        }
        return toPlanVO(p);
    }

    /** 待执行方案月节约合计（可按车间过滤：location 以车间名开头即视为该车间） */
    private BigDecimal pendingSavingTotal(String workshop) {
        QueryWrapper<EnergySavingPlan> w = new QueryWrapper<EnergySavingPlan>()
                .eq("status", EnergySavingPlan.STATUS_PENDING);
        if (workshop != null) {
            w.likeRight("location", workshop);
        }
        return planMapper.selectList(w).stream()
                .map(p -> p.getSaving() == null ? BigDecimal.ZERO : p.getSaving())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private EnergySavingPlanVO toPlanVO(EnergySavingPlan p) {
        BigDecimal saving = p.getSaving() == null ? BigDecimal.ZERO : p.getSaving();
        return EnergySavingPlanVO.builder()
                .id(p.getId())
                .planCode(p.getPlanCode())
                .planName(p.getPlanName())
                .location(p.getLocation())
                .medium(p.getMedium())
                .status(p.getStatus())
                .saving(saving)
                .dailySaving(saving.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP))
                .reason(p.getReason())
                .advice(p.getAdvice())
                .scope(p.getScope())
                .result(p.getResult())
                .execTime(p.getExecTime())
                .source(p.getSource())
                .createTime(p.getCreateTime())
                .build();
    }

    /* ======================================================================
       七、基础查询 / 工具
       ====================================================================== */

    /** 车间级日汇总的中性条件 */
    private QueryWrapper<EnergyMeter> dayWrapper(LocalDate from, LocalDate to, String medium, String workshop) {
        QueryWrapper<EnergyMeter> w = new QueryWrapper<EnergyMeter>()
                .eq("granularity", EnergyMeter.GRANULARITY_DAY)
                .between("record_date", from, to);
        if (medium != null) {
            w.eq("medium", medium);
        }
        if (workshop != null) {
            // workshop 传的是名字，转 id 再查；转不到就查空集（宁空勿全）
            Long wid = workshopId(workshop);
            if (wid == null) {
                w.eq("id", -1L);
            } else {
                w.eq("workshop_id", wid);
            }
        }
        return w;
    }

    /** 某天的各介质合计 */
    private Map<String, BigDecimal> sumByMedium(LocalDate day, String medium, String workshop) {
        QueryWrapper<EnergyMeter> w = dayWrapper(day, day, medium, workshop)
                .select("medium", "COALESCE(SUM(value),0) AS v")
                .groupBy("medium");
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Map<String, Object> row : meterMapper.selectMaps(w)) {
            out.put(str(row.get("medium"), ""), toDecimal(row.get("v")));
        }
        return out;
    }

    /** 某天的各小时合计 */
    private Map<Integer, BigDecimal> sumByHour(LocalDate day, String medium, String workshop) {
        QueryWrapper<EnergyMeter> w = new QueryWrapper<EnergyMeter>()
                .eq("granularity", EnergyMeter.GRANULARITY_HOUR)
                .eq("record_date", day);
        if (medium != null) {
            w.eq("medium", medium);
        }
        if (workshop != null) {
            Long wid = workshopId(workshop);
            if (wid == null) {
                w.eq("id", -1L);
            } else {
                w.eq("workshop_id", wid);
            }
        }
        w.select("record_hour AS h", "COALESCE(SUM(value),0) AS v").groupBy("record_hour");

        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        for (Map<String, Object> row : meterMapper.selectMaps(w)) {
            Long h = toLong(row.get("h"));
            if (h != null) {
                out.put(h.intValue(), toDecimal(row.get("v")));
            }
        }
        return out;
    }

    /** 当日产量：从生产计划聚合（与生产模块同源，避免两处口径打架） */
    private BigDecimal outputOf(LocalDate day, String workshop) {
        QueryWrapper<ProdPlan> w = new QueryWrapper<ProdPlan>()
                .eq("plan_date", day)
                .eq("is_deleted", 0)
                .select("COALESCE(SUM(actual_qty),0) AS v");
        if (workshop != null) {
            Long wid = workshopId(workshop);
            List<Long> lineIds = lineIdsOfWorkshop(wid);
            if (lineIds.isEmpty()) {
                return BigDecimal.ZERO;
            }
            w.in("line_id", lineIds);
        }
        List<Map<String, Object>> r = prodPlanMapper.selectMaps(w);
        return r.isEmpty() ? BigDecimal.ZERO : toDecimal(r.get(0).get("v"));
    }

    /**
     * 取「最近一个有产量数据的那天」的产量。
     * <p>
     * 生产模块与能耗模块的数据截止日不一定一致（能耗灌到 11-05，生产计划只到 11-01），
     * 若硬用当天会除零。这里回退到最近的 availableQtys 日期，并在口径说明里标出实际日期，
     * 避免把「没数据」显示成「0 kWh/件」。
     *
     * @return [产量, 实际取数日期]；找不到时返回 [0, null]
     */
    private Object[] latestOutput(LocalDate beforeDay, String workshop) {
        QueryWrapper<ProdPlan> w = new QueryWrapper<ProdPlan>()
                .lt("plan_date", beforeDay.plusDays(1))
                .eq("is_deleted", 0)
                .gt("actual_qty", 0)
                .select("plan_date AS d", "COALESCE(SUM(actual_qty),0) AS v")
                .groupBy("plan_date")
                .orderByDesc("plan_date")
                .last("LIMIT 1");
        if (workshop != null) {
            List<Long> lineIds = lineIdsOfWorkshop(workshopId(workshop));
            if (lineIds.isEmpty()) {
                return new Object[]{BigDecimal.ZERO, null};
            }
            w.in("line_id", lineIds);
        }
        List<Map<String, Object>> r = prodPlanMapper.selectMaps(w);
        if (r.isEmpty()) {
            return new Object[]{BigDecimal.ZERO, null};
        }
        return new Object[]{toDecimal(r.get(0).get("v")), toLocalDate(r.get(0).get("d"))};
    }

    /** 车间下的产线 id 列表 */
    private List<Long> lineIdsOfWorkshop(Long wid) {
        if (wid == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = prodPlanMapper.selectMaps(
                new QueryWrapper<ProdPlan>()
                        .select("DISTINCT line_id AS lid")
                        .inSql("line_id", "SELECT id FROM base_production_line WHERE workshop_id = " + wid));
        List<Long> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long lid = toLong(row.get("lid"));
            if (lid != null) {
                out.add(lid);
            }
        }
        return out;
    }

    /**
     * 车间名 → id。车间只有 3 个，直接一次全查缓存住，不值得为它单独写 Mapper。
     */
    private Map<Long, String> workshopCache;

    /** 车间 id → 名称：查 sys_workshop，拿不到时按 1/2/3 兜底 */
    private Map<Long, String> workshops() {
        if (workshopCache == null) {
            Map<Long, String> m = new LinkedHashMap<>();
            List<Map<String, Object>> rows = meterMapper.selectMaps(new QueryWrapper<EnergyMeter>()
                    .select("DISTINCT workshop_id AS wid")
                    .isNotNull("workshop_id"));
            Map<Long, String> dbNames = queryWorkshopNames();
            for (Map<String, Object> row : rows) {
                Long wid = toLong(row.get("wid"));
                if (wid != null) {
                    m.put(wid, dbNames.getOrDefault(wid, defaultWorkshopName(wid)));
                }
            }
            workshopCache = m;
        }
        return workshopCache;
    }

    private Map<Long, String> queryWorkshopNames() {
        Map<Long, String> out = new LinkedHashMap<>();
        for (SysWorkshop w : workshopMapper.selectList(null)) {
            out.put(w.getId(), w.getWorkshopName());
        }
        return out;
    }

    private static String defaultWorkshopName(Long wid) {
        if (wid == null) {
            return "-";
        }
        return switch (wid.intValue()) {
            case 1 -> "A车间";
            case 2 -> "B车间";
            case 3 -> "C车间";
            default -> "车间" + wid;
        };
    }

    private String workshopName(Long wid) {
        return workshops().getOrDefault(wid, defaultWorkshopName(wid));
    }

    private Long workshopId(String name) {
        for (Map.Entry<Long, String> e : workshops().entrySet()) {
            if (e.getValue().equals(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 综合能耗折标煤 */
    private BigDecimal tce(BigDecimal elec, BigDecimal water, BigDecimal gas, BigDecimal steam) {
        BigDecimal e = elec.divide(THOUSAND, 6, RoundingMode.HALF_UP).multiply(COAL_ELECTRIC);
        BigDecimal w = water.multiply(COAL_WATER);
        BigDecimal g = gas.multiply(COAL_GAS);
        BigDecimal s = steam.multiply(COAL_STEAM);
        return e.add(w).add(g).add(s);
    }

    /** 能耗成本 */
    private BigDecimal cost(BigDecimal elec, BigDecimal water, BigDecimal gas, BigDecimal steam) {
        return elec.multiply(PRICE_ELECTRIC)
                .add(water.multiply(PRICE_WATER))
                .add(gas.multiply(PRICE_GAS))
                .add(steam.multiply(PRICE_STEAM));
    }

    private static String unitOf(String medium) {
        return switch (medium) {
            case "电" -> "kWh";
            case "水" -> "t";
            case "气" -> "Nm³";
            case "蒸汽" -> "t";
            default -> "";
        };
    }

    /** 环比百分比文案，如 "+1.2%" / "-3.4%"；基数为 0 返回 "—" */
    private static String deltaPct(BigDecimal cur, BigDecimal last) {
        if (last == null || last.compareTo(BigDecimal.ZERO) == 0) {
            return "—";
        }
        BigDecimal pct = cur.subtract(last)
                .multiply(new BigDecimal("100"))
                .divide(last, 2, RoundingMode.HALF_UP);
        return signedPct(pct);
    }

    private static String signedPct(BigDecimal pct) {
        String sign = pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + pct.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 差值文案，如 "+0.28" */
    private static String signedDiff(BigDecimal cur, BigDecimal last) {
        if (last == null) {
            return "—";
        }
        BigDecimal d = cur.subtract(last).setScale(2, RoundingMode.HALF_UP);
        return (d.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + d.toPlainString();
    }

    private static String scopeText(EnergyQuery q, LocalDate today, LocalDate yesterday, LocalDate outputDate) {
        String medium = q.getMedium() == null ? "全介质" : q.getMedium();
        String ws = q.getWorkshop() == null ? "全厂" : q.getWorkshop();
        String outputPart = outputDate == null
                ? "，产量口径：无生产数据"
                : "，产量口径：" + outputDate.format(DateTimeFormatter.ISO_DATE) + " 实际产量";
        return ws + " · " + medium + " · " + today.format(DateTimeFormatter.ISO_DATE)
                + "（环比基准 " + yesterday.format(DateTimeFormatter.ISO_DATE) + "）" + outputPart;
    }

    private static String key(Long wid, LocalDate d, String medium) {
        return wid + "|" + d + "|" + medium;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate toLocalDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate ld) {
            return ld;
        }
        if (v instanceof Date d) {
            return d.toLocalDate();
        }
        if (v instanceof java.util.Date d) {
            return new Date(d.getTime()).toLocalDate();
        }
        try {
            return LocalDate.parse(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object v, String def) {
        return v == null ? def : v.toString();
    }
}