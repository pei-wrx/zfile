<template>
  <div class="shares-view">
    <div class="page-header">
      <div>
        <h2 class="page-title">我的分享</h2>
        <p class="page-subtitle">管理你创建的文件分享链接</p>
      </div>
      <el-button :loading="loading" text @click="fetchShares">
        <el-icon><Refresh /></el-icon>&nbsp;刷新
      </el-button>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="filterStatus" size="small" @change="handleStatusChange">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="ACTIVE">有效</el-radio-button>
        <el-radio-button label="CANCELLED">已取消</el-radio-button>
        <el-radio-button label="EXPIRED">已过期</el-radio-button>
      </el-radio-group>
    </div>

    <div v-loading="loading" class="shares-card">
      <el-table :data="shares" stripe style="width: 100%" empty-text="暂无分享">
        <el-table-column label="分享标题" min-width="220">
          <template #default="{ row }">
            <div class="share-title">
              <el-icon :size="20" color="var(--cb-primary)"><ShareIcon /></el-icon>
              <div class="share-info">
                <div class="share-name text-ellipsis">{{ row.title }}</div>
                <div class="share-code">提取码：{{ row.shareCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getShareStatusTag(row.status).type" size="small">
              {{ getShareStatusTag(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="访问限制" width="160">
          <template #default="{ row }">
            <div class="share-limits">
              <span class="limit-tag" :class="{ active: row.hasPassword }">
                <el-icon><Lock /></el-icon>
                {{ row.hasPassword ? '有口令' : '无口令' }}
              </span>
              <span class="limit-tag">
                <el-icon><Clock /></el-icon>
                {{ row.expiresAt ? formatTime(row.expiresAt) : '永久' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="下载统计" width="140">
          <template #default="{ row }">
            <span class="download-count">
              {{ row.downloadCount }}
              <span v-if="row.downloadLimit" class="download-limit">/ {{ row.downloadLimit }}</span>
              次
            </span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip content="复制链接" placement="top">
                <el-button text size="small" @click="copyShareUrl(row as Share)">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="row.status === 'ACTIVE'" content="取消分享" placement="top">
                <el-button text size="small" type="danger" @click="cancelShare(row as Share)">
                  <el-icon><Close /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="shares-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, prev, pager, next, sizes"
          background
          @current-change="fetchShares"
          @size-change="fetchShares"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Share as ShareIcon, Lock, Clock, CopyDocument, Close } from '@element-plus/icons-vue'
import { shareApi } from '@/api'
import { formatTime, getShareStatusTag } from '@/utils/format'
import type { Share, ShareStatus } from '@/types'

const shares = ref<Share[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterStatus = ref<ShareStatus | ''>('')

onMounted(fetchShares)

async function fetchShares() {
  loading.value = true
  try {
    const res = await shareApi.listMyShares({
      status: filterStatus.value || undefined,
      page: page.value,
      size: size.value,
    })
    shares.value = res.items
    total.value = res.total
  } catch { /* 拦截器处理 */ } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  page.value = 1
  fetchShares()
}

async function cancelShare(row: Share) {
  try {
    await ElMessageBox.confirm(`确定取消分享 "${row.title}" 吗？`, '取消分享', {
      type: 'warning',
      confirmButtonText: '确定取消',
      cancelButtonText: '保留',
    })
  } catch { return }

  try {
    await shareApi.cancelShare(row.id)
    ElMessage.success('已取消分享')
    fetchShares()
  } catch { /* 拦截器处理 */ }
}

function getShareUrl(row: Share) {
  return `${window.location.origin}/share/${row.shareCode}`
}

async function copyShareUrl(row: Share) {
  try {
    await navigator.clipboard.writeText(getShareUrl(row))
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败')
  }
}
</script>

<style scoped>
.shares-view {
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

.filter-bar {
  margin-bottom: 16px;
}

.shares-card {
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-lg);
  border: 1px solid var(--cb-border-light);
  box-shadow: var(--cb-shadow-sm);
  overflow: hidden;
}

.share-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.share-info {
  min-width: 0;
}

.share-name {
  font-weight: 500;
  color: var(--cb-text-primary);
}

.share-code {
  font-size: 12px;
  color: var(--cb-text-muted);
  margin-top: 2px;
}

.share-limits {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.limit-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--cb-text-secondary);
}

.limit-tag.active {
  color: var(--cb-warning);
}

.download-count {
  font-size: 13px;
  color: var(--cb-text-primary);
}

.download-limit {
  color: var(--cb-text-muted);
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

.shares-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--cb-border-light);
}
</style>
