#!/bin/bash
# 部署脚本 - 部署到阿里云服务器

set -e

SERVER="root@8.137.106.8"
REMOTE_DIR="/root/gobang"

echo "======================================"
echo "  部署到阿里云服务器"
echo "======================================"
echo ""

# 1. 构建项目
echo "📦 构建项目..."
./scripts/build.sh

# 2. 上传后端
echo ""
echo "📤 上传后端..."
scp backend/target/gobang-server-1.0.0.jar $SERVER:$REMOTE_DIR/target/

# 3. 上传前端
echo "📤 上传前端..."
cd frontend
npm run build:prod
cd ..
scp -r frontend/dist/* $SERVER:$REMOTE_DIR/frontend/

# 4. 重启服务
echo ""
echo "🔄 重启服务..."
ssh $SERVER << 'ENDSSH'
cd /root/gobang
pkill -f gobang-server
sleep 2
nohup java -jar target/gobang-server-1.0.0.jar > server.log 2>&1 &
systemctl restart nginx
ENDSSH

echo ""
echo "✅ 部署完成！"
echo "访问: http://8.137.106.8"
