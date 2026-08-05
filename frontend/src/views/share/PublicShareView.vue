<template>
  <div class="public-share">
    <!-- 加载中 -->
    <div v-if="loading" class="ps-center">
      <el-icon :size="48" class="is-loading"><Loading /></el-icon>
      <p>正在加载分享...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="ps-center">
      <el-icon :size="56" color="var(--cb-danger)"><CircleCloseFilled /></el-icon>
      <h2 class="ps-error-title">{{ error }}</h2>
      <p class="ps-error-desc">分享可能已过期、被取消或不存在</p>
    </div>

    <!-- 密码验证 -->
    <div v-else-if="needPassword && !verified" class="ps-center">
      <div class="ps-card fade-in-up">
        <div class="ps-card-icon">
          <el-icon :size="40" color="var(--cb-primary)"><Lock /></el-icon>
        </div>
        <h2>{{ shareInfo?.title || '分享' }}</h2>
        <p class="ps-card-desc">此分享需要访问口令</p>
        <el-input
          v-model="password"
          type="password"
          placeholder="请输入访问口令"
          show-password
          size="large"
          class="ps-password"
          @keyup.enter="handleVerify"
        />
        <el-button
          type="primary"
          size="large"
          :loading="verifying"
          class="ps-verify-btn"
          @click="handleVerify"
        >
          验证
        </el-button>
        <p v-if="verifyError" class="ps-verify-error">{{ verifyError }}</p>
      </div>
    </div>

    <!-- 分享内容浏览 -->
    <div v-else class="ps-content">
      <div class="ps-header">
        <div class="ps-header-icon">
          <el-icon :size="24" color="var(--cb-primary)"><Share /></el-icon>
        </div>
        <div>
          <h2 class="ps-title">{{ shareInfo?.title || '分享' }}</h2>
          <div class="ps-meta">
            <span v-if="shareInfo?.expiresAt">有效期至 {{ formatTime(shareInfo!.expiresAt) }}</span>
            <span v-else>永久有效</span>
            <span v-if="shareInfo?.downloadLimit != null">
              · 已下载 {{ shareInfo!.downloadCount }} / {{ shareInfo!.downloadLimit }} 次
            </span>
          </div>
        </div>
      </div>

      <!-- 面包屑 -->
      <div v-if="breadcrumbs.length > 0" class="ps-breadcrumb">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>
            <a @click.prevent="navigateToRoot">根目录</a>
          </el-breadcrumb-item>
          <el-breadcrumb-item v-for="(b, i) in breadcrumbs" :key="i">
            <a v-if="i < breadcrumbs.length - 1" @click.prevent="navigateToIndex(i)">{{ b.name }}</a>
            <span v-else class="ps-breadcrumb-current">{{ b.name }}</span>
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <div class="ps-table-card" v-loading="nodesLoading">
        <el-table :data="nodes" style="width: 100%" empty-text="分享内容为空">
          <el-table-column label="名称" min-width="300">
            <template #default="{ row }">
              <div
                class="ps-node-name"
                :class="{ clickable: row.type === 'FOLDER' }"
                @click="row.type === 'FOLDER' && browseInto(row as Node)"
              >
                <FileIcon :name="row.name" :type="row.type" :size="22" />
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="大小" width="120" align="right">
            <template #default="{ row }">
              <span class="cell-muted">
                {{ row.type === 'FOLDER' ? '—' : formatFileSize(row.sizeBytes) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="row-actions" @click.stop>
                <el-button
                  v-if="row.type === 'FILE'"
                  text
                  type="primary"
                  size="small"
                  @click="handlePreview(row as Node)"
                >
                  <el-icon><View /></el-icon>&nbsp;预览
                </el-button>
                <el-button
                  v-if="row.type === 'FILE'"
                  text
                  size="small"
                  @click="handleDownload(row as Node)"
                >
                  <el-icon><Download /></el-icon>&nbsp;下载
                </el-button>
                <span v-if="row.type === 'FOLDER'" class="cell-muted">点击进入</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="ps-footer">
        © 2026 CloudBox · 私有文件管理与分享系统
      </div>
    </div>

    <!-- ============ 预览对话框 ============ -->
    <el-dialog
      v-model="showPreview"
      :title="previewNode?.name || '预览'"
      width="80%"
      top="5vh"
      align-center
      @close="onPreviewClose"
    >
      <div class="preview-container" v-loading="previewLoading">
        <img v-if="previewType === 'image'" :src="previewUrl" :alt="previewNode?.name" class="preview-image" />
        <pre v-else-if="previewType === 'text'" class="preview-text">{{ previewContent }}</pre>
        <iframe v-else-if="previewType === 'pdf'" :src="previewUrl" class="preview-pdf" />
        <div v-else-if="previewType === 'unsupported'" class="preview-unsupported">
          <el-icon :size="64" color="var(--cb-text-muted)"><WarningFilled /></el-icon>
          <p>不支持预览此文件类型</p>
          <el-button type="primary" @click="handleDownload(previewNode!)">下载文件</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, CircleCloseFilled, Lock, Share, Download, View, WarningFilled } from '@element-plus/icons-vue'
import { publicShareApi, downloadBlob } from '@/api'
import { getShareToken, setShareToken, clearShareToken } from '@/api/request'
import { formatFileSize, formatTime } from '@/utils/format'
import FileIcon from '@/components/FileIcon.vue'
import type { PublicShare, Node, BreadcrumbItem } from '@/types'

const route = useRoute()
const shareCode = route.params.shareCode as string

// ============ 状态 ============
const loading = ref(true)
const error = ref('')
const shareInfo = ref<PublicShare | null>(null)
const needPassword = ref(false)
const verified = ref(false)
const password = ref('')
const verifying = ref(false)
const verifyError = ref('')

const nodes = ref<Node[]>([])
const nodesLoading = ref(false)
const currentParentId = ref<number | null>(null)
const breadcrumbs = ref<BreadcrumbItem[]>([])

// ============ 预览状态 ============
const showPreview = ref(false)
const previewNode = ref<Node | null>(null)
const previewUrl = ref('')
const previewContent = ref('')
const previewType = ref<'image' | 'text' | 'pdf' | 'unsupported'>('unsupported')
const previewLoading = ref(false)

onMounted(() => {
  fetchShareInfo()
})

// ============ 获取分享信息 ============
async function fetchShareInfo() {
  loading.value = true
  error.value = ''
  try {
    const info = await publicShareApi.getPublicShare(shareCode)
    shareInfo.value = info
    const passwordRequired = info.passwordRequired ?? info.hasPassword ?? false
    if (passwordRequired) {
      needPassword.value = true
      if (getShareToken(shareCode)) {
        // 页面刷新时复用仍在 30 分钟有效期内的 Share Token。
        verified.value = true
        needPassword.value = false
        await loadNodes()
      }
    } else {
      // 无口令分享：直接加载内容
      needPassword.value = false
      verified.value = true
      await loadNodes()
    }
  } catch (e: any) {
    // 后端可能对口令分享的 getPublicShare 返回 403
    // 此时应进入口令验证流程，而不是显示错误页
    const status = e?.response?.status
    const code = e?.code || e?.response?.data?.code
    if (status === 403 || code === 'FORBIDDEN' || code === 'SHARE_PASSWORD_REQUIRED') {
      needPassword.value = true
      // 尝试从 403 响应体中提取分享信息用于显示标题
      const partialInfo = e?.response?.data?.data
      if (partialInfo) {
        shareInfo.value = partialInfo as PublicShare
      }
    } else if (status === 404) {
      error.value = '分享不存在'
    } else if (status === 410) {
      error.value = '分享已过期'
    } else {
      error.value = e?.message || '分享不存在或已失效'
    }
  } finally {
    loading.value = false
  }
}

// ============ 密码验证 ============
async function handleVerify() {
  if (!password.value.trim()) {
    ElMessage.warning('请输入访问口令')
    return
  }
  verifying.value = true
  verifyError.value = ''
  try {
    const data = await publicShareApi.verifyPassword(shareCode, password.value)
    setShareToken(shareCode, data.shareToken, data.expiresIn)
    verified.value = true
    needPassword.value = false
    ElMessage.success('验证成功')
    await loadNodes()
  } catch (e: any) {
    // 口令错误：清空输入，显示内联错误提示
    password.value = ''
    const status = e?.response?.status
    if (status === 403) {
      verifyError.value = '口令错误，请重试'
    } else if (status === 429) {
      verifyError.value = '尝试过于频繁，请稍后再试'
    } else {
      verifyError.value = e?.message || '验证失败，请重试'
    }
  } finally {
    verifying.value = false
  }
}

// ============ 浏览节点 ============
async function loadNodes(parentId: number | null = null) {
  nodesLoading.value = true
  try {
    const data = await publicShareApi.listNodes(shareCode, parentId)
    nodes.value = data
    currentParentId.value = parentId
  } catch (e: any) {
    // 浏览时 403：口令缺失或 Share Token 已过期，回到口令输入界面
    const status = e?.response?.status
    const code = e?.code || e?.response?.data?.code
    if (
      status === 401 ||
      status === 403 ||
      code === 'SHARE_PASSWORD_REQUIRED' ||
      code === 'SHARE_PASSWORD_INVALID'
    ) {
      needPassword.value = true
      verified.value = false
      clearShareToken(shareCode)
      nodes.value = []
      if (getShareToken(shareCode) === null) {
        ElMessage.info('访问口令已过期，请重新输入')
      }
    }
  } finally {
    nodesLoading.value = false
  }
}

function browseInto(node: Node) {
  breadcrumbs.value.push({ id: node.id, name: node.name })
  loadNodes(node.id)
}

function navigateToRoot() {
  breadcrumbs.value = []
  loadNodes(null)
}

function navigateToIndex(index: number) {
  breadcrumbs.value = breadcrumbs.value.slice(0, index + 1)
  const target = breadcrumbs.value[index]
  loadNodes(target?.id ?? null)
}

// ============ 下载 ============
async function handleDownload(node: Node) {
  try {
    const { blob, filename } = await publicShareApi.downloadFile(shareCode, node.id)
    downloadBlob(blob, filename)
    ElMessage.success('下载已开始')
  } catch {
    // 拦截器处理
  }
}

// ============ 预览 ============

/** 根据 MIME 类型和文件扩展名判断预览类型 */
function detectPreviewType(node: Node): 'image' | 'text' | 'pdf' | 'unsupported' {
  const mime = node.contentType || ''
  if (mime.startsWith('image/')) return 'image'
  if (mime === 'text/plain' || mime.startsWith('text/') || mime === 'application/json' || mime === 'application/xml') return 'text'
  if (mime === 'application/pdf') return 'pdf'
  // MIME 缺失时按扩展名推断
  const ext = node.name.split('.').pop()?.toLowerCase() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(ext)) return 'image'
  if (['txt', 'md', 'json', 'xml', 'yaml', 'yml', 'csv', 'log', 'js', 'ts', 'vue', 'py', 'java', 'go', 'html', 'css', 'sql'].includes(ext)) return 'text'
  if (ext === 'pdf') return 'pdf'
  return 'unsupported'
}

