# -*- coding: utf-8 -*-
"""隔离专项检查 v2：4 角色 x 关键接口交叉越权测试（只读）。
判定：HTTP200+Result.code==200 => 放行；HTTP200+code!=200 => 业务拒绝；401/403 => HTTP拒绝"""
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

# (名称, 方法, 路径, 期望放行角色, 备注)
CASES = [
    ("监察中心概览",      "GET", "/api/monitor/overview",          {"ADMIN"}, "仅管理员"),
    ("监察-学生全景",     "GET", "/api/monitor/student/1",         {"ADMIN"}, "仅管理员"),
    ("系统账号-demo",     "GET", "/api/sys/accounts/demo",         {"ADMIN"}, "仅管理员"),
    ("导出成绩",          "GET", "/api/export/scores?format=json", {"ADMIN", "TEACHER"}, "教师系可导出"),
    ("预警工单导出",      "GET", "/api/export/tickets?format=json", {"ADMIN", "TEACHER"}, "教师系可导出"),
    ("学生门户概览",      "GET", "/api/portal/overview",           {"STUDENT"}, "仅学生"),
    ("学生门户成绩",      "GET", "/api/portal/scores",             {"STUDENT"}, "仅学生"),
    ("家长-子女列表",     "GET", "/api/parent/children",           {"PARENT"}, "仅家长"),
    ("家长-绑定子女详情", "GET", "/api/parent/children/1",         {"PARENT"}, "仅家长+绑定"),
    ("家长-非绑定子女",   "GET", "/api/parent/children/2",         set(),      "家长访问非绑定子女应被拒"),
    ("家长-AI报告(绑定)", "GET", "/api/parent/ai-report?studentId=1", {"PARENT"}, "仅家长+VIP"),
    ("学生列表",          "GET", "/api/student/page?page=1&size=5", {"ADMIN", "TEACHER"}, "教师系"),
    ("学生明细(id=2)",    "GET", "/api/student/2",                 {"ADMIN", "TEACHER"}, "学生不可看他人"),
    ("学情画像(学生1)",   "GET", "/api/profile/student?studentId=1", {"ADMIN", "TEACHER"}, "教师系"),
    ("干预工单列表",      "GET", "/api/intervention/tickets",      {"ADMIN", "TEACHER"}, "教师系"),
    ("学情总览",          "GET", "/api/dashboard/overview",        {"ADMIN", "TEACHER"}, "教师系"),
    ("预警列表",          "GET", "/api/warning/page?current=1&size=5", {"ADMIN", "TEACHER"}, "教师系"),
    ("预警时间线(学生1)", "GET", "/api/warning/timeline?studentId=1", {"ADMIN", "TEACHER"}, "数据范围校验"),
]


def call(method, path, token):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = b""
            while True:
                chunk = r.read(65536)
                if not chunk:
                    break
                raw += chunk
            head = raw[:4]
            # 非 JSON（导出 xlsx 等文件流）=> 放行
            if head.startswith(b"PK") or head.startswith(b"\x89PNG") or head.startswith(b"%PDF"):
                return "ALLOW"
            text = raw.decode("utf-8", "replace")
            try:
                j = json.loads(text)
                if j.get("code") == 200:
                    return "ALLOW"
                return f"BIZ_REJECT:{j.get('message','')}"
            except Exception:
                return f"HTTP200-nonjson:{(text or '')[:80]}"
    except urllib.error.HTTPError as e:
        return f"HTTP{e.code}"
    except Exception as e:
        return f"ERR:{str(e)[:80]}"


def login(username, password, login_type):
    data = json.dumps({"username": username, "password": password, "loginType": login_type}).encode("utf-8")
    req = urllib.request.Request(LOGIN, data=data, method="POST", headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            d = json.loads(r.read().decode("utf-8"))
            return d.get("data", {}).get("accessToken"), d.get("code"), d.get("message")
    except urllib.error.HTTPError as e:
        return None, e.code, e.read(120).decode("utf-8", "replace")
    except Exception as e:
        return None, -1, str(e)[:120]


def is_reject(r):
    return r.startswith("BIZ_REJECT") or r.startswith("HTTP") or r.startswith("ERR") or r.startswith("HTTP200")


def main():
    tokens = {}
    print("== 登录 ==")
    for role, (u, p, lt) in ACCOUNTS.items():
        tok, code, msg = login(u, p, lt)
        tokens[role] = tok
        print(f"{role:8s} {u:14s} -> {'OK' if tok else f'FAIL({code}:{msg})'}")
    print("\n== 交叉测试 ==")
    print(f"{'接口':<22}{'ADMIN':<16}{'TEACHER':<16}{'STUDENT':<16}{'PARENT':<16}预期放行")
    results = []
    issues = []
    for name, method, path, allowed, note in CASES:
        row = [name]
        for role in ("ADMIN", "TEACHER", "STUDENT", "PARENT"):
            r = call(method, path, tokens.get(role))
            expect_allow = role in allowed
            if expect_allow:
                v = "PASS" if r == "ALLOW" else f"FAIL应放行<{r}>"
            else:
                v = "PASS" if is_reject(r) else f"FAIL越权放行<{r}>"
            row.append(f"{v}")
            if v.startswith("FAIL"):
                issues.append(f"{name}[{role}] {v}")
        print(f"{name:<22}" + "".join(f" {x:<16}" for x in row[1:]) + f"  ({note})")
        results.append({"name": name, "path": path, "allowed": sorted(allowed), "row": row, "note": note})

    with open(r"C:\Users\郭耀彬\Desktop\gybct\gybxx\qa-results\isolation-test\results.json", "w", encoding="utf-8") as f:
        json.dump({"cases": results, "issues": issues}, f, ensure_ascii=False, indent=2)
    print(f"\n== 发现问题: {len(issues)} 条 ==")
    for i in issues:
        print(" -", i)


if __name__ == "__main__":
    main()
