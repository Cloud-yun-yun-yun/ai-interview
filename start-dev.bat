@echo off
chcp 65001 >nul
echo ============================================
echo   AI-Interview 一键启动
echo   后端: mvnw spring-boot:run (backend)
echo   前端: npm run dev         (frontend)
echo ============================================
echo.

echo [1/2] 启动后端服务 ...
start "ai-interview-backend" cmd /k "cd /d %~dp0backend && mvnw.cmd spring-boot:run"

echo [2/2] 启动前端服务 ...
start "ai-interview-frontend" cmd /k "cd /d %~dp0frontend && npm.cmd run dev"

echo.
echo 前后端已启动，请在新弹出的两个窗口中查看各自的日志。
echo 前端默认地址: http://localhost:5173/
echo 按任意键关闭此窗口（不影响已开启的前后端进程）。
pause >nul