async function handlePreview(node: Node) {
  previewNode.value = node
  previewLoading.value = true
  showPreview.value = true
  previewType.value = detectPreviewType(node)

  if (previewType.value === 'unsupported') {
    previewLoading.value = false
    return
  }

  try {
    // 使用专用预览接口（不计入下载次数）
    const blobUrl = await publicShareApi.previewFile(shareCode, node.id)
    if (previewType.value === 'image' || previewType.value === 'pdf') {
      // 释放旧的 Object URL 防止内存泄漏
      if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
      previewUrl.value = blobUrl
    } else if (previewType.value === 'text') {
      // 预览接口返回的是 Blob URL，需 fetch 取回文本内容
      const resp = await fetch(blobUrl)
      previewContent.value = await resp.text()
      URL.revokeObjectURL(blobUrl)
    }
  } catch {
    ElMessage.error('预览失败，请尝试下载')
    previewType.value = 'unsupported'
  } finally {
    previewLoading.value = false
  }
}

/** 关闭预览对话框时释放 Object URL */
function onPreviewClose() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  previewContent.value = ''
}
</script>

<style scoped>
.public-share {
  min-height: 100vh;
  background: var(--cb-bg-page);
}

.ps-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  text-align: center;
}

.ps-center p {
  color: var(--cb-text-muted);
  margin-top: 12px;
}

