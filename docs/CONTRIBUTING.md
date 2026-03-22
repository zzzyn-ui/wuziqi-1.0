# 贡献指南

感谢你对五子棋在线对战项目的关注！我们欢迎任何形式的贡献。

---

## 目录

1. [行为准则](#行为准则)
2. [如何贡献](#如何贡献)
3. [开发环境搭建](#开发环境搭建)
4. [代码规范](#代码规范)
5. [提交规范](#提交规范)
6. [Pull Request 流程](#pull-request-流程)
7. [问题反馈](#问题反馈)

---

## 行为准则

### 我们的承诺

为了营造开放和友好的环境，我们承诺让每个人都能参与项目，无论其经验水平、性别、性别认同和表达、性取向、残疾、个人外貌、体型、种族、民族、年龄、宗教或国籍如何。

### 我们的标准

积极行为包括：
- 使用友好和包容的语言
- 尊重不同的观点和经验
- 优雅地接受建设性批评
- 关注对社区最有利的事情
- 对其他社区成员表示同理心

不可接受的行为包括：
- 使用性别化语言或图像
- 恶意攻击或侮辱性评论
- 骚扰
- 未经许可发布他人的私人信息
- 其他在专业场合可能被认为不恰当的行为

---

## 如何贡献

### 贡献方式

1. **报告问题**: 发现 Bug 或有功能建议
2. **提交代码**: 修复 Bug 或开发新功能
3. **完善文档**: 改进文档质量
4. **分享经验**: 帮助其他用户解决问题

### 贡献前准备

1. Fork 本仓库
2. Clone 你的 Fork 到本地
3. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
4. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
5. 推送到分支 (`git push origin feature/AmazingFeature`)
6. 创建 Pull Request

---

## 开发环境搭建

### 环境要求

| 软件 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.6+ |
| MySQL | 8.0+ |
| Redis | 7.0+ |
| IDE | IntelliJ IDEA / Eclipse |

### 搭建步骤

#### 1. 克隆项目

```bash
git clone https://github.com/your-username/gobang-server.git
cd gobang-server
```

#### 2. 导入 IDE

**IntelliJ IDEA**:
1. File → Open → 选择项目目录
2. 等待 Maven 依赖下载完成
3. 配置 JDK 17

**Eclipse**:
1. File → Import → Maven → Existing Maven Projects
2. 选择项目目录
3. Finish

#### 3. 配置数据库

```sql
CREATE DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行 SQL 脚本：
```bash
mysql -u root -p gobang < src/main/resources/sql/schema.sql
```

#### 4. 修改配置

编辑 `src/main/resources/application.yml`:

```yaml
database:
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC
  username: your_username
  password: your_password

redis:
  host: localhost
  port: 6379
  password: your_redis_password
```

#### 5. 启动项目

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer"
```

---

## 代码规范

### Java 代码规范

遵循 [阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)：

1. **命名规范**
   - 类名：大驼峰 `UserService`
   - 方法名：小驼峰 `getUserById`
   - 常量：全大写下划线 `MAX_SIZE`
   - 变量：小驼峰 `userName`

2. **注释规范**
   - 类注释：说明类的功能
   - 方法注释：说明参数、返回值、异常
   - 复杂逻辑：添加行内注释

3. **代码格式**
   - 使用 4 空格缩进
   - 每行不超过 120 字符
   - 方法不超过 50 行
   - 类不超过 500 行

### JavaScript 代码规范

遵循 [Airbnb JavaScript Style Guide](https://github.com/airbnb/javascript)：

1. **使用 const/let**，不使用 var
2. **使用单引号**
3. **使用 ===** 而非 ==
4. **函数命名**：小驼峰

### 前端规范

1. **HTML**: 语义化标签，缩进 2 空格
2. **CSS**: BEM 命名规范
3. **响应式**: 移动优先设计

---

## 提交规范

### Commit Message 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| Type | 说明 |
|------|------|
| feat | 新功能 |
| fix | 修复 Bug |
| docs | 文档更新 |
| style | 代码格式（不影响功能） |
| refactor | 重构 |
| perf | 性能优化 |
| test | 测试相关 |
| chore | 构建/工具相关 |

### 示例

```bash
# 新功能
git commit -m "feat(game): 添加悔棋功能"

# 修复 Bug
git commit -m "fix(auth): 修复 token 过期处理错误"

# 文档更新
git commit -m "docs(api): 更新 API 文档"

# 重构
git commit -m "refactor(netty): 优化连接管理器"
```

---

## Pull Request 流程

### PR 标题格式

与 Commit Message 格式相同：
```
type(scope): subject
```

### PR 描述模板

```markdown
## 变更说明
简要描述本次变更的内容

## 变更类型
- [ ] Bug 修复
- [ ] 新功能
- [ ] 代码重构
- [ ] 文档更新
- [ ] 性能优化

## 测试情况
- [ ] 已添加单元测试
- [ ] 已进行手动测试
- [ ] 测试通过

## 相关 Issue
Closes #issue_number

## 截图
如果是 UI 变更，请提供截图
```

### PR 检查清单

提交 PR 前请确认：

- [ ] 代码编译通过
- [ ] 所有测试通过
- [ ] 添加了必要的注释
- [ ] 更新了相关文档
- [ ] 遵循代码规范
- [ ] Commit Message 格式正确

### Code Review

1. **审查重点**
   - 代码逻辑正确性
   - 代码风格一致性
   - 潜在的性能问题
   - 安全性问题

2. **响应反馈**
   - 及时回复 Review 意见
   - 修改后请求再次 Review
   - 讨论不同意见

3. **合并标准**
   - 至少一名维护者 Approval
   - 所有 CI 检查通过
   - 解决所有 Review 意见

---

## 问题反馈

### 报告 Bug

使用 Issue 模板：

```markdown
## Bug 描述
清晰简洁地描述 Bug

## 复现步骤
1. 进入 '...'
2. 点击 '....'
3. 滚动到 '....'
4. 看到错误

## 预期行为
描述预期应该发生什么

## 截图
如果适用，添加截图说明

## 环境信息
- OS: [e.g. Windows 11]
- Browser: [e.g. Chrome 120]
- Server Version: [e.g. 1.2.0]

## 附加信息
其他相关信息
```

### 功能建议

```markdown
## 功能描述
简洁描述你建议的功能

## 问题背景
这个功能解决什么问题

## 解决方案
描述你希望的实现方式

## 替代方案
描述你考虑过的其他解决方案

## 附加信息
其他相关信息或示例
```

---

## 开发指南

### 项目结构

```
src/main/java/com/gobang/
├── GobangServer.java          # 主启动类
├── config/                    # 配置类
├── core/                      # 核心逻辑
│   ├── game/                 # 游戏逻辑
│   ├── netty/                # 网络层
│   ├── protocol/             # 协议定义
│   ├── room/                 # 房间管理
│   └── social/               # 社交功能
├── service/                   # 业务服务
├── mapper/                    # 数据访问
└── util/                      # 工具类
```

### 添加新功能

1. **API 接口**: 在 `core/netty/api/` 创建 Handler
2. **业务逻辑**: 在 `service/` 创建或更新 Service
3. **数据访问**: 在 `mapper/` 创建 Mapper
4. **前端页面**: 在 `static/` 创建 HTML
5. **测试**: 编写单元测试

### 代码审查要点

- 检查 SQL 注入风险
- 检查 XSS 漏洞
- 检鉴认证授权
- 检查资源释放
- 检查并发安全

---

## 获取帮助

如有疑问，可以通过以下方式获取帮助：

- **GitHub Issues**: 技术问题讨论
- **邮件**: dev@example.com
- **社区论坛**: （待建立）

---

## 许可证

贡献的代码将遵循项目的 MIT 许可证。

---

再次感谢你的贡献！
