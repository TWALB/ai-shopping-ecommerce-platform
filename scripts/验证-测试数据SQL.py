# -*- coding: utf-8 -*-
"""校验 db/测试商品数据.sql 是否可安全导入。

检查项：
  1. 文件存在且能以 UTF-8 解码（编码错了导入会报 Data too long / Incorrect string value）
  2. 圆括号配平、语句以分号结尾
  3. SET @变量 的定义与使用一致（测试数据靠 @变量 + LAST_INSERT_ID() 串联商品/SKU/参数/图片）
  4. 涉及的表都在建表脚本里存在

只用标准库，不依赖 sqlparse。用法：
    python scripts/验证-测试数据SQL.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_DIR = os.path.join(ROOT, "db")
DATA_SQL = os.path.join(DB_DIR, "测试商品数据.sql")
SCHEMA_SQL = os.path.join(DB_DIR, "数码电商平台-数据库建表SQL.sql")

failures = []


def check(label, ok, detail=""):
    print(f"[{'PASS' if ok else 'FAIL'}] {label}{('  ' + detail) if detail else ''}")
    if not ok:
        failures.append(label)


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def strip_comments(sql):
    """去掉整行注释，避免注释里的括号/分号干扰统计。"""
    return "\n".join(ln for ln in sql.splitlines() if not ln.strip().startswith("--"))


def main():
    if not os.path.exists(DATA_SQL):
        check("测试数据脚本存在", False, DATA_SQL)
        return finish()
    check("测试数据脚本存在", True)

    try:
        raw = read(DATA_SQL)
        check("文件为 UTF-8 编码", True)
    except UnicodeDecodeError as e:
        check("文件为 UTF-8 编码", False, str(e))
        return finish()

    check("文件含 SET NAMES utf8mb4", "SET NAMES utf8mb4" in raw,
          "缺失时 Windows 命令行导入中文会乱码")

    body = strip_comments(raw)

    # 括号配平：逐语句统计，比全文统计更准
    statements = [s.strip() for s in body.split(";") if s.strip()]
    unbalanced = [s[:60].replace("\n", " ") for s in statements
                  if s.count("(") != s.count(")")]
    check("每条语句圆括号配平", not unbalanced, f"异常 {len(unbalanced)} 条" if unbalanced else "")
    for b in unbalanced:
        print("      !!", b)

    check("语句数 > 0", len(statements) > 0, f"共 {len(statements)} 条")

    # SET @变量 一致性
    defined = set(re.findall(r"SET\s+@([A-Za-z_]\w*)\s*=", raw))
    used = set(re.findall(r"@([A-Za-z_]\w*)", raw))
    undefined = used - defined
    check("引用的 @变量都已定义", not undefined, f"未定义: {sorted(undefined)}" if undefined else f"{len(defined)} 个")

    check("使用 LAST_INSERT_ID() 关联", "LAST_INSERT_ID()" in raw,
          "缺失说明商品与 SKU/参数/图片没有正确串起来")

    # 插入的表必须在建表脚本中存在
    if os.path.exists(SCHEMA_SQL):
        schema = read(SCHEMA_SQL)
        declared = set(re.findall(r"CREATE TABLE IF NOT EXISTS `(\w+)`", schema))
        inserted = set(re.findall(r"INSERT INTO `(\w+)`", raw))
        missing = inserted - declared
        check("INSERT 的表都在建表脚本中", not missing,
              f"缺表: {sorted(missing)}" if missing else f"{len(inserted)} 张")
    else:
        check("建表脚本存在", False, SCHEMA_SQL)

    tables = sorted(set(re.findall(r"INSERT INTO `(\w+)`", raw)))
    print(f"\n涉及表: {', '.join(tables)}")
    return finish()


def finish():
    if failures:
        print(f"\n结果：{len(failures)} 项未通过 → {', '.join(failures)}")
        return 1
    print("\n结果：全部通过，可安全导入")
    return 0


if __name__ == "__main__":
    sys.exit(main())