.ps-error-title {
  font-size: 20px;
  margin-top: 16px;
  color: var(--cb-text-primary);
}

.ps-error-desc {
  color: var(--cb-text-muted);
  margin-top: 8px;
}

.ps-card {
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-xl);
  padding: 48px;
  box-shadow: var(--cb-shadow-lg);
  max-width: 420px;
  width: 100%;
}

.ps-card-icon {
  margin-bottom: 16px;
}

.ps-card h2 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
}

.ps-card-desc {
  color: var(--cb-text-muted);
  margin-bottom: 24px;
}

.ps-password {
  margin-bottom: 16px;
}

.ps-verify-btn {
  width: 100%;
  height: 44px;
}

.ps-verify-error {
  color: var(--cb-danger);
  font-size: 13px;
  margin-top: 12px;
}

.ps-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.ps-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 20px;
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-lg);
  border: 1px solid var(--cb-border-light);
  box-shadow: var(--cb-shadow-sm);
}

.ps-header-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--cb-radius);
  background: var(--cb-primary-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ps-title {
  font-size: 18px;
  font-weight: 600;
}

.ps-meta {
  font-size: 13px;
  color: var(--cb-text-muted);
  margin-top: 4px;
}

.ps-breadcrumb {
  margin-bottom: 16px;
  padding: 10px 16px;
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius);
  border: 1px solid var(--cb-border-light);
}

