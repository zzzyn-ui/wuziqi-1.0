#!/bin/bash
# 开发启动脚本 - 同时启动前后端开发服务器

set -e

echo "======================================"
echo "  启动开发环境"
echo "======================================"
echo ""

# 检查是否在项目根目录
if [ ! -d "backend" ] || [ ! -d "frontend" ]; then
    echo "❌ 错误: 请在项目根目录运行此脚本"
    exit 1
fi

# 启动后端（后台）
echo "🚀 启动后端..."
cd backend
mvn spring-boot:run &
BACKEND_PID=$!
echo "后端 PID: $BACKEND_PID"
cd ..

# 等待后端启动
sleep 5

# 启动前端（后台）
echo "🚀 启动前端..."
cd frontend
npm run dev &
FRONTEND_PID=$!
echo "前端 PID: $FRONTEND_PID"
cd ..

echo ""
echo "✅ 开发环境已启动！"
echo ""
echo "后端: http://localhost:8080"
echo "前端: http://localhost:5173"
echo ""
echo "按 Ctrl+C 停止所有服务"

# 等待用户中断
trap "echo ''; echo '停止服务...'; kill $BACKEND_PID $FRONTEND_PID; exit 0" INT

wait
