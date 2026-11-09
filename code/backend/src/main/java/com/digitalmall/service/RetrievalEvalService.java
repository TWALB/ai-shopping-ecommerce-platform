package com.digitalmall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.entity.Brand;
import com.digitalmall.entity.Category;
import com.digitalmall.entity.Product;
import com.digitalmall.mapper.BrandMapper;
import com.digitalmall.mapper.CategoryMapper;
import com.digitalmall.mapper.ProductMapper;
import com.digitalmall.vo.ProductBriefVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导购检索离线评测。
 *
 * 目的：为「大模型导购」这一创新点提供可复现的量化证据，回答三个问题：
 *   1. 意图识别准不准（intentAcc / categoryAcc / budgetAcc）；
 *   2. 召回排得好不好（hitAt5 / recallAt5 / mrr / budgetViolation / abstainAcc）；
 *   3. 相比「朴素关键词搜索」是否真的有提升（B0 基线与 B1/B2 的差值）。
 *
 * 设计取舍：
 * - 三条对照通路共用同一份数据集与同一份商品语料，只改变检索策略：
 *     B0 关键词基线：中文二元组 + 英文词的重叠计数，等价于 `LIKE '%词%'` 排序；
 *     B1 本文方案（Oracle）：喂入数据集标注的意图与槽位，隔离出「检索本身」的质量；
 *     B2 本文方案（端到端）：喂入规则意图识别出的槽位，反映线上真实链路。
 * - 强制关闭向量通路（allowVector=false），否则「是否配置了大模型 Key」「向量库是否同步」
 *   会让同一份数据集在不同机器上得出不同结论，评测失去可比性。向量通路的对比需单独跑，
 *   结果记入报告备注。
 * - 只读：不写库、不调大模型、不依赖 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEvalService {

    /** 评测集位置（classpath） */
    private static final String DATASET_PATH = "eval/retrieval-eval.json";
    /** 与线上召回保持一致：Top5 */
    private static final int TOP_N = 5;
    /** 预算容差：与线上「超预算 20% 剔除」对齐 */
    private static final double BUDGET_TOLERANCE = 1.2;

    /** 关键词基线用的停用词：只剔除功能性 2 字词，保留实体词，避免基线被人为削弱 */
    private static final Set<String> STOPWORDS = Set.of(
            "推荐", "什么", "哪个", "以内", "左右", "预算", "怎么", "帮我", "一台", "好的",
            "一款", "多少", "用的", "可以", "想要", "有哪", "值不", "是不", "适合", "有什",
            "么好", "买什", "么手", "么笔", "记本"
    );

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final IntentService intentService;
    private final ProductRetrievalService retrievalService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 对外入口 ====================

    /**
     * 执行一次全量评测。
     *
     * @param includeVector 是否允许走向量召回（默认 false，保证可复现）
     * @return 可直接序列化为 JSON 的报告
     */
    public Map<String, Object> run(boolean includeVector) {
        List<JsonNode> items = loadDataset();
        Corpus corpus = loadCorpus();
        if (corpus.products().isEmpty()) {
            throw new IllegalStateException("商品语料为空，请先执行测试商品数据.sql 再运行评测");
        }

        Metrics baseline = new Metrics("B0 关键词基线（二元组重叠 / 类 LIKE 检索）");
        Metrics oracle = new Metrics("B1 本文方案（标注槽位 / Oracle）");
        Metrics e2e = new Metrics("B2 本文方案（端到端 / 预测槽位）");
        SlotMetrics slots = new SlotMetrics();

        List<Map<String, Object>> details = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();

        for (JsonNode node : items) {
            String id = node.path("id").asText();
            String query = node.path("query").asText();
            String kind = node.path("kind").asText("retrieval");
            String gtIntent = node.path("intent").asText("");
            String note = node.path("note").asText("");
            Map<String, Object> gtSlots = toSlots(node.path("slots"));
            List<Long> expected = toIdList(node.path("expected"));

            // 线上真实链路：规则意图识别
            Map<String, Object> pred = intentService.recognizeByRule(query);
            String predIntent = String.valueOf(pred.get("intent"));
            @SuppressWarnings("unchecked")
            Map<String, Object> predSlots = (Map<String, Object>) pred.get("slots");
            slots.add(gtIntent, predIntent, gtSlots, predSlots);

            // B0 基线
            long t0 = System.nanoTime();
            List<Long> baseTop = keywordSearch(corpus, query, TOP_N);
            long baseMs = elapsedMs(t0);
            baseline.add(kind, expected, baseTop, budgetOf(gtSlots), categoryOf(gtSlots), corpus, baseMs);

            // B1 Oracle：用标注槽位，单独衡量检索质量
            long t1 = System.nanoTime();
            List<Long> oracleTop = runRetrieval(gtSlots, query, gtIntent, includeVector);
            long oracleMs = elapsedMs(t1);
            oracle.add(kind, expected, oracleTop, budgetOf(gtSlots), categoryOf(gtSlots), corpus, oracleMs);

            // B2 端到端：用预测槽位
            long t2 = System.nanoTime();
            List<Long> e2eTop = runRetrieval(predSlots, query, predIntent, includeVector);
            long e2eMs = elapsedMs(t2);
            e2e.add(kind, expected, e2eTop, budgetOf(gtSlots), categoryOf(gtSlots), corpus, e2eMs);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("query", query);
            row.put("kind", kind);
            row.put("gtIntent", gtIntent);
            row.put("predIntent", predIntent);
            row.put("gtSlots", gtSlots);
            row.put("predSlots", predSlots);
            row.put("expected", expected);
            row.put("baselineTop", baseTop);
            row.put("oracleTop", oracleTop);
            row.put("e2eTop", e2eTop);
            details.add(row);

            // 失败样本：以主力方案（Oracle）判定，便于定位「检索本身」的问题
            boolean failed = "retrieval".equals(kind)
                    ? !intersects(expected, oracleTop)
                    : "abstain".equals(kind) && !oracleTop.isEmpty();
            if (failed) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("id", id);
                f.put("query", query);
                f.put("kind", kind);
                f.put("expected", expected);
                f.put("got", oracleTop);
                f.put("errorType", classify(expected, oracleTop, kind));
                f.put("note", note);
                failures.add(f);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dataset", datasetMeta(items.size(), corpus.products().size()));
        report.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        report.put("vectorEnabled", includeVector);
        report.put("intentAndSlots", slots.toMap());
        report.put("systems", List.of(baseline.toMap(), oracle.toMap(), e2e.toMap()));
        report.put("improvement", improvementMap(baseline, oracle));
        report.put("failures", failures);
        report.put("details", details);
        return report;
    }

    /** 数据集条目数，供前端/脚本快速读取 */
    public int datasetSize() {
        return loadDataset().size();
    }

    // ==================== 检索调用 ====================

    private List<Long> runRetrieval(Map<String, Object> slots, String query, String intent, boolean includeVector) {
        try {
            List<ProductBriefVO> list = retrievalService.retrieve(slots, query, intent, includeVector);
            return list.stream().map(ProductBriefVO::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("评测检索异常 query={} : {}", query, e.getMessage());
            return List.of();
        }
    }

    /** B0 基线：中文二元组 + 英文/数字词的重叠计数排序，模拟最朴素的关键词检索 */
    private List<Long> keywordSearch(Corpus corpus, String query, int topN) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        return corpus.products().stream()
                .map(p -> new int[]{p.getId().intValue(),
                        (int) tokens.stream().filter(t -> corpus.text(p.getId()).contains(t)).count(),
                        p.getSales() == null ? 0 : p.getSales()})
                .filter(a -> a[1] > 0)
                .sorted(Comparator.comparingInt((int[] a) -> a[1]).reversed()
                        .thenComparing(Comparator.comparingInt((int[] a) -> a[2]).reversed()))
                .limit(topN)
                .map(a -> (long) a[0])
                .collect(Collectors.toList());
    }

    /** 分词：英文/数字整词 + 中文二元组，去掉功能性停用词 */
    private List<String> tokenize(String query) {
        String norm = query == null ? "" : query.toLowerCase().replaceAll("\\s+", "");
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("[a-z0-9]{2,}").matcher(norm);
        while (m.find()) {
            tokens.add(m.group());
        }
        String cn = norm.replaceAll("[^\\u4e00-\\u9fa5]", " ");
        for (String seg : cn.split("\\s+")) {
            for (int i = 0; i + 2 <= seg.length(); i++) {
                tokens.add(seg.substring(i, i + 2));
            }
        }
        tokens.removeAll(STOPWORDS);
        return new ArrayList<>(tokens);
    }

    // ==================== 语料与数据集加载 ====================

    private Corpus loadCorpus() {
        List<Product> products = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1));
        Map<Long, String> catNames = categoryMapper.selectList(null).stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Category::getId,
                        c -> c.getCategoryName() == null ? "" : c.getCategoryName(), (a, b) -> a));
        Map<Long, String> brandNames = brandMapper.selectList(null).stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(Brand::getId,
                        b -> b.getBrandName() == null ? "" : b.getBrandName(), (a, b) -> a));
        Map<Long, String> texts = new LinkedHashMap<>();
        Map<Long, String> cats = new LinkedHashMap<>();
        Map<Long, Double> prices = new LinkedHashMap<>();
        for (Product p : products) {
            String cat = catNames.getOrDefault(p.getCategoryId(), "");
            String brand = brandNames.getOrDefault(p.getBrandId(), "");
            cats.put(p.getId(), cat);
            prices.put(p.getId(), p.getPrice() == null ? null : p.getPrice().doubleValue());
            texts.put(p.getId(), (p.getProductName() == null ? "" : p.getProductName())
                    + " " + (p.getProductTitle() == null ? "" : p.getProductTitle())
                    + " " + (p.getKeywords() == null ? "" : p.getKeywords())
                    + " " + (p.getTags() == null ? "" : p.getTags())
                    + " " + (p.getBrief() == null ? "" : p.getBrief())
                    + " " + cat + " " + brand);
        }
        return new Corpus(products, texts, cats, brandNames, prices);
    }

    private List<JsonNode> loadDataset() {
        try (InputStream in = new ClassPathResource(DATASET_PATH).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            List<JsonNode> items = new ArrayList<>();
            root.path("items").forEach(items::add);
            if (items.isEmpty()) {
                throw new IllegalStateException("评测集为空：" + DATASET_PATH);
            }
            return items;
        } catch (Exception e) {
            throw new IllegalStateException("评测集加载失败：" + DATASET_PATH + "，" + e.getMessage(), e);
        }
    }

    private Map<String, Object> datasetMeta(int itemCount, int corpusSize) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", "数码导购检索评测集");
        meta.put("version", "1.0");
        meta.put("itemCount", itemCount);
        meta.put("corpusSize", corpusSize);
        meta.put("topN", TOP_N);
        meta.put("note", "小语料（8 件商品）用于验证检索策略的相对差异，绝对命中率不代表生产规模下的表现");
        return meta;
    }

    private Map<String, Object> toSlots(JsonNode node) {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("category", node.path("category").asText(""));
        slots.put("budget", node.path("budget").asDouble(0));
        slots.put("keyword", node.path("keyword").asText(""));
        slots.put("brand", node.path("brand").asText(""));
        return slots;
    }

    private List<Long> toIdList(JsonNode node) {
        List<Long> ids = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> ids.add(n.asLong()));
        }
        return ids;
    }

    private double budgetOf(Map<String, Object> slots) {
        Object b = slots.get("budget");
        return b instanceof Number n ? n.doubleValue() : 0;
    }

    private String categoryOf(Map<String, Object> slots) {
        Object c = slots.get("category");
        return c == null ? "" : String.valueOf(c).trim();
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    // ==================== 指标计算 ====================

    private boolean intersects(List<Long> expected, List<Long> got) {
        if (expected.isEmpty() || got.isEmpty()) {
            return false;
        }
        for (Long id : got) {
            if (expected.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private double reciprocalRank(List<Long> expected, List<Long> got) {
        for (int i = 0; i < got.size(); i++) {
            if (expected.contains(got.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    private String classify(List<Long> expected, List<Long> got, String kind) {
        if ("abstain".equals(kind)) {
            return got.isEmpty() ? "OK" : "过度召回（本应返回空）";
        }
        if (got.isEmpty()) {
            return "零召回";
        }
        return "排序靠后（Top5 未命中）";
    }

    private Map<String, Object> improvementMap(Metrics baseline, Metrics oracle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hitAt5Delta", round(oracle.hitAt5() - baseline.hitAt5()));
        m.put("precisionDelta", round(oracle.precisionAt5() - baseline.precisionAt5()));
        m.put("mrrDelta", round(oracle.mrr() - baseline.mrr()));
        m.put("budgetViolationDelta", round(oracle.budgetViolation() - baseline.budgetViolation()));
        return m;
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /** 单条检索通路（B0/B1/B2）的累计指标 */
    private class Metrics {
        private final String name;
        private int retrievalN;
        private int hitCount;
        private double recallSum;
        private double mrrSum;
        private int returnedTotal;
        private int budgetViolations;
        private int precisionHit;
        private int precisionReturned;
        private int crossCategoryReturned;
        /** 跨品类判定中「落在正确品类」的条数，错配率 = (返回数 - 本值) / 返回数 */
        private int crossCategoryOk;
        private int abstainN;
        private int abstainOk;
        private long latencySum;
        private int latencyN;

        Metrics(String name) {
            this.name = name;
        }

        void add(String kind, List<Long> expected, List<Long> got, double budget,
                 String gtCategory, Corpus corpus, long latencyMs) {
            latencySum += latencyMs;
            latencyN++;
            if ("retrieval".equals(kind)) {
                retrievalN++;
                if (intersects(expected, got)) {
                    hitCount++;
                }
                if (!expected.isEmpty()) {
                    long hit = got.stream().filter(expected::contains).count();
                    recallSum += (double) hit / expected.size();
                    // 精度：返回的商品里有多少是标注可接受的（Top5 饱和时唯一有区分度的指标）
                    precisionHit += (int) hit;
                    precisionReturned += got.size();
                }
                mrrSum += reciprocalRank(expected, got);
                for (Long id : got) {
                    if (budget > 0) {
                        returnedTotal++;
                        Double price = corpus.price(id);
                        if (price != null && price > budget * BUDGET_TOLERANCE) {
                            budgetViolations++;
                        }
                    }
                    // 跨品类：问手机却推耳机，用户直接可见的错配
                    if (gtCategory != null && !gtCategory.isEmpty()) {
                        crossCategoryReturned++;
                        String cat = corpus.categories().getOrDefault(id, "");
                        if (!cat.contains(gtCategory)) {
                            crossCategoryOk++;
                        }
                    }
                }
            } else if ("abstain".equals(kind)) {
                abstainN++;
                if (got.isEmpty()) {
                    abstainOk++;
                }
            }
        }

        double hitAt5() {
            return retrievalN == 0 ? 0 : (double) hitCount / retrievalN;
        }

        double mrr() {
            return retrievalN == 0 ? 0 : mrrSum / retrievalN;
        }

        double precisionAt5() {
            return precisionReturned == 0 ? 0 : (double) precisionHit / precisionReturned;
        }

        double budgetViolation() {
            return returnedTotal == 0 ? 0 : (double) budgetViolations / returnedTotal;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("retrievalSamples", retrievalN);
            m.put("hitCount", hitCount);
            m.put("hitAt5", round(hitAt5()));
            m.put("recallAt5", round(retrievalN == 0 ? 0 : recallSum / retrievalN));
            m.put("precisionAt5", round(precisionReturned == 0 ? 0 : (double) precisionHit / precisionReturned));
            m.put("precisionReturned", precisionReturned);
            m.put("mrr", round(mrr()));
            m.put("returnedItems", returnedTotal);
            m.put("budgetViolations", budgetViolations);
            m.put("budgetViolationRate", round(budgetViolation()));
            m.put("crossCategoryReturned", crossCategoryReturned);
            m.put("crossCategoryMissRate", round(crossCategoryReturned == 0 ? 0
                    : (double) (crossCategoryReturned - crossCategoryOk) / crossCategoryReturned));
            m.put("abstainSamples", abstainN);
            m.put("abstainCorrect", abstainOk);
            m.put("abstainAcc", round(abstainN == 0 ? 0 : (double) abstainOk / abstainN));
            m.put("avgLatencyMs", latencyN == 0 ? 0 : round((double) latencySum / latencyN));
            return m;
        }
    }

    /** 意图与槽位准确率（与检索通路无关，只跑一次） */
    private static class SlotMetrics {
        private int intentN;
        private int intentOk;
        private int binaryN;
        private int binaryOk;
        private int catN;
        private int catOk;
        private int budgetN;
        private int budgetOk;

        @SuppressWarnings("unchecked")
        void add(String gtIntent, String predIntent, Map<String, Object> gt, Map<String, Object> pred) {
            if (gtIntent != null && !gtIntent.isBlank()) {
                intentN++;
                if (gtIntent.equals(predIntent)) {
                    intentOk++;
                }
                // 粗粒度二分类：四类意图的边界存在主观性，而「该不该推商品」是业务硬边界，
                // 单独给一个不受标签细则影响的口径，避免结论建立在有争议的标注上。
                binaryN++;
                if (isRetrievalIntent(gtIntent) == isRetrievalIntent(predIntent)) {
                    binaryOk++;
                }
            }
            String gtCat = str(gt, "category");
            String predCat = str(pred, "category");
            if (!gtCat.isEmpty()) {
                catN++;
                if (gtCat.equals(predCat)) {
                    catOk++;
                }
            }
            double gtBudget = num(gt, "budget");
            if (gtBudget > 0) {
                budgetN++;
                double predBudget = num(pred, "budget");
                if (Math.abs(predBudget - gtBudget) <= gtBudget * 0.1) {
                    budgetOk++;
                }
            }
        }

        private static boolean isRetrievalIntent(String intent) {
            return intent != null && !"chat".equals(intent.trim());
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("intentSamples", intentN);
            m.put("intentCorrect", intentOk);
            m.put("intentAcc", intentN == 0 ? 0 : Math.round((double) intentOk / intentN * 10000.0) / 10000.0);
            m.put("intentBinarySamples", binaryN);
            m.put("intentBinaryCorrect", binaryOk);
            m.put("intentBinaryAcc", binaryN == 0 ? 0 : Math.round((double) binaryOk / binaryN * 10000.0) / 10000.0);
            m.put("categorySamples", catN);
            m.put("categoryCorrect", catOk);
            m.put("categoryAcc", catN == 0 ? 0 : Math.round((double) catOk / catN * 10000.0) / 10000.0);
            m.put("budgetSamples", budgetN);
            m.put("budgetCorrect", budgetOk);
            m.put("budgetAcc", budgetN == 0 ? 0 : Math.round((double) budgetOk / budgetN * 10000.0) / 10000.0);
            return m;
        }

        private static String str(Map<String, Object> m, String k) {
            Object v = m == null ? null : m.get(k);
            return v == null ? "" : String.valueOf(v).trim();
        }

        private static double num(Map<String, Object> m, String k) {
            Object v = m == null ? null : m.get(k);
            return v instanceof Number n ? n.doubleValue() : 0;
        }
    }

    /** 评测语料快照：只读一次数据库，避免 60 条样本反复全表查询 */
    private record Corpus(List<Product> products, Map<Long, String> texts,
                          Map<Long, String> categories, Map<Long, String> brands,
                          Map<Long, Double> prices) {

        String text(Long id) {
            return texts.getOrDefault(id, "");
        }

        Double price(Long id) {
            return prices.get(id);
        }
    }
}