.ps-breadcrumb a {
  cursor: pointer;
  color: var(--cb-text-secondary);
}
.ps-breadcrumb a:hover {
  color: var(--cb-primary);
}

.ps-breadcrumb-current {
  font-weight: 600;
  color: var(--cb-text-primary);
}

.ps-table-card {
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-lg);
  border: 1px solid var(--cb-border-light);
  box-shadow: var(--cb-shadow-sm);
  overflow: hidden;
}

.ps-node-name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ps-node-name.clickable {
  cursor: pointer;
}

.ps-node-name.clickable:hover span {
  color: var(--cb-primary);
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.cell-muted {
  color: var(--cb-text-muted);
  font-size: 13px;
}

.ps-footer {
  margin-top: auto;
  padding-top: 24px;
  text-align: center;
  font-size: 12px;
  color: var(--cb-text-muted);
}

/* ============ 预览对话框 ============ */
.preview-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  max-height: 75vh;
  overflow: auto;
  background: var(--cb-bg-page);
  border-radius: var(--cb-radius);
}

.preview-image {
  max-width: 100%;
  max-height: 75vh;
  object-fit: contain;
}

.preview-text {
  width: 100%;
  max-height: 70vh;
  padding: 20px;
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--cb-bg-card);
  color: var(--cb-text-primary);
  border-radius: var(--cb-radius);
}

.preview-pdf {
  width: 100%;
  height: 75vh;
  border: none;
}

.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px;
  color: var(--cb-text-muted);
}

.preview-unsupported p {
  margin: 0;
}
</style>
