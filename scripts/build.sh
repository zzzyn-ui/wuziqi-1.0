#!/bin/bash
# 构建脚本 - 编译前后端

set -e

echo "======================================"
echo "  构建前后端项目"
echo "======================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. 构建后端
echo -e "${YELLOW}📦 步骤 1: 构建后端...${NC}"
cd backend
mvn clean package -DskipTests
echo -e "${GREEN}✅ 后端构建完成${NC}"
echo ""

# 2. 构建前端
echo -e "${YELLOW}📦 步骤 2: 构建前端...${NC}"
cd ../frontend
npm run build:prod
echo -e "${GREEN}✅ 前端构建完成${NC}"
echo ""

echo -e "${GREEN}======================================"
echo "  ✅ 构建完成！"
echo "======================================"
echo ""
echo "后端 jar: backend/target/gobang-server-1.0.0.jar"
echo "前端 dist: frontend/dist/"
