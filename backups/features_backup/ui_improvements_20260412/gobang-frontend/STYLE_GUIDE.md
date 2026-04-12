# 五子棋前端统一样式指南

## 🎨 设计系统

### 颜色系统

```css
/* 主题色 */
--primary-color: #ff6b35;
--primary-light: #ff8c61;
--primary-dark: #e55a2b;
--primary-gradient: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);

/* 功能色 */
--success-color: #67c23a;
--warning-color: #e6a23c;
--danger-color: #f56c6c;
--info-color: #909399;

/* 文本色 */
--text-primary: #333333;
--text-secondary: #666666;
--text-tertiary: #999999;
```

### 间距系统

```css
--spacing-xs: 5px;
--spacing-sm: 10px;
--spacing-md: 15px;
--spacing-lg: 20px;
--spacing-xl: 30px;
```

### 圆角系统

```css
--radius-sm: 8px;
--radius-md: 12px;
--radius-lg: 16px;
--radius-full: 50%;
```

## 📦 共享组件

### 1. PageHeader - 页面头部

```vue
<PageHeader
  title="页面标题"
  subtitle="副标题"
  :show-back="true"
  :show-particles="true"
  gradient-from="#ff6b35"
  gradient-to="#ff8c61"
>
  <template #actions>
    <ActionButton>按钮</ActionButton>
  </template>
</PageHeader>
```

### 2. ContentCard - 内容卡片

```vue
<ContentCard
  title="卡片标题"
  subtitle="副标题"
  variant="primary"
  :hoverable="true"
>
  <template #extra>
    <span>额外内容</span>
  </template>

  <!-- 卡片内容 -->
  <div>内容区域</div>

  <template #footer>
    <div>底部内容</div>
  </template>
</ContentCard>
```

**变体**: `default` | `primary` | `success` | `warning` | `danger`

### 3. TabPane - 标签页

```vue
<TabPane
  v-model="activeTab"
  :tabs="[
    { label: '标签1', value: 'tab1', icon: '🏠' },
    { label: '标签2', value: 'tab2', badge: 5 }
  ]"
>
  <!-- 内容区域 -->
</TabPane>
```

### 4. ListItem - 列表项

```vue
<ListItem
  title="列表项标题"
  subtitle="副标题"
  avatar="A"
  :active="true"
  @click="handleClick"
>
  <template #title-suffix>
    <span class="tag">标签</span>
  </template>

  <template #extra>
    <span>额外信息</span>
  </template>

  <template #actions>
    <ActionButton size="small">操作</ActionButton>
  </template>
</ListItem>
```

### 5. ActionButton - 按钮

```vue
<ActionButton
  variant="primary"
  size="large"
  icon="➕"
  icon-position="left"
  :block="true"
  :loading="false"
  :disabled="false"
  @click="handleClick"
>
  按钮文字
</ActionButton>
```

**变体**: `primary` | `secondary` | `success` | `danger` | `warning` | `text`
**尺寸**: `small` | `medium` | `large`

### 6. EmptyState - 空状态

```vue
<EmptyState
  icon="📭"
  title="暂无数据"
  description="这里什么都没有"
  action-text="去添加"
  @action="handleAction"
/>
```

### 7. LoadingState - 加载状态

```vue
<LoadingState
  text="加载中..."
  size="40px"
/>
```

## 🎯 页面模板

### 标准页面结构

```vue
<template>
  <div class="page-view">
    <!-- 页面头部 -->
    <PageHeader
      :title="pageTitle"
      :subtitle="pageSubtitle"
    />

    <!-- 页面内容 -->
    <div class="page-content">
      <ContentCard>
        <!-- 使用共享组件 -->
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { PageHeader, ContentCard } from '@/components/shared'
</script>

<style scoped>
.page-view {
  min-height: 100vh;
  padding-bottom: 40px;
}

.page-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}
</style>
```

## 💡 使用示例

### 带标签页的列表页面

```vue
<template>
  <div class="my-page">
    <PageHeader title="我的页面" subtitle="页面描述" />

    <div class="page-content">
      <ContentCard>
        <TabPane v-model="activeTab" :tabs="tabs">
          <div v-if="loading" class="loading-wrapper">
            <LoadingState text="加载中..." />
          </div>

          <div v-else-if="items.length === 0" class="empty-wrapper">
            <EmptyState
              icon="📭"
              title="暂无数据"
            />
          </div>

          <div v-else class="list-items">
            <ListItem
              v-for="item in items"
              :key="item.id"
              :title="item.title"
              :subtitle="item.subtitle"
              @click="handleClick(item)"
            />
          </div>
        </TabPane>
      </ContentCard>
    </div>
  </div>
</template>
```

### 表单页面

```vue
<template>
  <div class="form-page">
    <PageHeader
      title="编辑资料"
      :show-back="true"
    />

    <div class="page-content">
      <ContentCard>
        <form class="form-content" @submit.prevent="handleSubmit">
          <div class="form-group">
            <label class="form-label">用户名</label>
            <input
              v-model="form.username"
              type="text"
              class="form-input"
              placeholder="请输入用户名"
            />
          </div>

          <div class="form-actions">
            <ActionButton type="submit">保存</ActionButton>
            <ActionButton variant="secondary" @click="handleCancel">取消</ActionButton>
          </div>
        </form>
      </ContentCard>
    </div>
  </div>
</template>
```

## 📐 最佳实践

### 1. 保持一致性
- 所有页面使用相同的页面结构
- 使用共享组件而不是重复实现
- 遵循统一的间距和圆角规范

### 2. 响应式设计
- 使用相对单位（em, rem, %）
- 在移动端测试所有页面
- 使用 flex 和 grid 布局

### 3. 性能优化
- 使用 `v-if` vs `v-show` 适当
- 列表使用 `key` 属性
- 大列表使用虚拟滚动

### 4. 可访问性
- 使用语义化 HTML
- 添加适当的 ARIA 属性
- 确保键盘导航可用

## 🔧 自定义主题

### 覆盖 CSS 变量

```css
/* 在你的组件样式中 */
.my-component {
  --primary-color: #custom-color;
  --radius-md: 20px;
}
```

### 创建自定义变体

```vue
<ContentCard
  class="custom-card"
  title="自定义卡片"
/>
```

```css
.custom-card {
  border: 2px solid #ff6b35;
  box-shadow: 0 8px 30px rgba(255, 107, 53, 0.2);
}
```

## 📚 相关文件

- 共享组件: `src/components/shared/`
- 全局样式: `src/styles/common.css`
- CSS 变量: `src/App.vue` root
- 页面示例: `src/views/RankView.vue`
