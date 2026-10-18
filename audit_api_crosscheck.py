# -*- coding: utf-8 -*-
"""前端 API 调用 vs 后端 Controller 路由交叉核对（空壳扫描 #2）"""
import os, re, json, glob

ROOT = r'C:\Users\郭耀彬\Desktop\gybct\gybxx'
API_DIR = os.path.join(ROOT, 'web', 'src', 'api')
CTRL_DIR = os.path.join(ROOT, 'server', 'src', 'main', 'java', 'com', 'gyb', 'xqfx', 'controller')

def norm(path):
    # 归一化路径模板：${x} / {x} / :x → {p}
    p = re.sub(r'\$\{[^}]+\}', '{p}', path)
    p = re.sub(r'\{[^}]+\}', '{p}', p)
    p = re.sub(r'^/api', '', p)
    return p.rstrip('/') or '/'

# 1) 后端路由
backend = []
for f in glob.glob(os.path.join(CTRL_DIR, '*.java')):
    src = open(f, encoding='utf-8').read()
    cls_m = re.search(r'@RequestMapping\("([^"]+)"\)', src)
    prefix = cls_m.group(1) if cls_m else ''
    for m in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping\("([^"]*)"\)', src):
        method, p = m.group(1).upper(), m.group(2)
        backend.append((method, norm(prefix + p)))

backend_set = set(backend)

# 2) 前端 API
front = []
for f in glob.glob(os.path.join(API_DIR, '*.js')):
    src = open(f, encoding='utf-8').read()
    for fm in re.finditer(r'export function (\w+)', src):
        name = fm.group(1)
        body = src[fm.end():]
        # 找到该函数体（下一个 export function 或文件尾）
        nxt = re.search(r'\nexport function ', body)
        body = body[:nxt.start()] if nxt else body
        for mm in re.finditer(r"request\.(get|post|put|delete|patch)\(\s*['\"`]([^'\"`]+)['\"`]", body):
            method, p = mm.group(1).upper(), mm.group(2)
            front.append({'file': os.path.basename(f), 'fn': name, 'method': method, 'path': p})

# 3) 交叉核对
missing = []
ok = 0
for a in front:
    np_ = norm(a['path'])
    if (a['method'], np_) in backend_set:
        ok += 1
    else:
        # 放宽：GET 可用其他方法替代？严格按方法匹配
        missing.append(a)

lines = []
lines.append(f'后端路由总数: {len(backend_set)}')
lines.append(f'前端 API 调用总数: {len(front)}')
lines.append(f'匹配: {ok}')
lines.append(f'未匹配(疑似空壳): {len(missing)}')
lines.append('')
lines.append('=== 未匹配明细 ===')
for m in missing:
    lines.append(f"{m['file']:20s} {m['fn']:32s} {m['method']:6s} {m['path']}")

used_front = { (a['method'], norm(a['path'])) for a in front }
unused_back = [b for b in backend if b not in used_front]
lines.append(f'')
lines.append(f'=== 后端有但前端未调用: {len(unused_back)} ===')
for m in sorted(unused_back, key=lambda x: x[1]):
    lines.append(f"{m[0]:6s} {m[1]}")

open(os.path.join(ROOT, 'audit_api_result.txt'), 'w', encoding='utf-8').write('\n'.join(lines))
print('written audit_api_result.txt')
