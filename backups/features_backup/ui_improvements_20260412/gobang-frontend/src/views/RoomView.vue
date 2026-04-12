<template>
  <div class="room-view">
    <div class="header">
      <h1>房间对战</h1>
      <p class="subtitle">请前往首页创建或加入房间</p>
    </div>

    <div class="content">
      <div class="entry-card">
        <div class="entry-icon">🏠</div>
        <h2>房间对战</h2>
        <p>创建房间邀请好友对战，或加入公开房间与其他玩家切磋</p>
        <el-button type="primary" size="large" @click="goToHome">
          前往首页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goToHome = () => {
  router.push('/home?panel=room')
}
</script>

<style scoped>
.room-view {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.header {
  text-align: center;
  margin-bottom: 40px;
}

.header h1 {
  font-size: 48px;
  color: white;
  margin-bottom: 10px;
}

.subtitle {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.8);
}

.content {
  width: 100%;
  max-width: 500px;
}

.entry-card {
  background: white;
  border-radius: 24px;
  padding: 50px 40px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.entry-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.entry-card h2 {
  font-size: 28px;
  color: #333;
  margin-bottom: 15px;
}

.entry-card p {
  font-size: 16px;
  color: #666;
  margin-bottom: 30px;
  line-height: 1.6;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  padding: 15px 50px;
  font-size: 18px;
}
</style>
