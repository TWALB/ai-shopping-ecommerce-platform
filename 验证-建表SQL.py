# -*- coding: utf-8 -*-
"""验证《数码电商平台-数据库建表SQL.sql》的完整性：括号配平、语句可解析、表数量、主键与注释"""
import re
import sqlparse

path = r"C:\Users\翁宇\Doubao\chats\2026-09-22\new-chat-1\数码电商平台-数据库建表SQL.sql"
with open(path, encoding="utf-8") as f:
    sql = f.read()

print("== 基本信息 ==")
print("字符数:", len(sql))
print("左括号数:", sql.count("("), "| 右括号数:", sql.count(")"))

print("\n== sqlparse 解析 ==")
parsed = sqlparse.parse(sql)
print("解析出的语句块数:", len(parsed))

creates = [s for s in parsed if s.get_type() == "CREATE"]
print("CREATE 语句数:", len(creates))

table_names = []
for s in creates:
    m = re.search(r"CREATE TABLE IF NOT EXISTS `(\w+)`", s.value, re.I)
    if m:
        table_names.append(m.group(1))
print("建表数量:", len(table_names))
for t in table_names:
    print("  -", t)

print("\n== 完整性检查 ==")
bad = [s.value[:60].replace("\n", " ") for s in parsed if not s.value.rstrip().endswith(";")]
print("未以分号结尾的语句数:", len(bad))
for b in bad:
    print("  !!", b)

for s in creates:
    name = re.search(r"`(\w+)`", s.value)
    nm = name.group(1) if name else "?"
    if "PRIMARY KEY" not in s.value.upper():
        print("  !! 缺主键:", nm)
    if "COMMENT" not in s.value.upper():
        print("  !! 缺表注释:", nm)
print("主键/表注释检查完成")

print("\n== 初始数据检查 ==")
print("INSERT 语句数:", sum(1 for s in parsed if s.get_type() == "INSERT"))
print("\n验证通过")
