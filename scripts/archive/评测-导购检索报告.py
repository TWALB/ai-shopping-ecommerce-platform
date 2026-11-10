# -*- coding: utf-8 -*-
"""
导购检索评测报告生成器。

用法：
    1. 先跑评测（需要 MySQL 已导入建表 SQL 与测试商品数据.sql）：
         cd code/backend && mvn -Dtest=RetrievalEvalIT test
    2. 再生成本报告：
         python 评测-导购检索报告.py

输入：code/backend/target/retrieval-eval-report.json（评测产出）
     code/backend/src/main/resources/eval/retrieval-eval.json（数据集元信息）
输出：deliverables/导购检索评测报告.md

本脚本只做格式化与统计，不重新计算指标——所有数字均来自 Java 侧的真实检索结果，
避免"报告与代码各算一套"导致论文数据无法复现。
"""

import io
import json
import os
from collections import Counter
from datetime import datetime

ROOT = os.path.dirname(os.path.abspath(__file__))
REPORT_JSON = os.path.join(ROOT, "code", "backend", "target", "retrieval-eval-report.json")
DATASET_JSON = os.path.join(ROOT, "code", "backend", "src", "main", "resources", "eval", "retrieval-eval.json")
OUT_MD = os.path.join(ROOT, "deliverables", "导购检索评测报告.md")

KIND_LABEL = {
    "retrieval": "找货检索（计入命中率）",
    "abstain": "无满足条件商品（应返回空）",
    "open": "开放式推荐（只计意图）",
    "chat": "域外/闲聊（只计意图）",
}


def load(path):
    with io.open(path, encoding="utf-8") as f:
        return json.load(f)


def pct(v):
    return "{:.1f}%".format(float(v) * 100)


