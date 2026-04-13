<template>
  <button
    class="action-btn"
    :class="[variant, size, { block, loading, disabled, iconOnly }]"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="btn-spinner"></span>
    <span v-else-if="icon && iconPosition === 'left'" class="btn-icon">{{ icon }}</span>
    <span v-if="$slots.default" class="btn-text"><slot></slot></span>
    <span v-if="icon && iconPosition === 'right'" class="btn-icon">{{ icon }}</span>
  </button>
</template>

<script setup lang="ts">
interface Props {
  variant?: 'primary' | 'secondary' | 'success' | 'danger' | 'warning' | 'text' | 'ghost'
  size?: 'small' | 'medium' | 'large'
  icon?: string
  iconPosition?: 'left' | 'right'
  block?: boolean
  loading?: boolean
  disabled?: boolean
  iconOnly?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'medium',
  iconPosition: 'left',
  block: false,
  loading: false,
  disabled: false
})

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<style scoped>
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: none;
  border-radius: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 尺寸 */
.action-btn.small {
  padding: 8px 16px;
  font-size: 14px;
}

.action-btn.medium {
  padding: 12px 24px;
  font-size: 16px;
}

.action-btn.large {
  padding: 16px 32px;
  font-size: 18px;
}

/* 变体 */
.action-btn.primary {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  color: white;
}

.action-btn.primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.4);
}

.action-btn.secondary {
  background: #f0f0f0;
  color: #333;
}

.action-btn.secondary:hover:not(:disabled) {
  background: #e0e0e0;
}

.action-btn.success {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  color: white;
}

.action-btn.success:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.4);
}

.action-btn.danger {
  background: linear-gradient(135deg, #f56c6c 0%, #f89898 100%);
  color: white;
}

.action-btn.danger:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.4);
}

.action-btn.warning {
  background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
  color: white;
}

.action-btn.warning:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.4);
}

.action-btn.text {
  background: transparent;
  color: #ff6b35;
  padding: 8px 12px;
}

.action-btn.text:hover:not(:disabled) {
  background: rgba(255, 107, 53, 0.1);
}

.action-btn.ghost {
  background: #f5f5f5;
  color: #667eea;
  border: 1px solid #ddd;
}

.action-btn.ghost:hover:not(:disabled) {
  background: rgba(102, 126, 234, 0.1);
  border-color: #667eea;
}

/* 块级按钮 */
.action-btn.block {
  width: 100%;
}

/* 仅图标按钮 */
.action-btn.iconOnly {
  padding: 12px;
  border-radius: 50%;
}

/* 加载状态 */
.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.btn-icon {
  font-size: 18px;
}
</style>
