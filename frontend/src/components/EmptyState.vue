<template>
  <div class="empty-state">
    <div class="empty-illustration">
      <slot name="icon">
        <el-icon :size="56" color="var(--cb-text-muted)">
          <component :is="icon" />
        </el-icon>
      </slot>
    </div>
    <h3 class="empty-title">{{ title }}</h3>
    <p v-if="description" class="empty-description">{{ description }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  description?: string
  icon?: string
}

withDefaults(defineProps<Props>(), {
  title: '暂无数据',
  description: '',
  icon: 'FolderOpened',
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 20px;
  text-align: center;
}

.empty-illustration {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: var(--cb-bg-page);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--cb-text-primary);
  margin-bottom: 8px;
}

.empty-description {
  font-size: 14px;
  color: var(--cb-text-muted);
  max-width: 360px;
  line-height: 1.6;
}

.empty-action {
  margin-top: 16px;
}
</style>
