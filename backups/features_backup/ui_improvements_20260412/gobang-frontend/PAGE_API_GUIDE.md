# 页面主题颜色和数据库连接指南

## 🎨 页面主题颜色配置

每个页面都有独特的主题颜色，在 `src/composables/usePageTheme.ts` 中配置：

| 页面 | 渐变色 | 主题色 | 粒子色 |
|------|--------|--------|--------|
| **登录** | #ff6b35 → #ff8c61 | #ff6b35 | rgba(255,107,53,0.3) |
| **注册** | #ff6b35 → #ff8c61 | #ff6b35 | rgba(255,107,53,0.3) |
| **大厅** | #667eea → #764ba2 | #667eea | rgba(102,126,234,0.3) |
| **匹配** | #11998e → #38ef7d | #11998e | rgba(17,153,142,0.3) |
| **游戏** | #4facfe → #00f2fe | #4facfe | rgba(79,172,254,0.3) |
| **人机** | #f093fb → #f5576c | #f093fb | rgba(240,147,251,0.3) |
| **排行榜** | #ffd700 → #ffed4e | #ffd700 | rgba(255,215,0,0.3) |
| **记录** | #a8edea → #fed6e3 | #a8edea | rgba(168,237,234,0.3) |
| **个人** | #ff9a8b → #ffc3a0 | #ff9a8b | rgba(255,154,139,0.3) |
| **残局** | #f093fb → #f5576c | #f093fb | rgba(240,147,251,0.3) |
| **复盘** | #a18cd1 → #fbc2eb | #a18cd1 | rgba(161,140,209,0.3) |
| **好友** | #667eea → #764ba2 | #667eea | rgba(102,126,234,0.3) |
| **帮助** | #ffecd2 → #fcb69f | #fcb69f | rgba(252,182,159,0.3) |
| **设置** | #868f96 → #596164 | #868f96 | rgba(134,143,150,0.3) |
| **房间** | #f093fb → #f5576c | #f093fb | rgba(240,147,251,0.3) |

## 🔌 API 接口说明

所有 API 基础路径：`http://localhost:8080/api`

### 认证 API (`/v2/auth/`)

```typescript
// 登录
POST /v2/auth/login
{
  "username": "string",
  "password": "string"
}

// 注册
POST /v2/auth/register
{
  "username": "string",
  "password": "string",
  "nickname": "string",
  "email": "string"
}
```

### 用户 API (`/v2/users/`)

```typescript
// 获取用户信息
GET /v2/users/{id}

// 更新用户信息
PUT /v2/users/{id}
{
  "nickname": "string"
}

// 获取用户统计
GET /v2/users/{id}/stats

// 搜索用户
GET /v2/users/search?q={query}
```

### 排行榜 API (`/v2/rank/`)

```typescript
// 获取排行榜
GET /v2/rank/{type}
type: all | daily | weekly | monthly

// 获取用户排名
GET /v2/rank/{type}/user/{userId}
```

### 对局记录 API (`/v2/records/`)

```typescript
// 获取对局记录
GET /v2/records/{userId}?filter={filter}&limit={limit}
filter: all | win | loss | draw

// 获取对局详情
GET /v2/records/{userId}/game/{gameId}

// 获取复盘数据
GET /v2/replay/{gameId}
```

### 好友系统 API (`/v2/friends/`)

```typescript
// 获取好友列表
GET /v2/friends/{userId}

// 发送好友请求
POST /v2/friends/request
{
  "userId": number,
  "targetUserId": number,
  "message": "string"
}

// 获取好友请求列表
GET /v2/friends/requests/{userId}

// 处理好友请求
PUT /v2/friends/request/{requestId}?userId={userId}&accept={boolean}

// 删除好友
DELETE /v2/friends/{userId}/{friendId}
```

### 残局挑战 API (`/v2/puzzles/`)

```typescript
// 获取残局列表
GET /v2/puzzles?difficulty={difficulty}
difficulty: beginner | intermediate | advanced | expert

// 获取残局详情
GET /v2/puzzles/{id}

// 提交残局答案
POST /v2/puzzles/{id}/submit
{
  "userId": number,
  "moves": number[][]
}

// 获取用户残局统计
GET /v2/puzzles/stats/{userId}
```

## 💻 前端使用示例

### 1. 应用页面主题

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'

// 获取当前页面主题
const theme = getPageTheme('rank')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
})
</script>

