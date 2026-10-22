# -*- coding: utf-8 -*-
"""隔离检测·正确参数重测：区分'参数缺失假500'与'真实越权'"""
import json, urllib.request, urllib.error

BASE = "http://localhost:8080"

def login(u, p, lt):
    data = json.dumps({"username": u, "password": p, "loginType": lt}).encode()
    req = urllib.request.Request(BASE + "/api/auth/login", data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode()).get("data", {}).get("accessToken")

def call(method, path, token, body=None, form=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, method=method, data=data)
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("Content-Type", "application/x-www-form-urlencoded" if form else "application/json")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read(1200)
            try:
                j = json.loads(raw.decode("utf-8", "replace"))
                return f"code={j.get('code')} msg={j.get('message','')[:50]}"
            except Exception:
                return f"HTTP200:{raw[:50]!r}"
    except urllib.error.HTTPError as e:
        return f"HTTP{e.code} {e.read(120).decode('utf-8','replace')[:60]}"

STU = login("2300000001", "123456", "STUDENT")
PAR = login("2300000001P1", "123456", "PARENT")

CASES = [
    # (method, path, body/form, 说明)
    ("GET", "/api/profile/student?studentId=1", None, "学生查画像(教师系)"),
    ("POST", "/api/profile/generate?studentId=1&termId=1", None, "学生生成画像(仅管理员)"),
    ("POST", "/api/checkin?offeringId=1&title=t", None, "学生发起签到(教师系)"),
    ("POST", "/api/intervention/tickets?warningId=1", None, "学生创建干预工单(教师系)"),
    ("POST", "/api/exam/papers?title=xx&courseId=1", None, "学生生成试卷(教师系)"),
    ("GET", "/api/performance/summary?studentId=1", None, "学生查表现汇总(?)"),
    ("GET", "/api/performance/ranking", None, "学生查排名(?)"),
    ("DELETE", "/api/sys/configs/zzz_no_such_key", None, "学生删系统配置(仅管理员)"),
    ("GET", "/api/scan/list", None, "学生查试卷录入列表(教师系)"),
    ("GET", "/api/paper-scan/list", None, "学生查paper-scan(路径核对)"),
    ("POST", "/api/message/send", {"toUserId": 2, "content": "隔离测试"}, "学生发站内信(全员?)"),
    ("GET", "/api/message/contacts", None, "学生看联系人(?)"),
    ("GET", "/api/vip/orders", None, "学生看VIP订单(本人?)"),
]

for method, path, body, note in CASES:
    for who, tok in (("学生", STU), ("家长", PAR)):
        r = call(method, path, tok, body)
        blocked = r.startswith("HTTP401") or r.startswith("HTTP403") or r.startswith("code=403") or r.startswith("code=401")
        flag = "✅拦截" if blocked else ("⚠️放行" if (r.startswith("code=200") or r.startswith("HTTP200")) else "❓异常")
        print(f"{flag} {who} {method:6s} {path:<52} -> {r}  ({note})")
    print("-" * 100)
