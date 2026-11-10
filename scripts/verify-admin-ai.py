"""管理端 AI 接口冒烟脚本：登录 → 逐个调用 10 个新接口，打印关键字段。

用法：python scripts/verify-admin-ai.py
依赖：后端已启动在 http://127.0.0.1:8080
"""
import json
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8080/api"
ADMIN = {"username": "admin", "password": "admin123"}


def call(path, method="GET", body=None, token=None):
    path = urllib.parse.quote(path, safe="/?=&:,")
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode("utf-8") if body is not None else None
    with urllib.request.urlopen(req, data) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    login = call("/auth/login", "POST", ADMIN)
    token = login["data"]["token"]
    print("login ok, role =", login["data"].get("role"))

    checks = [
        ("知识库列表", "/admin/ai/knowledge/products?pageNum=1&pageSize=5"),
        ("切片概览", "/admin/ai/knowledge/chunk-summary"),
        ("切片明细", "/admin/ai/knowledge/chunks?productId=1"),
        ("检索", "/admin/ai/knowledge/search?query=5000以内的手机&topK=5&vector=false"),
        ("导购任务", "/admin/ai/session/page?pageNum=1&pageSize=5"),
        ("运行记录", "/admin/ai/run/page?pageNum=1&pageSize=5"),
        ("推荐商品", "/admin/ai/recommend/top?days=90&limit=5"),
        ("商品问答", "/admin/ai/qa/page?pageNum=1&pageSize=5"),
        ("评价分析", "/admin/ai/comment/analysis?days=90"),
        ("运营报告", "/admin/ai/report/overview?days=7"),
        ("AI 配置", "/admin/ai/config"),
    ]
    for name, path in checks:
        try:
            r = call(path, token=token)
            d = r.get("data")
            if isinstance(d, dict) and "list" in d:
                brief = "total=%s 首条=%s" % (d["total"], json.dumps(d["list"][0], ensure_ascii=False)[:120] if d["list"] else "空")
            elif isinstance(d, list):
                brief = "条数=%s 首条=%s" % (len(d), json.dumps(d[0], ensure_ascii=False)[:120] if d else "空")
            else:
                brief = json.dumps(d, ensure_ascii=False)[:220]
            print("[OK] %-8s %s" % (name, brief))
        except Exception as e:
            print("[FAIL] %-8s %s" % (name, e))


if __name__ == "__main__":
    main()
