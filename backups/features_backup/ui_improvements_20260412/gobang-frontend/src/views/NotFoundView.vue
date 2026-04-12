<template>
  <div class="not-found-view">
    <div class="content">
      <h1>404</h1>
      <p class="message">抱歉，您访问的页面不存在</p>
      <el-button type="primary" @click="goHome">返回大厅</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/home')
}

const goBack = () => {
  router.back()
}
</script>

<style scoped>
.not-found-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);
}

.content {
  text-align: center;
}

.content h1 {
  font-size: 120px;
  font-weight: bold;
  color: #ff6b35;
  margin: 0;
  line-height: 1;
}

.message {
  font-size: 18px;
  color: #666;
  margin: 20px 0 30px;
}
</style>
