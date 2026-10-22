# -*- coding: utf-8 -*-
"""静态盘点所有 Controller 接口的角色鉴权覆盖，找出无鉴权/鉴权过宽的接口"""
import os, re, io

CTRL_DIR = r"C:\Users\郭耀彬\Desktop\gybct\gybxx\server\src\main\java\com\gyb\xqfx\controller"

cls_anno = re.compile(r"@RequireRole\s*\(\s*\{(.*?)\}\s*\)", re.S)
cls_anno_single = re.compile(r"@RequireRole\s*\(\s*\"([^\"]+)\"\s*\)")
req_map = re.compile(r"@RequestMapping\s*\(\s*\"([^\"]+)\"")
get_map = re.compile(r"@GetMapping\s*\(\s*\"([^\"]*)\"")
post_map = re.compile(r"@PostMapping\s*\(\s*\"([^\"]*)\"")
put_map = re.compile(r"@PutMapping\s*\(\s*\"([^\"]*)\"")
del_map = re.compile(r"@DeleteMapping\s*\(\s*\"([^\"]*)\"")

def roles_of(block):
    roles = set()
    m = cls_anno.search(block)
    if m:
        roles |= {r.strip().strip('"') for r in m.group(1).split(",")}
    m = cls_anno_single.search(block)
    if m:
        roles.add(m.group(1))
    return roles

rows = []
for fn in sorted(os.listdir(CTRL_DIR)):
    if not fn.endswith(".java"):
        continue
    path = os.path.join(CTRL_DIR, fn)
    text = io.open(path, encoding="utf-8").read()
    # 方法级注解块：方法签名(含上方注解) — 粗切分
    methods = re.finditer(r"@(?:Get|Post|Put|Delete)Mapping\s*\(\s*\"([^\"]*)\"", text)
    # 收集每个 mapping 位置前的最近 @RequireRole（在同方法内）
    for m in methods:
        pos = m.start()
        seg = text[max(0, pos - 400):pos]
        rroles = roles_of(seg)
        mm = "@GetMapping" if get_map.search("@" + "GetMapping(\"" + m.group(1) + "\")") else "?"
        kind = "GET" if "GetMapping" in text[pos-40:pos+10] else ("POST" if "PostMapping" in text[pos-40:pos+10] else ("PUT" if "PutMapping" in text[pos-40:pos+10] else "DELETE"))
        rows.append((fn, kind, m.group(1), sorted(rroles)))

# 类级注解
for fn in sorted(os.listdir(CTRL_DIR)):
    if not fn.endswith(".java"):
        continue
    path = os.path.join(CTRL_DIR, fn)
    text = io.open(path, encoding="utf-8").read()
    cm = req_map.search(text)
    base = cm.group(1) if cm else "/"
    cls_roles = roles_of(text[:text.find("public class")] if "public class" in text else text)
    print(f"== {fn} (base={base}, 类级角色={sorted(cls_roles)}) ==")
    for fn2, kind, path2, rroles in rows:
        if fn2 == fn:
            mark = "" if rroles else ("  <<< 无方法级+类级角色限制(公开/待核) >>>" if not cls_roles else "")
            print(f"  {kind:6s} {base}{path2}  角色={rroles or '(类级)'}{mark}")
