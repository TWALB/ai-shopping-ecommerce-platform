package com.digitalmall.eval;

import com.digitalmall.service.RetrievalEvalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 导购检索评测的执行入口（集成测试）。
 *
 * 刻意命名为 *IT 而非 *Test：Surefire 默认只收 *Test / Test* / *Tests / *TestCase，
 * 因此它不会被 `mvn test` 自动拉起，仅有 MySQL 环境时才显式执行：
 *
 *   mvn -Dtest=RetrievalEvalIT test
 *
 * 产物：target/retrieval-eval-report.json（完整报告）与 stdout 上的 Markdown 摘要表。
 * 说明：webEnvironment=NONE，不占用 8080，可与已在运行的后端共存。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RetrievalEvalIT {

    @Autowired
    private RetrievalEvalService evalService;

    @Test
    @DisplayName("跑通标注评测集并输出指标报告")
    @SuppressWarnings("unchecked")
    void runEvaluation() throws Exception {
        Assertions.assertTrue(evalService.datasetSize() >= 60, "评测集样本数应不少于 60 条");

        Map<String, Object> report = evalService.run(false);
        List<Map<String, Object>> systems = (List<Map<String, Object>>) report.get("systems");
        Assertions.assertEquals(3, systems.size(), "应包含 B0 基线、B1 Oracle、B2 端到端三条通路");

        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Path out = Path.of("target", "retrieval-eval-report.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json, StandardCharsets.UTF_8);

        System.out.println();
        System.out.println("================ 导购检索评测摘要 ================");
        Map<String, Object> slots = (Map<String, Object>) report.get("intentAndSlots");
        System.out.printf("意图(4类) %.1f%%  意图(检索/非检索) %.1f%%  品类槽位 %.1f%%  预算槽位 %.1f%%%n",
                pct(slots.get("intentAcc")), pct(slots.get("intentBinaryAcc")),
                pct(slots.get("categoryAcc")), pct(slots.get("budgetAcc")));
        System.out.println("--------------------------------------------------");
        System.out.printf("%-34s %8s %9s %8s %8s %8s%n", "通路", "Hit@5", "Precision", "MRR", "越界率", "错品类");
        for (Map<String, Object> s : systems) {
            System.out.printf("%-34s %8s %9s %8s %8s %8s%n",
                    s.get("name"), pct(s.get("hitAt5")), pct(s.get("precisionAt5")),
                    String.format("%.3f", ((Number) s.get("mrr")).doubleValue()),
                    pct(s.get("budgetViolationRate")), pct(s.get("crossCategoryMissRate")));
        }
        System.out.println("弃答准确率：" + pct(systems.get(0).get("abstainAcc")) + " / "
                + pct(systems.get(1).get("abstainAcc")) + " / " + pct(systems.get(2).get("abstainAcc")));
        Map<String, Object> imp = (Map<String, Object>) report.get("improvement");
        System.out.printf("相对基线提升：Hit@5 %+.1f pt，Precision %+.1f pt，MRR %+.3f%n",
                ((Number) imp.get("hitAt5Delta")).doubleValue() * 100,
                ((Number) imp.get("precisionDelta")).doubleValue() * 100,
                ((Number) imp.get("mrrDelta")).doubleValue());
        System.out.println("失败样本数：" + ((List<?>) report.get("failures")).size());
        System.out.println("报告已写入：" + out.toAbsolutePath());
        System.out.println("==================================================");
    }

    private double pct(Object rate) {
        return ((Number) rate).doubleValue() * 100;
    }
}
