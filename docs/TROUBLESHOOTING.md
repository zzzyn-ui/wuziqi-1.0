# 故障排查指南

本文档帮助你诊断和解决五子棋服务器常见问题。

---

## 目录

1. [启动问题](#启动问题)
2. [数据库问题](#数据库问题)
3. [Redis 问题](#redis-问题)
4. [网络连接问题](#网络连接问题)
5. [游戏功能问题](#游戏功能问题)
6. [性能问题](#性能问题)
7. [部署问题](#部署问题)

---

## 启动问题

### 问题：服务无法启动

**症状**: 执行启动命令后服务立即退出

**可能原因**:

1. **JDK 版本不匹配**
```bash
# 检查 JDK 版本
java -version
# 应显示: java version "17.x.x"
```

**解决方案**: 安装 JDK 17 或更高版本

2. **端口被占用**
```bash
# Windows
netstat -ano | findstr 8083

# Linux/Mac
lsof -i:8083
```

**解决方案**:
- 关闭占用端口的程序
- 或修改 `application.yml` 中的端口号

3. **配置文件错误**
```bash
# 检查配置文件语法
cat backend/src/main/resources/application.yml
```

**解决方案**: 检查 YAML 语法是否正确，注意缩进

4. **内存不足**
```
Error: Java heap space
```

**解决方案**: 增加 JVM 内存
```bash
java -Xmx1g -Xms512m -jar gobang-server.jar
```

---

### 问题：启动后立即崩溃

**症状**: 服务启动几秒后自动退出

**诊断步骤**:

1. **查看日志**
```bash
# 查看最新日志
tail -f logs/gobang-server.log

# 或查看系统日志
journalctl -u gobang -n 50
```

2. **常见错误**

| 错误信息 | 原因 | 解决方案 |
|---------|------|----------|
| `ClassNotFoundException` | 依赖缺失 | 执行 `mvn clean package` |
| `NoClassDefFoundError` | 类路径问题 | 检查 JAR 包完整性 |
| `OutOfMemoryError` | 内存不足 | 增加 JVM 内存 |

---

## 数据库问题

### 问题：数据库连接失败

**症状**: 日志显示 `Could not connect to database`

**诊断步骤**:

1. **检查 MySQL 服务**
```bash
# Windows
sc query MySQL

# Linux
systemctl status mysql
# 或
service mysql status
```

2. **测试连接**
```bash
mysql -u username -p -h localhost
```

3. **检查配置**
```yaml
database:
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC
  username: your_username
  password: your_password
```

**解决方案**:
- 确保 MySQL 服务正在运行
- 验证用户名和密码
- 确认数据库 `gobang` 已创建
- 检查防火墙设置

---

### 问题：表不存在

**症状**: `Table 'gobang.xxx' doesn't exist`

**解决方案**:

1. **导入表结构**
```bash
mysql -u root -p gobang < backend/src/main/resources/schema.sql
```

2. **验证表已创建**
```sql
USE gobang;
SHOW TABLES;
```

---

### 问题：连接池耗尽

**症状**: `Connection pool exhausted`

**诊断**:
```bash
# 查看当前连接数
mysql> SHOW PROCESSLIST;
```

**解决方案**:

1. **增加连接池大小**
```yaml
database:
  pool-size: 20  # 增加到 20
```

2. **检查连接泄露**
- 确保所有数据库操作正确关闭连接
- 使用 try-with-resources

---

## Redis 问题

### 问题：Redis 连接失败

**症状**: `Unable to connect to Redis`

**诊断步骤**:

1. **检查 Redis 服务**
```bash
# Windows
redis-cli ping

# Linux
systemctl status redis
# 或
service redis status
```

2. **测试连接**
```bash
redis-cli -h localhost -p 6379 ping
# 应返回: PONG
```

3. **检查配置**
```yaml
redis:
  host: localhost
  port: 6379
  password: ""  # 如有密码需填写
```

**解决方案**:
- 启动 Redis 服务
- 验证端口和密码配置
- 检查 Redis 绑定地址

---

### 问题：Redis 内存不足

**症状**: `OOM command not allowed when used memory`

**解决方案**:

1. **查看内存使用**
```bash
redis-cli INFO memory
```

2. **设置最大内存**
```bash
# 编辑 redis.conf
maxmemory 1gb
maxmemory-policy allkeys-lru
```

3. **清理过期数据**
```bash
redis-cli FLUSHDB  # 清空当前数据库（慎用）
```

---

## 网络连接问题

### 问题：WebSocket 连接失败

**症状**: 前端无法建立 WebSocket 连接

**诊断步骤**:

1. **检查服务器状态**
```bash
curl http://localhost:8083/api/health
```

2. **检查防火墙**
```bash
# Windows
netsh advfirewall firewall show rule name=all

# Linux
sudo ufw status
```

3. **浏览器控制台**
```
按 F12 打开开发者工具 → Console 标签
查看错误信息
```

**解决方案**:
- 确保服务器正在运行
- 检查防火墙允许 8083 端口
- 验证 WebSocket URL 正确
- 检查代理设置

---

### 问题：跨域错误

**症状**: `CORS policy: No 'Access-Control-Allow-Origin' header`

**解决方案**:

1. **开发环境**: 使用代理
2. **生产环境**: 配置 Nginx
```nginx
location /api {
    add_header Access-Control-Allow-Origin *;
    add_header Access-Control-Allow-Methods 'GET, POST, OPTIONS';
    add_header Access-Control-Allow-Headers 'Authorization, Content-Type';
}
```

---

## 游戏功能问题

### 问题：匹配无响应

**症状**: 点击匹配后一直等待

**诊断步骤**:

1. **检查在线人数**
```bash
curl http://localhost:8083/api/stats
```

2. **查看日志**
```bash
tail -f logs/gobang-server.log | grep Match
```

**可能原因**:
- 在线人数少
- 积分差距大
- 匹配队列异常

**解决方案**:
- 邀请好友对战
- 尝试创建房间
- 重启服务器

---

### 问题：游戏中断

**症状**: 游戏中突然断开连接

**诊断步骤**:

1. **检查网络稳定性**
2. **查看服务器日志**
```bash
tail -f logs/gobang-server.log | grep ERROR
```

3. **检查超时设置**
```yaml
game:
  move-timeout: 300  # 5分钟
```

**解决方案**:
- 重新登录继续游戏
- 检查网络连接
- 如频繁断线，联系管理员

---

### 问题：积分计算错误

**症状**: 游戏结束后积分变化异常

**诊断步骤**:

1. **查看对局记录**
```bash
# 访问 /api/records/my 查看最近对局
```

2. **检查积分配置**
```java
// ELOCalculator.java
private static final int K_FACTOR = 32;
```

**解决方案**:
- 确认游戏模式（休闲模式不影响积分）
- 检查是否有异常数据
- 联系管理员修复

---

## 性能问题

### 问题：服务器响应慢

**症状**: API 响应时间过长

**诊断步骤**:

1. **查看系统资源**
```bash
top  # CPU 和内存
df -h  # 磁盘使用
iostat  # IO 状态
```

2. **查看数据库性能**
```sql
SHOW PROCESSLIST;  # 慢查询
SHOW ENGINE INNODB STATUS;
```

3. **启用性能监控**
```yaml
logging:
  level:
    com.gobang: DEBUG
```

**解决方案**:
- 增加 JVM 内存
- 优化数据库查询
- 增加数据库索引
- 启用 Redis 缓存

---

### 问题：内存占用高

**症状**: 服务器内存持续增长

**诊断步骤**:

1. **查看 JVM 堆内存**
```bash
jmap -heap <pid>
```

2. **生成堆转储**
```bash
jmap -dump:format=b,file=heap.hprof <pid>
```

3. **分析堆转储**
- 使用 VisualVM
- 使用 Eclipse MAT

**可能原因**:
- 内存泄露
- 连接未关闭
- 缓存过多

**解决方案**:
- 修复内存泄露
- 确保资源正确释放
- 限制缓存大小

---

## 部署问题

### 问题：ECS 部署后无法访问

**症状**: 本地正常，ECS 上无法访问

**诊断步骤**:

1. **检查安全组规则**
- 登录阿里云控制台
- ECS → 安全组 → 入方向
- 确认 8083 端口已开放

2. **检查服务状态**
```bash
systemctl status gobang
```

3. **检查端口监听**
```bash
netstat -tlnp | grep 8083
```

**解决方案**:
- 添加安全组规则
- 确保服务正在运行
- 检查防火墙设置

---

### 问题：Nginx 代理失败

**症状**: 通过 Nginx 访问时报错

**诊断步骤**:

1. **检查 Nginx 配置**
```bash
sudo nginx -t
```

2. **查看 Nginx 日志**
```bash
tail -f /var/log/nginx/error.log
```

3. **测试后端连接**
```bash
curl http://localhost:8083/api/health
```

**解决方案**:
- 修正 Nginx 配置
- 确保 WebSocket 代理正确
- 检查超时设置

---

## 日志分析

### 日志位置

| 日志类型 | 位置 |
|---------|------|
| 应用日志 | `logs/gobang-server.log` |
| 系统日志 | `/var/log/syslog` |
| Nginx 日志 | `/var/log/nginx/` |

### 关键日志关键字

| 关键字 | 含义 |
|--------|------|
| ERROR | 错误信息 |
| WARN | 警告信息 |
| Exception | 异常堆栈 |
| Timeout | 超时问题 |
| Connection | 连接问题 |

---

## 获取帮助

如果以上方案无法解决问题，请：

1. **收集信息**
   - 错误信息截图
   - 相关日志
   - 系统环境信息

2. **联系支持**
   - GitHub Issues
   - 技术支持邮箱
   - 社区论坛

3. **提供信息**
   - 问题描述
   - 复现步骤
   - 已尝试的解决方案

---

## 常用命令速查

```bash
# 服务管理
systemctl start gobang      # 启动服务
systemctl stop gobang       # 停止服务
systemctl restart gobang    # 重启服务
systemctl status gobang     # 查看状态

# 日志查看
tail -f logs/gobang-server.log       # 实时日志
tail -n 100 logs/gobang-server.log   # 最近100行
journalctl -u gobang -f              # 系统日志

# 数据库
mysql -u root -p gobang              # 登录数据库
mysqldump -u root -p gobang > backup.sql  # 备份

# Redis
redis-cli                            # 连接 Redis
redis-cli FLUSHDB                    # 清空缓存

# 网络测试
curl http://localhost:8083/api/health  # 健康检查
netstat -tlnp | grep 8083             # 查看端口
```

---

希望这份指南能帮助你快速解决问题！
