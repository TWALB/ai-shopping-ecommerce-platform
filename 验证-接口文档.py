# -*- coding: utf-8 -*-
"""验证接口文档HTML结构：div配平、接口数量、模块分布"""
import io, re
from collections import Counter

p = r"C:\Users\翁宇\Desktop\大模型导购的数码电商平台\数码电商平台-后端接口文档.html"
h = io.open(p, encoding="utf-8").read()

print("文件字符数:", len(h))
print("div open/close:", h.count("<div"), h.count("</div>"))
print("details 接口数:", h.count('<details class="api"'))
print("表格数量:", h.count("<table>"))
print("文档级 title 存在:", "<title>数码电商平台后端接口文档</title>" in h)

# 模块分布：按 data-path 前缀统计
paths = re.findall(r'data-path="([^"]+)"', h)
mods = Counter()
for p_ in paths:
    seg = p_.split("/")[1] if p_.startswith("/") and "/" in p_[1:] else p_
    mods[seg] += 1
print("\n模块接口分布:")
for k, v in mods.most_common():
    print(f"  /{k:20s} {v} 个")

# 请求方法分布
methods = re.findall(r'class="m (\w+)"', h)
print("\n请求方法分布:", dict(Counter(methods)))
print("结构检查完成")
