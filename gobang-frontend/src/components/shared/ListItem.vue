<template>
  <div class="list-item-shared" :class="{ active, clickable }" @click="$emit('click', $event)">
    <div v-if="$slots.avatar || avatar" class="item-avatar">
      <slot name="avatar">
        <div class="avatar-circle" :style="{ background: avatarColor }">
          {{ avatar }}
        </div>
      </slot>
    </div>
    <div class="item-content">
      <div class="item-header">
        <div class="item-title">
          {{ title }}
          <slot name="title-suffix"></slot>
        </div>
        <div v-if="$slots.extra" class="item-extra">
          <slot name="extra"></slot>
        </div>
      </div>
      <div v-if="subtitle || $slots.subtitle" class="item-subtitle">
        <slot name="subtitle">
          {{ subtitle }}
        </slot>
      </div>
      <slot name="content"></slot>
    </div>
    <div v-if="$slots.actions" class="item-actions">
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  subtitle?: string
  avatar?: string
  avatarColor?: string
  active?: boolean
  clickable?: boolean
}

withDefaults(defineProps<Props>(), {
  avatarColor: 'linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%)',
  clickable: true
})

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<style scoped>
.list-item-shared {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
  margin-bottom: 10px;
  transition: all 0.3s;
}

.list-item-shared.clickable {
  cursor: pointer;
}

.list-item-shared.clickable:hover {
  background: #fff5f0;
  transform: translateX(5px);
}

.list-item-shared.active {
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  border: 2px solid #ff6b35;
}

.item-avatar {
  flex-shrink: 0;
}

.avatar-circle {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 5px;
}

.item-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-extra {
  flex-shrink: 0;
}

.item-subtitle {
  font-size: 14px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-actions {
  flex-shrink: 0;
  display: flex;
  gap: 10px;
}
</style>
