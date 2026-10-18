@echo off
chcp 65001 >nul
title 高校学情预测分析系统 - 一键打开
echo ============================================
echo   高校学情预测分析系统（gybxx）
echo   前端页面 : http://localhost:5173  （登录页含"学生/家长自助注册"入口）
echo   注册页面 : http://localhost:5173/register
echo   后端接口 : http://localhost:8080/api （接口端口，不直接打开页面）
echo ============================================

rem ---- 检测后端 8080 ----
powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if errorlevel 1 (
    echo [警告] 后端未启动，请先运行：cd server ^&^& mvnw.cmd spring-boot:run
) else (
    echo [OK] 后端服务已运行（8080）
)

rem ---- 检测前端 5173 ----
powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if errorlevel 1 (
    echo [警告] 前端未启动，请先运行：cd web ^&^& npm run dev
) else (
    echo [OK] 前端页面已运行（5173）
)

timeout /t 2 /nobreak >nul
start "" "http://localhost:5173/login"
echo 已打开登录页。若看不到"学生/家长自助注册"，请按 Ctrl+F5 强制刷新（清除旧缓存）。
pause
