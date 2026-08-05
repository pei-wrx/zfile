<template>
  <div class="admin-view">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-subtitle">管理系统用户、启用/禁用用户、调整存储配额</p>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名或邮箱..."
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 140px" @change="handleSearch">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="已禁用" value="DISABLED" />
      </el-select>
      <el-button :loading="loading" text @click="fetchUsers">
        <el-icon><Refresh /></el-icon>&nbsp;刷新
      </el-button>
    </div>

    <div v-loading="loading" class="table-card">
      <el-table :data="users" stripe style="width: 100%" empty-text="暂无用户">
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" class="user-avatar">
                {{ row.username?.charAt(0).toUpperCase() }}
              </el-avatar>
              <div>
                <div class="user-name">{{ row.username }}</div>
                <div class="user-email">{{ row.email }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN'" type="warning" size="small">管理员</el-tag>
            <el-tag v-else size="small">普通用户</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getUserStatusTag(row.status).type" size="small">
              {{ getUserStatusTag(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="存储用量" min-width="180">
          <template #default="{ row }">
            <div class="storage-cell">
              <el-progress
                :percentage="getUsagePercent(row as User)"
                :color="getUsageColor(row as User)"
                :show-text="false"
                :stroke-width="5"
              />
              <div class="storage-text">
                {{ formatFileSize(row.usedBytes) }} / {{ formatFileSize(row.quotaBytes) }}
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                text
                size="small"
                :type="row.status === 'ACTIVE' ? 'danger' : 'primary'"
                @click="toggleStatus(row as User)"
              >
                {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
              </el-button>
              <el-button text size="small" @click="openQuotaDialog(row as User)">配额</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="fetchUsers"
          @size-change="fetchUsers"
        />
      </div>
    </div>

    <!-- ============ 配额对话框 ============ -->
    <el-dialog v-model="showQuota" title="调整存储配额" width="440px" align-center :close-on-click-modal="false">
      <div v-if="quotaTarget" class="quota-dialog">
        <div class="quota-user">
          <el-avatar :size="40" class="user-avatar">
            {{ quotaTarget.username.charAt(0).toUpperCase() }}
          </el-avatar>
          <div>
            <div class="user-name">{{ quotaTarget.username }}</div>
            <div class="user-email">已用 {{ formatFileSize(quotaTarget.usedBytes) }}</div>
          </div>
        </div>
        <el-form label-position="top">
          <el-form-item label="新配额 (GB)">
            <el-input-number
              v-model="quotaValue"
              :min="0"
              :precision="1"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
          <el-alert type="info" :closable="false" show-icon>
            输入 0 表示不限制配额。1 GB = 1,073,741,824 字节。
          </el-alert>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="showQuota = false">取消</el-button>
        <el-button type="primary" :loading="savingQuota" @click="handleSaveQuota">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { adminApi } from '@/api'
import { formatFileSize, formatDate, getUserStatusTag } from '@/utils/format'
import type { User, UserStatus } from '@/types'

const users = ref<User[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const statusFilter = ref<UserStatus | ''>('')

onMounted(fetchUsers)

async function fetchUsers() {
  loading.value = true
  try {
    const data = await adminApi.listUsers({
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      size: size.value,
    })
    users.value = data.items
    total.value = data.total
  } catch { /* 拦截器处理 */ } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchUsers()
}

function getUsagePercent(user: User): number {
  if (!user.quotaBytes) return 0
  return Math.min(Math.round((user.usedBytes / user.quotaBytes) * 100), 100)
}

function getUsageColor(user: User): string {
  const pct = getUsagePercent(user)
  if (pct > 90) return 'var(--cb-danger)'
  if (pct > 70) return 'var(--cb-warning)'
  return 'var(--cb-primary)'
}

// ============ 状态切换 ============
async function toggleStatus(row: User) {
  const action = row.status === 'ACTIVE' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 "${row.username}" 吗？`, `${action}用户`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消',
    })
  } catch { return }

  try {
    const newStatus: UserStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    await adminApi.updateUserStatus(row.id, { status: newStatus })
    ElMessage.success(`已${action}用户`)
    fetchUsers()
  } catch { /* 拦截器处理 */ }
}

// ============ 配额调整 ============
const showQuota = ref(false)
const quotaTarget = ref<User | null>(null)
const quotaValue = ref(0)
const savingQuota = ref(false)

function openQuotaDialog(row: User) {
  quotaTarget.value = row
  // 字节 → GB 转换（1 GB = 1073741824 字节）
  quotaValue.value = row.quotaBytes > 0
    ? Number((row.quotaBytes / 1073741824).toFixed(1))
    : 0
  showQuota.value = true
}

async function handleSaveQuota() {
  if (!quotaTarget.value) return
  savingQuota.value = true
  try {
    // GB → 字节转换
    const quotaBytes = Math.round(quotaValue.value * 1073741824)
    await adminApi.updateUserQuota(quotaTarget.value.id, { quotaBytes })
    ElMessage.success('配额已更新')
    showQuota.value = false
    fetchUsers()
  } catch { /* 拦截器处理 */ } finally {
    savingQuota.value = false
  }
}
</script>

<style scoped>
.admin-view {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 6px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--cb-text-muted);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search-input {
  max-width: 360px;
}

.table-card {
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-lg);
  border: 1px solid var(--cb-border-light);
  box-shadow: var(--cb-shadow-sm);
  overflow: hidden;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-accent));
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.user-name {
  font-weight: 500;
  color: var(--cb-text-primary);
}

.user-email {
  font-size: 12px;
  color: var(--cb-text-muted);
}

.storage-cell {
  padding: 4px 0;
}

.storage-text {
  font-size: 12px;
  color: var(--cb-text-secondary);
  margin-top: 4px;
}

.cell-muted {
  color: var(--cb-text-muted);
  font-size: 13px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--cb-border-light);
}

/* 配额对话框 */
.quota-user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 12px;
  background: var(--cb-bg-page);
  border-radius: var(--cb-radius);
}
</style>
