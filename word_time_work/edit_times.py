# -*- coding: utf-8 -*-
"""按 run 层修改两份 DOCX 的时间（保留原格式），原地写回 TARGET_DOCX"""
from docx import Document

BASE = r'C:\Users\郭耀彬\Desktop\gybct\gybxx'

def replace_in_paragraphs(doc, matcher):
    """matcher(para_text)-> None 或 {old_run_text: new_text} 映射，仅替换匹配段落内对应 run"""
    n = 0
    for p in doc.paragraphs:
        n += _apply(p, matcher)
    for table in doc.tables:
        for row in table.rows:
            for cell in row.cells:
                for p in cell.paragraphs:
                    n += _apply(p, matcher)
    return n

def _apply(p, matcher):
    m = matcher(p.text)
    if not m:
        return 0
    cnt = 0
    for r in p.runs:
        if r.text in m:
            r.text = m[r.text]
            cnt += 1
    return cnt

# ---- 题目审核表 ----
def sh_matcher(text):
    if text.strip() == '2026年8月13日':
        return {'8': '9', '13': '16'}
    if text.startswith('负责人签字：') and '2026年 8月15日' in text:
        return {'8': '9', '15': '18'}
    if text.startswith('负责人签字（盖章）：') and '2026年 8月' in text:
        return {' 8': ' 9'}
    return None

# ---- 开题报告 ----
def kt_matcher(text):
    if text.strip() == '2026年10月11日':
        return {'11': '23'}
    if text.strip() == '2026年 10月 11日':
        return {'11': '23'}
    return None

for path, matcher, label in [
    (BASE + r'\2301132013-郭耀彬-题目审核表.docx', sh_matcher, '题目审核表'),
    (BASE + r'\2301132013-郭耀彬-开题报告.docx', kt_matcher, '开题报告'),
]:
    doc = Document(path)
    cnt = replace_in_paragraphs(doc, matcher)
    doc.save(path)
    print(f'{label}: 修改 {cnt} 处 run')
