# 五子棋在线对战系统 - 备份信息

**备份时间**: 2026-04-12 16:15:53
**备份版本**: v1.0

## 备份内容

### 后端代码
- `src/` - Java后端源代码
  - `src/main/java/com/gobang/` - 主要业务逻辑
  - `src/main/resources/` - 配置文件和资源

### 前端代码
- `gobang-frontend/` - Vue3 + TypeScript前端源代码
  - `gobang-frontend/src/` - 源代码
  - `gobang-frontend/public/` - 静态资源

### 配置文件
- `pom.xml` - Maven项目配置
- `docker-compose.yml` - Docker编排配置
- `Dockerfile` - Docker镜像构建配置
- `.env.example` - 环境变量示例

### 文档
- `docs/` - 项目文档

## 本次修改内容

1. **修复聊天历史加载功能**
   - 将聊天历史从 `/user/queue/chat/history` 改为 topic-based 方式 `/topic/chat/history/{userId}`
   - 后端使用 `messagingTemplate.convertAndSend()` 替代 `convertAndSendToUser()`
   - 前端订阅改为 `/topic/chat/history/${userId}`

2. **删除页脚统计信息**
   - 移除了首页页尾的"在线玩家"和"今日对局"显示
   - 清理了相关的 `onlineCount` 和 `totalGames` 变量

3. **删除排行榜在线状态**
   - 从排行榜项中移除了"在线"标签显示
   - 删除了相关的 `.online-tag` CSS样式
   - 移除了模拟数据生成中的 `online` 属性

## 技术栈

### 后端
- Java 17
- Spring Boot 3.x
- MyBatis Plus
- WebSocket + STOMP
- MySQL

### 前端
- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia
- SockJS + @stomp/stompjs

## 恢复方法

1. 将备份内容复制到项目根目录
2. 确保MySQL数据库已启动
3. 运行后端: `mvn spring-boot:run`
4. 运行前端: `cd gobang-frontend && npm run dev`
5. 访问: `http://localhost:5173`

## 注意事项

- 本备份仅包含源代码和配置文件
- 不包含数据库数据
- 不包含node_modules和target目录（需重新编译）
