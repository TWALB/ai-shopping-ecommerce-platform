# -*- coding: utf-8 -*-
"""验证测试商品数据SQL：可解析性、语句数量、括号配平"""
import sqlparse

path = r"C:\Users\翁宇\Desktop\大模型导购的数码电商平台\测试商品数据.sql"
with open(path, encoding="utf-8") as f:
    sql = f.read()

print("字符数:", len(sql))
print("括号配平:", sql.count("("), "/", sql.count(")"))
parsed = sqlparse.parse(sql)
print("语句块数:", len(parsed))
kinds = {}
for s in parsed:
    t = s.get_type()
    kinds[t] = kinds.get(t, 0) + 1
print("语句类型分布:", kinds)

bad = [s.value[:50].replace("\n", " ") for s in parsed if not s.value.rstrip().endswith(";")]
print("未以分号结尾（应为0，尾部注释除外）:", len(bad))
for b in bad:
    print("  !!", b)

# 检查变量引用一致性
print("\nSET 变量数:", sql.count("SET @"))
print("LAST_INSERT_ID 使用数:", sql.count("LAST_INSERT_ID()"))
print("验证完成")
