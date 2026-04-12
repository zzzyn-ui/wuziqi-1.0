<template>
  <div class="empty-state-shared">
    <div class="empty-icon">{{ icon }}</div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="description" class="empty-description">{{ description }}</div>
    <slot name="actions">
      <button v-if="actionText" class="empty-action" @click="$emit('action')">
        {{ actionText }}
      </button>
    </slot>
  </div>
</template>

<script setup lang="ts">
interface Props {
  icon?: string
  title: string
  description?: string
  actionText?: string
}

withDefaults(defineProps<Props>(), {
  icon: '📭'
})

defineEmits<{
  action: []
}>()
</script>

<style scoped>
.empty-state-shared {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.5;
}

.empty-title {
  font-size: 18px;
  font-weight: 500;
  color: #333;
  margin-bottom: 10px;
}

.empty-description {
  font-size: 14px;
  color: #999;
  margin-bottom: 20px;
  max-width: 400px;
}

.empty-action {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  color: white;
  border: none;
  padding: 12px 30px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.empty-action:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.4);
}
</style>
