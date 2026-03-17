# 五子棋压力测试指南

## 概述

本项目提供了两个压力测试工具，用于测试五子棋服务器在100+用户同时在线场景下的性能表现。

## 测试工具

### 1. Java版本 (LoadTestClient.java)
- 位置: `src/test/java/com/gobang/stress/LoadTestClient.java`
- 优点: 与服务器使用相同技术栈，消息处理更准确
- 缺点: 需要编译，启动较慢

### 2. Python版本 (load_test.py) - 推荐
- 位置: `load_test.py`
- 优点: 简单易用，快速启动，异步性能好
- 缺点: 需要安装Python依赖

## 快速开始

### 使用Python版本（推荐）

1. 安装依赖：
```bash
pip install websockets
```

2. 确保服务器已启动：
```bash
# 检查服务器是否运行
netstat -an | grep 9091
```

3. 运行测试：
```bash
python load_test.py
```

### 使用Java版本

1. 编译项目：
```bash
mvn clean package -DskipTests
```

2. 运行测试：
```bash
# Windows
run-load-test.bat

# Linux/Mac
chmod +x run-load-test.sh
./run-load-test.sh
```

或直接使用Maven：
```bash
mvn exec:java -Dexec.mainClass="com.gobang.stress.LoadTestClient" -Dexec.classpathScope=test
```

## 测试配置

可以在代码顶部修改以下配置：

```python
# Python版本配置
SERVER_URL = "ws://localhost:9091/ws"  # 服务器地址
TOTAL_USERS = 100                       # 总用户数
CONNECTION_BATCH_SIZE = 10              # 每批连接用户数
CONNECTION_BATCH_DELAY = 0.5            # 批次间延迟(秒)
TEST_DURATION = 300                     # 测试持续时间(秒)
```

```java
// Java版本配置
private static final int TOTAL_USERS = 100;
private static final int CONNECTION_BATCH_SIZE = 10;
private static final int CONNECTION_BATCH_DELAY_MS = 500;
private static final int TEST_DURATION_SECONDS = 300;
```

## 测试场景

测试客户端会模拟以下操作：

1. **连接建立**: 分批连接到服务器，避免瞬时压力过大
2. **用户认证**: 每个用户连接后自动进行登录认证
3. **匹配对局**: 登录成功后自动进入匹配队列
4. **游戏操作**: 匹配成功后自动进行落子（随机策略）
5. **游戏循环**: 一局结束后自动重新匹配，持续测试

## 性能指标

测试过程中会实时输出以下指标：

- **在线用户数**: 当前在线的用户数量
- **登录成功率**: 成功登录的用户比例
- **已完成对局数**: 完成的游戏总数量
- **进行中对局数**: 当前正在进行的游戏数量
- **消息吞吐量**: 发送和接收的消息总数及速率
- **延迟统计**: 平均登录延迟和匹配延迟

## 测试报告示例

```
========================================
  压力测试完成 - 最终统计
========================================
  总用户数: 100
  在线用户: 98 (98.0%)
  登录成功: 95 (95.0%)
  登录失败: 5
  已完成对局: 47
  总发送消息: 15234
  总接收消息: 18945
  平均登录延迟: 125.34ms
  平均匹配延迟: 2341.56ms
  消息发送速率: 50.78 msg/s
  消息接收速率: 63.15 msg/s
========================================
```

## 注意事项

1. **服务器状态**: 确保MySQL和Redis都已启动
2. **端口冲突**: 确保9091端口没有被其他程序占用
3. **系统资源**: 大规模测试可能消耗较多内存和CPU
4. **日志文件**: 测试日志会保存到 `load_test_*.log` 文件中
5. **数据库清理**: 测试会产生大量测试用户，建议定期清理

## 高级配置

### 调整并发用户数

对于更大规模的测试，可以增加用户数：

```python
TOTAL_USERS = 500  # 500个用户
CONNECTION_BATCH_SIZE = 20  # 每批20个
```

### 调整测试时间

```python
TEST_DURATION = 600  # 10分钟
```

### 修改用户行为

可以修改客户端的等待时间和策略来模拟不同的用户行为模式。

## 故障排查

### 连接失败
- 检查服务器是否启动
- 检查端口是否正确
- 检查防火墙设置

### 认证失败
- 检查数据库连接
- 确认用户表结构正确

### 匹配超时
- 增加匹配超时时间配置
- 检查Redis连接状态

## 性能优化建议

根据测试结果，可能需要优化的方面：

1. **连接池调优**: 增加数据库连接池大小
2. **线程配置**: 调整Netty工作线程数
3. **内存设置**: 增加JVM堆内存
4. **Redis优化**: 启用Redis持久化和集群模式
5. **负载均衡**: 考虑使用多台服务器进行负载均衡
