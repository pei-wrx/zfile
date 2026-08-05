<template>
  <span class="file-icon" :style="{ color: iconColor, fontSize: `${size}px` }">
    <el-icon :size="size">
      <component :is="iconName" />
    </el-icon>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getFileIcon, getFileColor } from '@/utils/format'
import type { NodeType } from '@/types'

interface Props {
  name: string
  type: NodeType
  size?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 20,
})

const iconName = computed(() => {
  if (props.type === 'FOLDER') return 'Folder'
  return getFileIcon(props.name)
})

const iconColor = computed(() => {
  if (props.type === 'FOLDER') return '#f59e0b'
  return getFileColor(props.name)
})
</script>

<style scoped>
.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>
