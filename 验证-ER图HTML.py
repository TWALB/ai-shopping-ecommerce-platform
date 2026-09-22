# -*- coding: utf-8 -*-
"""快速检查 ER 图 HTML 的结构完整性"""
import io

p = r"C:\Users\翁宇\Doubao\chats\2026-09-22\new-chat-1\数码电商平台-数据库ER图.html"
h = io.open(p, encoding="utf-8").read()

print("文件字符数:", len(h))
print("div open/close:", h.count("<div"), h.count("</div>"))
print("卡片数量:", h.count('class="card"'))
print("关系标签数量:", h.count("rel-label"))
print("SVG 关系线数量:", h.count("<path"))
print("列标题数量:", h.count("col-title"))
print("文档级 title 存在:", "<title>数码电商平台数据库ER图</title>" in h)
print("DOCTYPE 存在:", h.lstrip().startswith("<!DOCTYPE html>"))
print("结构检查完成")
