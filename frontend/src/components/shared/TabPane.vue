<template>
  <div class="tab-pane-shared">
    <div class="tabs-header">
      <div
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: modelValue === tab.value }"
        @click="$emit('update:modelValue', tab.value)"
      >
        <span v-if="tab.icon" class="tab-icon">{{ tab.icon }}</span>
        <span class="tab-label">{{ tab.label }}</span>
        <span v-if="tab.badge" class="tab-badge">{{ tab.badge }}</span>
      </div>
    </div>
    <div class="tabs-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Tab {
  label: string
  value: string
  icon?: string
  badge?: number | string
}

interface Props {
  tabs: Tab[]
  modelValue: string
}

defineProps<Props>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<style scoped>
.tab-pane-shared {
  width: 100%;
}

.tabs-header {
  display: flex;
  gap: 10px;
  margin-bottom: 30px;
  border-bottom: 2px solid #f0f0f0;
  overflow-x: auto;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  font-size: 16px;
  color: #666;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  transition: all 0.3s;
  white-space: nowrap;
  flex-shrink: 0;
}

.tab-item:hover {
  color: #ff6b35;
}

.tab-item.active {
  color: #ff6b35;
  border-bottom-color: #ff6b35;
  font-weight: 500;
}

.tab-icon {
  font-size: 18px;
}

.tab-label {
  flex-shrink: 0;
}

.tab-badge {
  background: #f56c6c;
  color: white;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

.tabs-content {
  width: 100%;
}

@media (max-width: 768px) {
  .tabs-header {
    margin-bottom: 20px;
  }

  .tab-item {
    padding: 10px 15px;
    font-size: 14px;
  }
}
</style>
