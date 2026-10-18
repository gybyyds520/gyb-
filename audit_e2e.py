# -*- coding: utf-8 -*-
"""关键功能真实调用验证（8081 新代码实例），结果写文件"""
import json, urllib.request, urllib.error, urllib.parse

BASE = 'http://127.0.0.1:8081/api'
OUT = []

def call(method, path, token=None, body=None):
    url = BASE + path
    data = None
    headers = {'Content-Type': 'application/json; charset=utf-8'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        return e.code, (e.read().decode('utf-8', 'ignore')[:400])

def login(u, p, t):
    code, r = call('POST', '/auth/login', body={'username': u, 'password': p, 'loginType': t})
    if code == 200 and r.get('data') and r['data'].get('accessToken'):
        return r['data']['accessToken']
    OUT.append(f'登录失败 {u}: {code} {r}')
    return None

tT = login('T004', '123456', 'TEACHER')
tS = login('2300000001', '123456', 'STUDENT')
tP = login('2300000001P1', '123456', 'PARENT')

# 1. 教师发布章节测验（offeringId=4, clazzId=null）
if tT:
    body = {"title": "摸排真伪验证测验-8081", "courseId": 1, "courseName": "PC",
            "courseOfferingId": 4, "clazzId": None,
            "questions": [{"no": "1", "type": "CHOICE", "content": "1+1=?", "answer": "B", "score": 10},
                          {"no": "2", "type": "CHOICE", "content": "2+2=?", "answer": "D", "score": 10}],
            "deadline": None}
    code, r = call('POST', '/homework', tT, body)
    hw_id = r.get('data') if isinstance(r, dict) else None
    OUT.append(f'1.发布测验 code={code} hwId={hw_id}')
    if code == 200 and hw_id:
        # 2. 学生可见
        code, r = call('GET', '/homework/offering/mine', tS)
        hit = [x for x in (r.get('data') or []) if x.get('id') == hw_id]
        OUT.append(f'2.学生测验列表 code={code} 条数={len(r.get("data") or [])} 新测验可见={bool(hit)}')
        # 3. 学生提交（全对 → 20 分）；业务失败时全局异常以 HTTP200+业务code 返回
        code, r = call('POST', f'/homework/{hw_id}/submit', tS,
                       {'answers': [{'no': '1', 'answer': 'B'}, {'no': '2', 'answer': 'D'}]})
        biz = r.get('code') if isinstance(r, dict) else None
        OUT.append(f'3.学生提交 http={code} biz={biz} msg={r.get("message") if isinstance(r, dict) else r}')
        # 4. 教师统计
        code, r = call('GET', '/homework/offering/4', tT)
        hit = [x for x in (r.get('data') or []) if x.get('id') == hw_id]
        if hit:
            OUT.append(f'4.教师统计 submitted={hit[0].get("submittedCount")} avg={hit[0].get("avgScore")}')
        else:
            OUT.append('4.教师统计 未找到新测验')

# 5. 学生错题本（应有收录或为空均可，验证接口真实返回）
if tS:
    code, r = call('GET', '/mistake/my/stats', tS)
    d = r.get('data') if isinstance(r, dict) else None
    OUT.append(f'5.学生错题统计 code={code} total={d.get("total") if d else None}')

# 6. 家长作业动态
if tP:
    code, r = call('GET', '/homework/parent', tP)
    OUT.append(f'6.家长作业动态 code={code} 条数={len(r.get("data") or []) if isinstance(r.get("data"), list) else r.get("data")}')

# 7. AI 学科答疑（本地 Ollama）
if tS:
    q = urllib.parse.quote('1+1等于几？请解释')
    s = urllib.parse.quote('数学')
    code, r = call('POST', f'/ai/tutor?question={q}&subject={s}', tS)
    d = r.get('data') if isinstance(r, dict) else None
    content = (d or {}).get('content') or ''
    OUT.append(f'7.AI答疑 code={code} 返回长度={len(content)} 模型={((d or {}).get("model"))} 内容前缀={content[:30]}')

# 8. AI 健康检查（带学生 token）
if tS:
    code, r = call('GET', '/ai/health', tS)
    OUT.append(f'8.AI健康检查 code={code} 可用={r.get("data") if isinstance(r, dict) else r}')
else:
    OUT.append('8.AI健康检查 无token跳过')

open(r'C:\Users\郭耀彬\Desktop\gybct\gybxx\audit_e2e_result.txt', 'w', encoding='utf-8').write('\n'.join(OUT))
print('written audit_e2e_result.txt')
