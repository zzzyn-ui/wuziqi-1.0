<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

onMounted(async () => {
  // 初始化用户信息（路由守卫可能已经调用过，但这里确保初始化完成）
  if (!userStore.userInfo && userStore.token) {
    await userStore.initUserInfo()
  }

  // 如果有 token 但没有 userInfo，说明登录状态无效，清除
  if (userStore.token && !userStore.userInfo) {
    userStore.logout()
  }
})
</script>

<style>
/* ========== CSS 变量 ========== */
:root {
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

  /* 中性色 */
  --text-primary: #333333;
  --text-secondary: #666666;
  --text-tertiary: #999999;
  --text-placeholder: #cccccc;

  /* 背景色 */
  --bg-primary: #ffffff;
  --bg-secondary: #fafafa;
  --bg-tertiary: #f0f0f0;
  --bg-page: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);

  /* 边框色 */
  --border-color: #e0e0e0;
  --border-light: #f0f0f0;

  /* 阴影 */
  --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.08);
  --shadow-md: 0 4px 20px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 8px 30px rgba(0, 0, 0, 0.15);

  /* 圆角 */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-full: 50%;

  /* 间距 */
  --spacing-xs: 5px;
  --spacing-sm: 10px;
  --spacing-md: 15px;
  --spacing-lg: 20px;
  --spacing-xl: 30px;

  /* 字体 */
  --font-family: 'Microsoft YaHei', 'PingFang SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-size-xs: 12px;
  --font-size-sm: 14px;
  --font-size-md: 16px;
  --font-size-lg: 18px;
  --font-size-xl: 24px;
  --font-size-2xl: 32px;
}

/* ========== 全局重置 ========== */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html {
  width: 100%;
  height: 100%;
  font-size: 16px;
}

body {
  width: 100%;
  min-height: 100vh;
  background: var(--bg-page);
  overflow-x: hidden;
  font-family: var(--font-family);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  min-height: 100vh;
  width: 100%;
  max-width: none;
}

/* ========== 滚动条样式 ========== */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
}

::-webkit-scrollbar-thumb {
  background: var(--primary-light);
  border-radius: var(--radius-sm);
  transition: background 0.3s;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--primary-color);
}

/* ========== 文本选择 ========== */
::selection {
  background: var(--primary-color);
  color: white;
}

::-moz-selection {
  background: var(--primary-color);
  color: white;
}

/* ========== 焦点样式 ========== */
:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

/* ========== 链接样式 ========== */
a {
  color: var(--primary-color);
  text-decoration: none;
  transition: color 0.3s;
}

a:hover {
  color: var(--primary-dark);
}

/* ========== 图片 ========== */
img {
  max-width: 100%;
  height: auto;
  display: block;
}

/* ========== 按钮重置 ========== */
button {
  font-family: inherit;
  font-size: inherit;
  border: none;
  background: none;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* ========== 输入框重置 ========== */
input,
textarea,
select {
  font-family: inherit;
  font-size: inherit;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  outline: none;
  transition: border-color 0.3s;
}

input:focus,
textarea:focus,
select:focus {
  border-color: var(--primary-color);
}

/* ========== 列表重置 ========== */
ul,
ol {
  list-style: none;
}

/* ========== 工具类 ========== */
.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-break {
  word-break: break-word;
}

.no-scroll {
  overflow: hidden;
}

/* ========== 过渡动画 ========== */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s;
}

.slide-enter-from {
  transform: translateX(-100%);
}

.slide-leave-to {
  transform: translateX(100%);
}

/* ========== Element Plus 主题覆盖 ========== */
.el-button--primary {
  background: var(--primary-gradient);
  border: none;
}

.el-button--primary:hover {
  opacity: 0.9;
}

.el-input__wrapper:focus {
  box-shadow: 0 0 0 1px var(--primary-color) inset;
}
</style>
