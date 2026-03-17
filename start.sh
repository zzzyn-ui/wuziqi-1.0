#!/bin/bash

# 五子棋服务器快速启动脚本 (Linux/Mac)

set -e

echo "========================================"
echo "  五子棋服务器启动脚本"
echo "========================================"

# 加载 .env 文件（如果存在）
if [ -f ".env" ]; then
    print_info "加载环境变量从 .env 文件..."
    set -a
    source .env
    set +a
fi

# 检查必需的环境变量
check_required_env_vars() {
    if [ -z "$JWT_SECRET" ]; then
        print_warning "JWT_SECRET 环境变量未设置"
        print_warning "生产环境必须设置此变量！"
        print_warning "开发环境将使用配置文件中的默认值"
        echo ""
        read -p "是否继续? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        print_info "JWT_SECRET 环境变量已设置 ✓"
    fi
}

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查命令是否存在
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# 打印信息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 检查依赖
check_dependencies() {
    print_info "检查依赖..."

    if ! command_exists java; then
        print_error "未找到Java，请安装JDK 17或更高版本"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        print_error "Java版本过低，需要JDK 17或更高版本"
        exit 1
    fi

    if ! command_exists mvn; then
        print_error "未找到Maven，请安装Maven 3.6或更高版本"
        exit 1
    fi

    print_info "依赖检查通过 ✓"
}

# 检查MySQL
check_mysql() {
    print_info "检查MySQL..."

    if command_exists mysql; then
        if mysql -u root -e "SELECT 1" >/dev/null 2>&1; then
            print_info "MySQL已运行 ✓"
            return 0
        fi
    fi

    print_warning "MySQL未运行或未安装"
    read -p "是否继续启动服务器? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
}

# 检查Redis
check_redis() {
    print_info "检查Redis..."

    if command_exists redis-cli; then
        if redis-cli ping >/dev/null 2>&1; then
            print_info "Redis已运行 ✓"
            return 0
        fi
    fi

    print_warning "Redis未运行或未安装"
    read -p "是否继续启动服务器? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
}

# 初始化数据库
init_database() {
    print_info "初始化数据库..."

    if [ -f "src/main/resources/schema.sql" ]; then
        read -p "是否需要初始化数据库? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            read -p "MySQL用户名 [root]: " mysql_user
            mysql_user=${mysql_user:-root}
            mysql -u "$mysql_user" -p < src/main/resources/schema.sql
            print_info "数据库初始化完成 ✓"
        fi
    fi
}

# 编译项目
build_project() {
    print_info "编译项目..."

    if [ ! -d "target" ] || [ "pom.xml" -nt "target/gobang-server-*.jar" ]; then
        mvn clean package -DskipTests
        print_info "编译完成 ✓"
    else
        print_info "项目已编译，跳过"
    fi
}

# 启动服务器
start_server() {
    print_info "启动五子棋服务器..."

    # 查找jar包
    JAR_FILE=$(find target -name "gobang-server-*.jar" -not -name "*sources.jar" | head -n 1)

    if [ -z "$JAR_FILE" ]; then
        print_error "未找到编译后的jar文件"
        exit 1
    fi

    print_info "使用jar文件: $JAR_FILE"

    # 设置JVM参数
    JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"

    print_info "JVM参数: $JAVA_OPTS"

    echo ""
    echo "========================================"
    echo "  服务器正在启动..."
    echo "  WebSocket: ws://localhost:9090/ws"
    echo "  测试页面: 打开 test-client.html"
    echo "========================================"
    echo ""

    # 启动服务器
    java $JAVA_OPTS -jar "$JAR_FILE"
}

# 主函数
main() {
    check_dependencies
    check_required_env_vars
    check_mysql
    check_redis
    init_database
    build_project
    start_server
}

# 捕获Ctrl+C
trap 'print_info "正在停止..."; exit 0' INT

# 运行主函数
main
