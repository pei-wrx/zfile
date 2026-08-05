<template>
  <div class="trash-view">
    <div class="page-header">
      <div>
        <h2 class="page-title">回收站</h2>
        <p class="page-subtitle">删除的文件会保留在这里，可随时恢复或彻底清除</p>
      </div>
      <div v-if="total > 0" class="header-actions">
        <el-button type="danger" plain :loading="emptying" @click="handleEmptyTrash">
          <el-icon><DeleteFilled /></el-icon>&nbsp;清空回收站
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="table-card">
      <el-table
        :data="items"
        style="width: 100%"
        empty-text="回收站为空"
      >
        <el-table-column label="名称" min-width="300">
          <template #default="{ row }">
            <div class="node-name">
              <FileIcon :name="row.name" :type="row.type" :size="22" />
              <span class="node-name-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <span class="cell-muted">{{ row.type === 'FOLDER' ? '目录' : '文件' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="大小" width="120" align="right">
          <template #default="{ row }">
            <span class="cell-muted">
              {{ row.type === 'FOLDER' ? '—' : formatFileSize(row.sizeBytes) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="删除时间" width="180">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatTime(row.deletedAt || row.updatedAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button text size="small" type="primary" @click="handleRestore(row as Node)">
                <el-icon><RefreshLeft /></el-icon>&nbsp;恢复
              </el-button>
              <el-button text size="small" type="danger" @click="handlePermanentDelete(row as Node)">
                <el-icon><Delete /></el-icon>&nbsp;彻底删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="view-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="fetchTrash"
          @size-change="fetchTrash"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, DeleteFilled, RefreshLeft } from '@element-plus/icons-vue'
import { trashApi } from '@/api'
import { formatFileSize, formatTime } from '@/utils/format'
import FileIcon from '@/components/FileIcon.vue'
import type { Node } from '@/types'

const items = ref<Node[]>([])
const loading = ref(false)
const emptying = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

onMounted(fetchTrash)

async function fetchTrash() {
  loading.value = true
  try {
    const data = await trashApi.listTrash(page.value, size.value)
    items.value = data.items
    total.value = data.total
  } catch { /* 拦截器处理 */ } finally {
    loading.value = false
  }
}

async function handleRestore(row: Node) {
  try {
    await trashApi.restoreNode(row.id)
    ElMessage.success('已恢复')
    fetchTrash()
  } catch { /* 拦截器处理 */ }
}

async function handlePermanentDelete(row: Node) {
  try {
    await ElMessageBox.confirm(
      `彻底删除 "${row.name}" 后将无法恢复，确定吗？`,
      '彻底删除',
      { type: 'error', confirmButtonText: '彻底删除', cancelButtonText: '取消' },
    )
  } catch { return }

  try {
    await trashApi.permanentlyDeleteNode(row.id)
    ElMessage.success('已彻底删除')
    fetchTrash()
  } catch { /* 拦截器处理 */ }
}

async function handleEmptyTrash() {
  try {
    await ElMessageBox.confirm(
      '清空回收站将彻底删除所有文件，此操作无法撤销，确定吗？',
      '清空回收站',
      { type: 'error', confirmButtonText: '清空', cancelButtonText: '取消' },
    )
  } catch { return }

  emptying.value = true
  try {
    await trashApi.emptyTrash()
    ElMessage.success('回收站已清空')
    fetchTrash()
  } catch { /* 拦截器处理 */ } finally {
    emptying.value = false
  }
}
</script>

<style scoped>
.trash-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--cb-text-primary);
  margin-bottom: 6px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--cb-text-muted);
}

.table-card {
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-lg);
  border: 1px solid var(--cb-border-light);
  box-shadow: var(--cb-shadow-sm);
  overflow: hidden;
}

.node-name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-muted {
  color: var(--cb-text-muted);
  font-size: 13px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.view-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--cb-border-light);
}
</style>
