# -*- coding: utf-8 -*-
"""诊断：找出产生 '//' 前缀的 data-path 值及其上下文"""
import io, re

p = r"C:\Users\翁宇\Desktop\大模型导购的数码电商平台\数码电商平台-后端接口文档.html"
h = io.open(p, encoding="utf-8").read()

paths = re.findall(r'data-path="([^"]+)"', h)
print("data-path 总数:", len(paths))
weird = [x for x in paths if x.startswith("//") or " " in x]
print("异常值:", weird)

# 找具体位置
for m in re.finditer(r'data-path="([^"]+)"', h):
    v = m.group(1)
    if v.startswith("//"):
        start = max(0, m.start() - 80)
        print("----")
        print(repr(h[start:m.end() + 40]))
