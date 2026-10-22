# -*- coding: utf-8 -*-
"""隔离检测扩展：学生/家长 token 访问静态扫描出的'无角色注解'接口。
写接口使用必然无效载荷（不存在的id/空body），只验证'是否被鉴权拦截'，不写入真实数据。"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"


def login(u, p, lt):
    data = json.dumps({"username": u, "password": p, "loginType": lt}).encode()
    req = urllib.request.Request(BASE + "/api/auth/login", data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode()).get("data", {}).get("accessToken")


def call(method, path, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, method=method, data=data)
    req.add_header("Authorization", "Bearer " + token)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read(1200)
            if raw[:4] in (b"PK\x03", b"\x89PN"):
                return "FILE200"
            try:
                j = json.loads(raw.decode("utf-8", "replace"))
                return f"code={j.get('code')} msg={j.get('message','')}"
            except Exception:
                return f"HTTP200:{raw[:60]!r}"
    except urllib.error.HTTPError as e:
        return f"HTTP{e.code} {e.read(120).decode('utf-8','replace')[:80]}"


STU = login("2300000001", "123456", "STUDENT")
PAR = login("2300000001P1", "123456", "PARENT")
print("登录 OK\n")

CASES = [
    ("GET", "/api/sys/users?page=1&size=5", None, "系统用户列表(仅管理员)"),
    ("GET", "/api/sys/roles", None, "角色列表(仅管理员)"),
    ("GET", "/api/sys/configs", None, "系统配置(仅管理员)"),
    ("GET", "/api/sys/data-scope/2", None, "数据范围(仅管理员)"),
    ("GET", "/api/sys/audit-logs?page=1&size=3", None, "审计日志(仅管理员)"),
    ("GET", "/api/vip/levels", None, "VIP等级(公开?)"),
    ("GET", "/api/vip/orders", None, "VIP订单(仅本人)"),
    ("GET", "/api/todo/list", None, "待办(本人)"),
    ("GET", "/api/notify/list?page=1&size=3", None, "通知(本人)"),
    ("GET", "/api/mistake/my?page=1&size=3", None, "错题本(本人)"),
    ("GET", "/api/performance/summary", None, "表现总览"),
    ("GET", "/api/ml/dataset-summary", None, "数据集摘要"),
    ("GET", "/api/exam/papers", None, "试卷列表"),
    ("POST", "/api/message/send", {"toUserId": 1, "content": "x"}, "站内信发送"),
    ("POST", "/api/intervention/tickets", {"studentId": 1, "title": "x"}, "干预建单(教师系)"),
    ("POST", "/api/warning/rules", {"name": "x", "threshold": 1}, "预警规则新建(管理员)"),
    ("POST", "/api/exam/papers", {"title": "x"}, "生成试卷(教师系)"),
    ("POST", "/api/profile/generate", {}, "画像生成(教师系)"),
    ("POST", "/api/checkin", {"offeringId": 1}, "发起签到(教师系)"),
    ("DELETE", "/api/sys/users/999", None, "删除用户(仅管理员,id不存在)"),
    ("DELETE", "/api/sys/roles/999", None, "删除角色(仅管理员)"),
    ("DELETE", "/api/sys/configs/__nokey__", None, "删除配置(仅管理员)"),
    ("POST", "/api/demo/generate", {"force": False}, "生成演示数据(管理员)"),
    ("GET", "/api/academic/indicator?studentId=1", None, "学业指标"),
    ("GET", "/api/academic/batch", None, "学业指标批量"),
    ("GET", "/api/base/college/list", None, "基础字典college"),
    ("GET", "/api/base/major/list", None, "基础字典major"),
    ("GET", "/api/base/clazz/list", None, "基础字典clazz"),
    ("POST", "/api/homework/999/submit", {}, "作业提交(学生)"),
    ("GET", "/api/paper-scan/list", None, "试卷录入列表"),
]

print(f"{'接口':<42}{'学生':<34}{'家长':<34}预期")
print("-" * 115)
issues = []
for method, path, body, note in CASES:
    rs = call(method, path, STU, body)
    rp = call(method, path, PAR, body)
    # 判定：code=200 或 FILE200 或 HTTP200 = 越权放行(漏洞)；HTTP401/403 或 code=403 = 拦截(安全)
    def verdict(r):
        return r.startswith("HTTP401") or r.startswith("HTTP403") or r.startswith("code=403")
    vs = "PASS(拦截)" if verdict(rs) else f"⚠️放行<{rs}>"
    vp = "PASS(拦截)" if verdict(rp) else f"⚠️放行<{rp}>"
    line = f"{path:<42}{vs:<34}{vp:<34}({note})"
    print(line)
    if not verdict(rs) or not verdict(rp):
        issues.append(f"{path} [{note}] 学生={vs} 家长={vp}")

print("\n== 疑似越权放行项 ==")
for i in issues:
    print(" -", i)
if not issues:
    print(" 无")
