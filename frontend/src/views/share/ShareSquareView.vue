<template>
  <div class="square-view">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h2 class="page-title">分享广场</h2>
        <p class="page-subtitle">浏览所有公开有效的分享，点击卡片查看详情和下载文件</p>
      </div>
      <el-button :loading="loading" text @click="fetchShares">
        <el-icon><Refresh /></el-icon>&nbsp;刷新
      </el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索分享标题..."
        clearable
        class="search-input"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="filterType" placeholder="筛选" clearable style="width: 140px">
        <el-option label="全部" value="" />
        <el-option label="无需口令" value="no-password" />
        <el-option label="需要口令" value="has-password" />
      </el-select>
    </div>

    <!-- 加载中 -->
    <div v-if="loading && shares.length === 0" class="square-loading">
      <el-icon :size="40" class="is-loading"><Loading /></el-icon>
      <p>正在加载分享...</p>
    </div>

    <!-- 空状态 -->
    <EmptyState
      v-else-if="filteredShares.length === 0"
      icon="Share"
      title="暂无公开分享"
      description="目前没有有效的公开分享，稍后再来看看吧"
    />

    <!-- 分享卡片网格 -->
    <div v-else class="share-grid" v-loading="loading">
      <div
        v-for="item in filteredShares"
        :key="item.shareCode"
        class="share-card fade-in-up"
        @click="openShare(item)"
      >
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="card-icon" :class="{ locked: isPasswordRequired(item) }">
            <el-icon :size="20">
              <Lock v-if="isPasswordRequired(item)" />
              <Share v-else />
            </el-icon>
          </div>
          <el-tag :type="getShareStatusTag(item.status).type" size="small" effect="light">
            {{ getShareStatusTag(item.status).text }}
          </el-tag>
        </div>

        <!-- 标题 -->
        <h3 class="card-title">{{ item.title || '未命名分享' }}</h3>

        <!-- 元信息 -->
        <div class="card-meta">
          <div class="meta-row">
            <el-icon :size="14"><Clock /></el-icon>
            <span v-if="item.expiresAt">
              {{ isExpired(item.expiresAt) ? '已过期' : '到期：' + formatDate(item.expiresAt) }}
            </span>
            <span v-else class="meta-permanent">永久有效</span>
          </div>
          <div class="meta-row">
            <el-icon :size="14"><Download /></el-icon>
            <span v-if="item.downloadLimit != null">
              {{ item.downloadCount }} / {{ item.downloadLimit }} 次下载
            </span>
            <span v-else>
              {{ item.downloadCount }} 次下载
            </span>
          </div>
          <div class="meta-row">
            <el-icon :size="14"><Calendar /></el-icon>
            <span>{{ formatTime(item.createdAt) }}</span>
          </div>
        </div>

        <!-- 下载次数进度条 -->
        <div v-if="item.downloadLimit != null" class="card-progress">
          <el-progress
            :percentage="Math.min(Math.round((item.downloadCount / item.downloadLimit) * 100), 100)"
            :color="getProgressColor(item.downloadCount, item.downloadLimit)"
            :show-text="false"
            :stroke-width="4"
          />
        </div>

        <!-- 卡片底部 -->
        <div class="card-footer">
          <el-button type="primary" text size="small">
            查看详情<el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="square-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[12, 24, 48]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="fetchShares"
        @size-change="fetchShares"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Refresh, Search, Share, Lock, Clock, Download, Calendar, ArrowRight, Loading,
} from '@element-plus/icons-vue'
import { publicShareApi } from '@/api'
import { formatTime, formatDate, getShareStatusTag, isExpired } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import type { ShareSquareItem } from '@/types'

const router = useRouter()

// ============ 状态 ============
const shares = ref<ShareSquareItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(12)
const total = ref(0)
const keyword = ref('')
const filterType = ref('')

// ============ 前端二次筛选（后端返回全部有效分享，前端按关键词和口令类型过滤） ============
const filteredShares = computed(() => {
  let result = shares.value
  // 关键词过滤
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    result = result.filter((s) => s.title?.toLowerCase().includes(kw))
  }
  // 口令类型过滤
  if (filterType.value === 'has-password') {
    result = result.filter(isPasswordRequired)
  } else if (filterType.value === 'no-password') {
    result = result.filter((s) => !isPasswordRequired(s))
  }
  return result
})

// ============ 数据加载 ============
async function fetchShares() {
  loading.value = true
  try {
    const data = await publicShareApi.listSquare({
      page: page.value,
      size: size.value,
    })
    shares.value = data.items
    total.value = data.total
  } catch {
    // 拦截器处理错误
  } finally {
    loading.value = false
  }
}

// ============ 打开分享详情 ============
function openShare(item: ShareSquareItem) {
  // 跳转到公共分享页面，由 PublicShareView 处理口令验证和内容浏览
  router.push({ name: 'PublicShare', params: { shareCode: item.shareCode } })
}

// ============ 辅助函数 ============
function isPasswordRequired(share: ShareSquareItem): boolean {
  return share.passwordRequired ?? share.hasPassword ?? false
}

function getProgressColor(count: number, limit: number): string {
  const ratio = count / limit
  if (ratio >= 0.9) return 'var(--cb-danger)'
  if (ratio >= 0.7) return 'var(--cb-warning)'
  return 'var(--cb-primary)'
}

// ============ 初始化 ============
onMounted(() => {
  fetchShares()
})
</script>

<style scoped>
.square-view {
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
  margin-bottom: 4px;
}

.page-subtitle {
  font-size: 13px;
  color: var(--cb-text-muted);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.search-input {
  flex: 1;
  max-width: 360px;
}

.square-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--cb-text-muted);
}

.square-loading p {
  margin-top: 12px;
}

/* ============ 卡片网格 ============ */
.share-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.share-card {
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-lg);
  padding: 20px;
  cursor: pointer;
  border: 1px solid var(--cb-border);
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.share-card:hover {
  border-color: var(--cb-primary);
  box-shadow: 0 8px 24px rgba(79, 124, 255, 0.12);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--cb-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(79, 124, 255, 0.1);
  color: var(--cb-primary);
}

.card-icon.locked {
  background: rgba(245, 158, 11, 0.1);
  color: var(--cb-warning);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--cb-text-primary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--cb-text-muted);
}

.meta-permanent {
  color: var(--cb-success);
  font-weight: 500;
}

.card-progress {
  margin-top: -4px;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--cb-border);
  padding-top: 10px;
  margin-top: auto;
}

/* ============ 分页 ============ */
.square-pagination {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

/* ============ 响应式 ============ */
@media (max-width: 640px) {
  .share-grid {
    grid-template-columns: 1fr;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }
}
</style>
