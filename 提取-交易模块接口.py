# -*- coding: utf-8 -*-
"""从接口文档HTML提取 购物车/订单/支付 三个模块的接口定义"""
import re
import html as html_mod

path = r"C:\Users\翁宇\Desktop\大模型导购的数码电商平台\数码电商平台-后端接口文档.html"
with open(path, encoding="utf-8") as f:
    content = f.read()

# 去掉标签，保留文本（接口文档是纯文本结构，去掉标签即可）
text = re.sub(r"<script[\s\S]*?</script>", "", content)
text = re.sub(r"<style[\s\S]*?</style>", "", text)
text = re.sub(r"<[^>]+>", "\n", text)
text = html_mod.unescape(text)
lines = [ln.strip() for ln in text.split("\n") if ln.strip()]

# 找到购物车/订单/支付模块范围
out = []
capture = False
module = ""
for i, ln in enumerate(lines):
    if re.search(r"(购物车|订单|支付)", ln) and ("模块" in ln or ln.endswith("：") or len(ln) < 20):
        module = ln
        capture = True
        out.append("\n===== " + ln + " =====")
        continue
    if re.match(r"^[一二三四五六七八九十]、", ln):
        capture = False
        continue
    if capture:
        out.append(ln)

result = "\n".join(out)
with open(r"C:\Users\翁宇\Desktop\大模型导购的数码电商平台\_交易模块接口提取.txt", "w", encoding="utf-8") as f:
    f.write(result)
print("提取行数:", len(out))
print("总字符:", len(result))
print("---预览前60行---")
print("\n".join(out[:60]))
