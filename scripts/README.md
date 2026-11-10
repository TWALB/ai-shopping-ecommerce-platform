# 工具脚本

## 当前在用

| 脚本 | 用途 | 前置条件 |
|---|---|---|
| `eval-report.py` | 读评测产物 JSON，生成 `deliverables/导购检索评测报告.md` | 已跑过 `RetrievalEvalIT`，`code/backend/target/` 下有 `retrieval-eval-report*.json` |
| `verify-admin-ai.py` | 管理端 AI 接口冒烟：登录后依次调用观测类接口，打印关键字段 | 后端已在 `http://127.0.0.1:8080` 启动，`admin/admin123` 可用 |
| `验证-测试数据SQL.py` | 校验 `db/测试商品数据.sql` 导入结果是否完整 | MySQL 已导入建表与测试数据 |

### 评测报告

```bash
cd code/backend
mvn -Dtest=RetrievalEvalIT test                      # 本地结构化通路 → target/retrieval-eval-report.json
mvn -Dtest=RetrievalEvalIT -Deval.vector=true test   # 向量通路 → target/retrieval-eval-report-vector.json

cd ../..
python scripts/eval-report.py                        # 生成 deliverables/导购检索评测报告.md
```

报告会读取 `retrieval-eval-report-before.json` 作为「修复前」对照；该文件不存在时只输出单组数据，不报错。

### 管理端冒烟

```bash
python scripts/verify-admin-ai.py
```

脚本用标准库 `urllib`，不需要 `pip install`。它会登录拿 token，再逐个调用观测类接口；任一路径 404 通常说明后端还是旧进程，重启即可。

## archive/（已停用）

| 脚本 | 为什么停用 |
|---|---|
| `评测-导购检索报告.py` | 被 `eval-report.py` 取代：新版支持修复前后对照与本地/向量双通路 |
| `提取-交易模块接口.py` | 一次性任务，产物已固化到 `docs/_交易模块接口提取.txt` |

保留是为了让「报告里的数字是怎么来的」可追溯，不建议再运行。
