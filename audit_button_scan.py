# -*- coding: utf-8 -*-
"""空壳按钮扫描：template 绑定 handler 是否在 script 中定义"""
import os, re, glob, json

ROOT = r'C:\Users\郭耀彬\Desktop\gybct\gybxx'
VIEWS = os.path.join(ROOT, 'web', 'src', 'views')

def extract_bindings(tpl):
    """@click/@change/@input/@submit/@keyup/@dblclick/@close 绑定及 v-on 绑定"""
    binds = set()
    # @click="fn" / @click="fn()" / @click="fn, other" (事件列表用分号/逗号?)
    for m in re.finditer(r'@(?:click|change|input|submit|keyup|keydown|dblclick|close|confirm|select|blur|focus)\s*=\s*"([^"]+)"', tpl):
        expr = m.group(1)
        # 提取表达式中的函数调用名：排除字符串/条件里的
        for name in re.findall(r'\b([A-Za-z_$][\w$]*)\s*\(', expr):
            binds.add(name)
        # 直接赋值表达式（非调用，如 open=true）
        for name in re.findall(r'@\w+="([A-Za-z_$][\w$]*)"', ' ' + expr):
            binds.add(name)
    # v-on:click 简写
    for m in re.finditer(r'v-on:click\s*=\s*"([^"]+)"', tpl):
        for name in re.findall(r'\b([A-Za-z_$][\w$]*)\s*\(', m.group(1)):
            binds.add(name)
    # :xxx 不扫
    return binds

def extract_defined(script):
    defined = set()
    defined |= set(re.findall(r'function\s+([A-Za-z_$][\w$]*)', script))
    defined |= set(re.findall(r'const\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(?[^;]*=>', script))
    defined |= set(re.findall(r'let\s+([A-Za-z_$][\w$]*)\s*=', script))
    # import 的函数也算可用
    defined |= set(re.findall(r'import\s*\{[^}]*\b([A-Za-z_$][\w$]*)\b[^}]*\}', script))
    defined |= set(re.findall(r'import\s+([A-Za-z_$][\w$]*)\s+from', script))
    return defined

results = []
for f in glob.glob(os.path.join(VIEWS, '**', '*.vue'), recursive=True):
    src = open(f, encoding='utf-8').read()
    tpl_m = re.search(r'<template>(.*)</template>', src, re.S)
    scr_m = re.search(r'<script[^>]*>(.*?)</script>', src, re.S)
    if not tpl_m or not scr_m:
        continue
    tpl, script = tpl_m.group(1), scr_m.group(1)
    binds = extract_bindings(tpl)
    defined = extract_defined(script)
    # 排除 Vue 内置/模板变量/常见全局
    builtin = {'visible', 'loading', 'open', 'close', 'dialog', 'show', 'false', 'true'}
    missing = sorted(b for b in binds if b not in defined and b not in builtin)
    if missing:
        rel = os.path.relpath(f, ROOT)
        results.append((rel, missing))

lines = []
lines.append(f'扫描 .vue 文件: {len(glob.glob(os.path.join(VIEWS, "**", "*.vue"), recursive=True))}')
lines.append(f'发现疑似空壳按钮(绑定未定义handler): {len(results)}')
lines.append('')
for rel, missing in sorted(results):
    lines.append(f'### {rel}')
    lines.append('  未定义: ' + ', '.join(missing))
    lines.append('')

open(os.path.join(ROOT, 'audit_button_result.txt'), 'w', encoding='utf-8').write('\n'.join(lines))
print('written audit_button_result.txt, issues=' + str(len(results)))
