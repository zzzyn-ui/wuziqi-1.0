<template>
  <div class="page-layout">
    <!-- 页面头部 -->
    <div v-if="showHeader" class="page-header">
      <div class="header-content">
        <div class="header-left">
          <button v-if="showBack" class="back-button" @click="handleBack">
            ←
          </button>
          <div class="page-title">
            <h1>{{ title }}</h1>
            <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
          </div>
        </div>
        <div class="header-right">
          <slot name="header-actions"></slot>
        </div>
      </div>
    </div>

    <!-- 页面内容 -->
    <div class="page-content">
      <slot></slot>
    </div>

    <!-- 页面底部 -->
    <div v-if="showFooter" class="page-footer">
      <slot name="footer"></slot>
      <div v-if="!$slots.footer" class="footer-default">
        © 2024 五子棋对战平台 · All rights reserved
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

interface Props {
  title?: string
  subtitle?: string
  showHeader?: boolean
  showFooter?: boolean
  showBack?: boolean
}

withDefaults(defineProps<Props>(), {
  showHeader: true,
  showFooter: false,
  showBack: false
})

const router = useRouter()

const handleBack = () => {
  router.back()
}
</script>

<style scoped>
.page-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.page-header {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  padding: 40px 20px 30px;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.header-content {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.back-button {
  background: rgba(255,255,255,0.2);
  border: none;
  color: white;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.back-button:hover {
  background: rgba(255,255,255,0.3);
  transform: scale(1.05);
}

.page-title h1 {
  font-size: 32px;
  color: white;
  margin-bottom: 5px;
}

.page-subtitle {
  font-size: 16px;
  color: rgba(255,255,255,0.9);
}

.page-content {
  flex: 1;
  padding: 30px 20px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.page-footer {
  background: #f8f8f8;
  padding: 20px;
  text-align: center;
  border-top: 1px solid #e0e0e0;
}

.footer-default {
  color: #999;
  font-size: 14px;
}

@media (max-width: 768px) {
  .page-header {
    padding: 30px 20px 20px;
  }

  .page-title h1 {
    font-size: 24px;
  }

  .page-content {
    padding: 20px 15px;
  }
}
</style>
