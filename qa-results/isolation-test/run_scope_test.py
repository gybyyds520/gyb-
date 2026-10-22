# -*- coding: utf-8 -*-
"""隔离专项检查 v3：数据范围对比（admin vs 普通教师 T004；学生/家长仅见自己）"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
LOGIN = BASE + "/api/auth/login"

ACCOUNTS = {
    "ADMIN":   ("admin", "admin123", "TEACHER"),
    "TEACHER": ("T004", "123456", "TEACHER"),
    "STUDENT": ("2300000001", "123456", "STUDENT"),
    "PARENT":  ("2300000001P1", "123456", "PARENT"),
}


def login(u, p, lt):
    data = json.dumps({"username": u, "password": p, "loginType": lt}).encode()
    req = urllib.request.Request(LOGIN, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode()).get("data", {}).get("accessToken")


def get_json(path, token):
    req = urllib.request.Request(BASE + path)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


def extract_data(j):
    return j.get("data") if isinstance(j, dict) else j


def main():
    tokens = {r: login(*a) for r, a in ACCOUNTS.items()}
    print("登录全部成功\n")

    # 1) 学生列表总量：admin vs T004（普通教师应被数据范围过滤）
    for role in ("ADMIN", "TEACHER"):
        j = get_json("/api/student/page?page=1&size=5", tokens[role])
        d = extract_data(j)
        print(f"学生列表[{role}] total={d.get('total')}  pageSize={d.get('pageSize')}")
    print()

    # 2) 预警列表总量：admin vs T004
    for role in ("ADMIN", "TEACHER"):
        j = get_json("/api/warning/page?current=1&size=5", tokens[role])
        d = extract_data(j)
        print(f"预警列表[{role}] total={d.get('total')}")
    print()

    # 3) 学情总览学生数：admin vs T004
    for role in ("ADMIN", "TEACHER"):
        j = get_json("/api/dashboard/overview", tokens[role])
        d = extract_data(j)
        print(f"学情总览[{role}] studentCount={d.get('studentCount')} clazzCount={d.get('clazzCount')}")
    print()

    # 4) 学生门户：仅返回自己的 studentId
    j = get_json("/api/portal/overview", tokens["STUDENT"])
    d = extract_data(j)
    print(f"学生门户[STUDENT] studentId={d.get('studentId')} studentNo={d.get('studentNo')} name={d.get('studentName', d.get('name'))}")
    scores = extract_data(get_json("/api/portal/scores", tokens["STUDENT"]))
    print(f"学生成绩记录数={len(scores) if isinstance(scores, list) else '?'}")
    print()

    # 5) 家长：子女列表仅绑定孩子
    j = get_json("/api/parent/children", tokens["PARENT"])
    d = extract_data(j)
    kids = d if isinstance(d, list) else d.get("children", d.get("list", []))
    print(f"家长子女列表={kids if isinstance(kids, list) else str(kids)[:200]}")
    # 家长访问非绑定子女 children/2 -> 应拒绝
    try:
        j2 = get_json("/api/parent/children/2", tokens["PARENT"])
        print(f"家长访问非绑定子女(2) -> 放行(code={j2.get('code')}, data={str(j2.get('data'))[:100]}) [隔离问题!]")
    except urllib.error.HTTPError as e:
        print(f"家长访问非绑定子女(2) -> HTTP{e.code} [已拦截]")
    print()

    # 6) 学生尝试访问他人明细 student/2 -> 应拒绝
    try:
        j3 = get_json("/api/student/2", tokens["STUDENT"])
        print(f"学生访问他人明细(2) -> code={j3.get('code')}, msg={j3.get('message')} [{'隔离问题!' if j3.get('code')==200 else '已拦截'}]")
    except urllib.error.HTTPError as e:
        print(f"学生访问他人明细(2) -> HTTP{e.code} [已拦截]")
    try:
        j4 = get_json("/api/monitor/student/1", tokens["STUDENT"])
        print(f"学生访问监察中心 -> code={j4.get('code')}, msg={j4.get('message')} [{'隔离问题!' if j4.get('code')==200 else '已拦截'}]")
    except urllib.error.HTTPError as e:
        print(f"学生访问监察中心 -> HTTP{e.code} [已拦截]")


if __name__ == "__main__":
    main()