<template>
  <PageHeader
    title="页面标题"
    :gradient-from="theme.gradientFrom"
    :gradient-to="theme.gradientTo"
  />
</template>
```

### 2. 调用 API

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { rankApi } from '@/api'

const loading = ref(false)
const dataList = ref([])

// 获取排行榜
const fetchData = async () => {
  loading.value = true
  try {
    const response = await rankApi.getRankList('all')
    if (response.data && response.data.code === 200) {
      dataList.value = response.data.data.list
    }
  } catch (error) {
    console.error('获取数据失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>
```

### 3. 完整页面示例

```vue
<template>
  <div class="my-page">
    <PageHeader
      title="🏆 排行榜"
      subtitle="查看玩家积分排名"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content">
      <ContentCard>
        <template #header>
          <h3>标题</h3>
        </template>

        <div v-if="loading" class="loading-wrapper">
          <LoadingState text="加载中..." />
        </div>

        <div v-else-if="dataList.length === 0" class="empty-wrapper">
          <EmptyState
            icon="📭"
            title="暂无数据"
            description="还没有任何内容"
          />
        </div>

        <div v-else class="data-list">
          <ListItem
            v-for="item in dataList"
            :key="item.id"
            :title="item.title"
            :subtitle="item.subtitle"
          />
        </div>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, EmptyState, ListItem } from '@/components/shared'
import { api } from '@/api'

const theme = getPageTheme('rank')
const loading = ref(false)
const dataList = ref([])

onMounted(() => {
  applyPageTheme(theme)
  fetchData()
})

const fetchData = async () => {
  // 获取数据逻辑
}
</script>
```

## 🔧 数据库表结构

### 用户表 (user)

```sql
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(50),
  email VARCHAR(100),
  avatar VARCHAR(255),
  rating INT DEFAULT 1200,
  level INT DEFAULT 1,
  exp INT DEFAULT 0,
  status INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_online DATETIME
);
```

### 游戏记录表 (game_record)

```sql
CREATE TABLE game_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id VARCHAR(50),
  black_player_id BIGINT,
  white_player_id BIGINT,
  winner_id BIGINT,
  win_color INT,
  end_reason INT,
  black_rating_change INT DEFAULT 0,
  white_rating_change INT DEFAULT 0,
  game_mode VARCHAR(20),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (black_player_id) REFERENCES user(id),
  FOREIGN KEY (white_player_id) REFERENCES user(id)
);
```

### 好友关系表 (friend)

```sql
CREATE TABLE friend (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  friend_id BIGINT,
  status INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES user(id),
  FOREIGN KEY (friend_id) REFERENCES user(id)
);
```

### 好友请求表 (friend_request)

```sql
CREATE TABLE friend_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  from_user_id BIGINT,
  to_user_id BIGINT,
  message VARCHAR(255),
  status INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (from_user_id) REFERENCES user(id),
  FOREIGN KEY (to_user_id) REFERENCES user(id)
);
```

### 残局表 (puzzle)

```sql
CREATE TABLE puzzle (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100),
  difficulty VARCHAR(20),
  moves INT,
  description TEXT,
  hint TEXT,
  solution JSON,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 残局完成记录表 (puzzle_completion)

```sql
CREATE TABLE puzzle_completion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  puzzle_id BIGINT,
  success BOOLEAN,
  attempts INT DEFAULT 1,
  completed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES user(id),
  FOREIGN KEY (puzzle_id) REFERENCES puzzle(id)
);
```

## 📋 待实现功能清单

### 后端
- [ ] 完善数据库表结构
- [ ] 实现 GameRecordMapper
- [ ] 实现 FriendMapper
- [ ] 实现 PuzzleMapper
- [ ] 完善各个Service中的TODO方法
- [ ] 添加分页支持
- [ ] 添加缓存支持

### 前端
- [ ] 更新所有页面使用统一主题
- [ ] 连接所有API接口
- [ ] 完善错误处理
- [ ] 添加加载状态
- [ ] 添加空状态处理
- [ ] 实现自动刷新机制

## 🚀 快速开始

1. 启动后端服务器
2. 启动前端开发服务器
3. 访问各个页面测试功能
4. 根据需要调整主题颜色
5. 完善数据库连接

---

**文档版本**: v1.0
**最后更新**: 2026-04-05
