<template>
  <div class="loading-state-shared">
    <div class="spinner" :style="{ width: size, height: size }"></div>
    <p v-if="text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
interface Props {
  text?: string
  size?: string
}

withDefaults(defineProps<Props>(), {
  size: '40px'
})
</script>

<style scoped>
.loading-state-shared {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #999;
}

.spinner {
  border: 3px solid #f0f0f0;
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 15px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-state-shared p {
  font-size: 14px;
}
</style>