def main():
    if not os.path.exists(REPORT_JSON):
        raise SystemExit("未找到评测结果，请先执行：cd code/backend && mvn -Dtest=RetrievalEvalIT test")
    report = load(REPORT_JSON)
    dataset = load(DATASET_JSON)

    meta = report["dataset"]
    slots = report["intentAndSlots"]
    systems = report["systems"]
    failures = report["failures"]
    improvement = report["improvement"]
    base, oracle, e2e = systems[0], systems[1], systems[2]

    kind_counter = Counter(item["kind"] for item in dataset["items"])
    lines = []

    lines.append("# 导购检索评测报告")
    lines.append("")
    lines.append("> 本报告由 `评测-导购检索报告.py` 依据 `code/backend/target/retrieval-eval-report.json` 自动生成，")
    lines.append("> 指标全部来自真实检索代码（`IntentService` + `ProductRetrievalService`）在标注评测集上的实际输出。")
    lines.append("")
    lines.append("| 项 | 值 |")
    lines.append("|---|---|")
    lines.append("| 生成时间 | {} |".format(report["generatedAt"]))
    lines.append("| 数据集版本 | {} |".format(meta["version"]))
    lines.append("| 样本数 | {} 条 |".format(meta["itemCount"]))
    lines.append("| 商品语料 | {} 件在售商品 |".format(meta["corpusSize"]))
    lines.append("| 召回深度 | Top{} |".format(meta["topN"]))
    lines.append("| 向量通路 | {} |".format("参与" if report["vectorEnabled"] else "关闭（强制本地检索，保证可复现）"))
    lines.append("")

    lines.append("## 一、结论摘要")
    lines.append("")
    lines.append("三条通路的核心差异如下（同数据集、同语料、同一套排序代码，只改变输入槽位来源）：")
    lines.append("")
    lines.append("| 通路 | Hit@5 | Recall@5 | Precision@5 | MRR | 预算越界率 | 跨品类错配率 | 弃答准确率 |")
    lines.append("|---|---|---|---|---|---|---|---|")
    for s in systems:
        lines.append("| {} | {} | {} | {} | {:.3f} | {} | {} | {} |".format(
            s["name"], pct(s["hitAt5"]), pct(s["recallAt5"]), pct(s["precisionAt5"]),
            s["mrr"], pct(s["budgetViolationRate"]), pct(s["crossCategoryMissRate"]), pct(s["abstainAcc"])))
    lines.append("")
    lines.append("关键读数：")
    lines.append("")
    lines.append("1. **约束遵守是本文方案的主要收益**：预算越界率从 {} 降到 {}（Oracle），"
                 "即每 100 条返回结果中，超预算商品从约 {} 条降到约 {} 条。".format(
                     pct(base["budgetViolationRate"]), pct(oracle["budgetViolationRate"]),
                     round(base["budgetViolationRate"] * 100), round(oracle["budgetViolationRate"] * 100)))
    lines.append("2. **排序质量有提升但幅度有限**：MRR 从 {:.3f} 提升到 {:.3f}（+{:.3f}），"
                 "而端到端通路只有 {:.3f}，说明槽位识别误差会吃掉检索侧的收益。".format(
                     base["mrr"], oracle["mrr"], improvement["mrrDelta"], e2e["mrr"]))
    lines.append("3. **精度与弃答是明显短板**：Precision@5 由基线的 {} 降至 {}，"
                 "且 `abstain` 类样本的正确率是 {}——语料中没有满足条件的商品时，系统从不返回空。".format(
                     pct(base["precisionAt5"]), pct(oracle["precisionAt5"]), pct(oracle["abstainAcc"])))
    lines.append("")
    lines.append("> 注意：本次语料仅 {} 件商品，Hit@5 已接近饱和（{}/{}），不足以区分方案优劣；"
                 "结论应以 MRR、预算越界率、Precision@5 与弃答准确率为准。".format(
                     meta["corpusSize"], base["hitCount"], base["retrievalSamples"]))
    lines.append("")

    lines.append("## 二、评测集构成")
    lines.append("")
    lines.append("| 样本类型 | 条数 | 说明 |")
    lines.append("|---|---|---|")
    for kind, label in KIND_LABEL.items():
        lines.append("| `{}` | {} | {} |".format(kind, kind_counter.get(kind, 0), label))
    lines.append("| **合计** | **{}** | — |".format(sum(kind_counter.values())))
    lines.append("")
    lines.append("覆盖维度：预算区间（千元档 / 3000 / 5000 / 7000 / 一万 / 一万五）、品牌（Apple / 华为 / 小米 / 联想 / 索尼）、")
    lines.append("品类（手机 / 笔记本 / 平板 / 耳机 / 音箱）、关注点（拍照 / 续航 / 游戏 / 轻薄 / 性能 / 降噪）、")
    lines.append("问法（对比 / 推荐 / 询问价格 / 域外闲聊），以及三类表达鲁棒性压力样本：中文数字金额（`两千以内`）、")
    lines.append("单位缩写（`5k以内`）、区间表达（`2000-3000`）。")
    lines.append("")
    lines.append("意图标注口径（`intentTaxonomy`）：" + dataset["intentTaxonomy"]["priority"] + "；")
    for k in ("compare", "find_product", "recommend", "chat"):
        lines.append("- **{}**：{}".format(k, dataset["intentTaxonomy"][k]))
    lines.append("")
    lines.append("> " + dataset["intentTaxonomy"]["note"] + "。")
    lines.append("")

    lines.append("## 三、意图与槽位识别")
    lines.append("")
    lines.append("| 指标 | 正确 / 总数 | 准确率 |")
    lines.append("|---|---|---|")
    lines.append("| 意图（4 类严格匹配） | {} / {} | {} |".format(
        slots["intentCorrect"], slots["intentSamples"], pct(slots["intentAcc"])))
    lines.append("| 意图（检索类 / 非检索类二分类） | {} / {} | {} |".format(
        slots["intentBinaryCorrect"], slots["intentBinarySamples"], pct(slots["intentBinaryAcc"])))
    lines.append("| 品类槽位 | {} / {} | {} |".format(
        slots["categoryCorrect"], slots["categorySamples"], pct(slots["categoryAcc"])))
    lines.append("| 预算槽位 | {} / {} | {} |".format(
        slots["budgetCorrect"], slots["budgetSamples"], pct(slots["budgetAcc"])))
    lines.append("")
    lines.append("四个意图标签的边界本身带有主观性（“XX 推荐”既可视为找货也可视为推荐），"
                 "因此额外提供不受标签细则影响的二分类口径：{}。".format(
                     "系统能稳定区分“该不该推商品”，但细分意图存在系统性偏移"
                     if slots["intentBinaryAcc"] >= slots["intentAcc"] else "二分类与细分类表现接近"))
    lines.append("")

    lines.append("## 四、失败样本（以 B1 Oracle 判定，共 {} 条）".format(len(failures)))
    lines.append("")
    if failures:
        lines.append("| ID | 用户问句 | 期望商品 | 实际返回 | 失败类型 |")
        lines.append("|---|---|---|---|---|")
        for f in failures:
            lines.append("| {} | {} | {} | {} | {} |".format(
                f["id"], f["query"],
                f["expected"] if f["expected"] else "（空）",
                f["got"] if f["got"] else "（空）",
                f["errorType"]))
        lines.append("")
        types = Counter(f["errorType"] for f in failures)
        lines.append("失败类型分布：" + "，".join("{} {} 条".format(k, v) for k, v in types.items()) + "。")
    else:
        lines.append("无失败样本。")
    lines.append("")

    lines.append("## 五、结论与改进建议")
    lines.append("")
    lines.append("### 5.1 已验证的收益")
    lines.append("")
    lines.append("- 结构化槽位召回（品类/预算/关键词/品牌加权）在**预算约束**上明显优于纯关键词匹配，"
                 "这是“大模型理解需求”落地为可量化收益的主要证据。")
    lines.append("- Oracle 与端到端通路的差值（MRR {:.3f} vs {:.3f}）量化了槽位识别对最终效果的损耗，"
                 "可直接作为“先修意图再修排序”的论证依据。".format(oracle["mrr"], e2e["mrr"]))
    lines.append("")
    lines.append("### 5.2 已定位的缺陷（按优先级）")
    lines.append("")
    lines.append("1. **不合条件的请求不做弃答**。`localRetrieve` 的兜底逻辑是“命中不足就放开过滤”，"
                 "导致 `1000 元以下的耳机` 会直接推出 2499 元的索尼耳机。修复方向：预算/品类过滤命中为空时"
                 "返回空结果并提示放宽条件，而不是回填其它品类。")
    lines.append("2. **Top5 被凑满**。即使只有 1 件真正匹配，也会补足 5 条，直接拉低 Precision@5 与用户观感。"
                 "修复方向：低于相关度阈值的结果直接截断，允许返回少于 5 条。")
    lines.append("3. **中文金额与单位不识别**。`两千以内`、`5k以内`、`2000-3000` 三种表达预算均被解析为 0，"
                 "等于丢弃了用户最强的约束条件。修复方向：预算抽取增加中文数字、k/w 单位与区间表达解析。")
    lines.append("4. **品类词表覆盖窄**。`游戏本`、`千元机`、`iPhone17Pro` 均无法命中品类，"
                 "导致品类槽位准确率停留在 {}。修复方向：补充同义词表，或改为对商品标题做品类归属判断。".format(
                     pct(slots["categoryAcc"])))
    lines.append("")
    lines.append("### 5.3 本评测的局限")
    lines.append("")
    lines.append("- 语料仅 {} 件商品，Hit@5 饱和，绝对命中率不能外推到生产规模；本评测的结论是**相对比较**，"
                 "不是绝对水平。".format(meta["corpusSize"]))
    lines.append("- 可接受集合由人工标注，多数样本只有 1–2 件，Precision@5 的理论上限即 0.2–0.4。")
    lines.append("- 向量通路未纳入对比：不同机器的大模型 Key 配置与向量库同步状态不同，纳入后会破坏可复现性；"
                 "需在固定环境下单独出一份对照表。")
    lines.append("- 评测只覆盖检索与意图，不覆盖回答生成质量（幻觉、格式、语气），后者需要人工评分或 LLM-as-judge。")
    lines.append("")

    os.makedirs(os.path.dirname(OUT_MD), exist_ok=True)
    with io.open(OUT_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    print("report written:", OUT_MD)
    print("samples:", meta["itemCount"], "failures:", len(failures))
    print("B1 hit@5 {:.3f} -> precision {:.3f}, abstain {:.3f}".format(
        oracle["hitAt5"], oracle["precisionAt5"], oracle["abstainAcc"]))


if __name__ == "__main__":
    main()
