<template>
  <div class="content-card-shared" :class="[variant, { hoverable, clickable }]">
    <div v-if="$slots.header || title" class="card-header">
      <slot name="header">
        <div class="card-title">
          <h3>{{ title }}</h3>
          <p v-if="subtitle" class="card-subtitle">{{ subtitle }}</p>
        </div>
      </slot>
      <div v-if="$slots.extra" class="card-extra">
        <slot name="extra"></slot>
      </div>
    </div>
    <div v-if="$slots.default" class="card-body">
      <slot></slot>
    </div>
    <div v-if="$slots.footer" class="card-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  subtitle?: string
  variant?: 'default' | 'primary' | 'success' | 'warning' | 'danger'
  hoverable?: boolean
  clickable?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'default',
  hoverable: false,
  clickable: false
})
</script>

<style scoped>
.content-card-shared {
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  transition: all 0.3s;
}

.content-card-shared.hoverable:hover {
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.content-card-shared.clickable {
  cursor: pointer;
}

.content-card-shared.clickable:hover {
  background: #fafafa;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.card-title h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 5px;
}

.card-subtitle {
  font-size: 14px;
  color: #999;
}

.card-extra {
  display: flex;
  gap: 10px;
}

.card-body {
  padding: 20px;
}

.card-footer {
  padding: 15px 20px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}

/* 变体样式 */
.content-card-shared.primary {
  border-top: 4px solid #ff6b35;
}

.content-card-shared.success {
  border-top: 4px solid #67c23a;
}

.content-card-shared.warning {
  border-top: 4px solid #e6a23c;
}

.content-card-shared.danger {
  border-top: 4px solid #f56c6c;
}
</style>
