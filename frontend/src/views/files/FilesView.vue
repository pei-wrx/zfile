<template>
  <div class="files-view">
    <!-- ============ 面包屑导航 ============ -->
    <div class="files-header">
      <el-button
        v-if="currentParentId !== null"
        size="small"
        class="back-btn"
        @click="goBack"
      >
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回上级
      </el-button>
      <div class="breadcrumb-bar">
        <el-icon :size="14" color="var(--cb-text-muted)"><FolderOpened /></el-icon>
        <span class="breadcrumb-label">当前位置：</span>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>
            <a class="breadcrumb-link" @click.prevent="navigateTo(null)">全部文件</a>
          </el-breadcrumb-item>
          <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="item.id ?? 'root'">
            <a v-if="index < breadcrumbs.length - 1" class="breadcrumb-link" @click.prevent="navigateTo(item.id)">{{ item.name }}</a>
            <span v-else class="breadcrumb-current">{{ item.name }}</span>
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
    </div>

    <!-- ============ 工具栏 ============ -->
    <div class="files-toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="showUpload = true">
          <el-icon><Upload /></el-icon>&nbsp;上传文件
        </el-button>
        <el-button @click="showNewFolder = true">
          <el-icon><FolderAdd /></el-icon>&nbsp;新建目录
        </el-button>
        <el-button :loading="loading" @click="fetchNodes">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
        <template v-if="selectedNodes.length > 0">
          <el-divider direction="vertical" />
          <span class="selection-count">已选 {{ selectedNodes.length }} 项</span>
          <el-button @click="openMoveDialog(selectedIds)">移动</el-button>
          <el-button @click="handleBatchCopy">复制</el-button>
          <el-button type="danger" plain @click="handleBatchDelete">删除</el-button>
        </template>
      </div>
      <div class="toolbar-right">
        <el-select v-model="sort" size="small" style="width: 110px" @change="onSortChange">
          <el-option label="名称" value="name" />
          <el-option label="大小" value="size" />
          <el-option label="修改时间" value="updatedAt" />
          <el-option label="创建时间" value="createdAt" />
        </el-select>
        <el-button size="small" @click="toggleDirection">
          <el-icon><component :is="direction === 'asc' ? 'Top' : 'Bottom'" /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- ============ 搜索提示 ============ -->
    <el-alert
      v-if="searchKeyword"
      type="info"
      :closable="false"
      show-icon
      class="search-alert"
    >
      <template #title>
        搜索 "{{ searchKeyword }}" 的结果，共 {{ total }} 项
        <el-button link type="primary" @click="clearSearch">清除搜索</el-button>
      </template>
    </el-alert>

    <!-- ============ 文件表格 ============ -->
    <div class="table-card" v-loading="loading">
      <el-table
        :data="nodes"
        @selection-change="handleSelectionChange"
        @row-dblclick="handleRowDoubleClick"
        @sort-change="handleSortChange"
        highlight-current-row
        style="width: 100%"
        :empty-text="searchKeyword ? '未找到匹配的文件' : '此目录为空'"
      >
        <el-table-column type="selection" width="48" />

        <el-table-column label="名称" min-width="300" sortable="custom" prop="name">
          <template #default="{ row }">
            <div
              class="node-name"
              :class="{ clickable: row.type === 'FOLDER' }"
              @click="row.type === 'FOLDER' && navigateTo(row.id)"
            >
              <FileIcon :name="row.name" :type="row.type" :size="22" />
              <span class="node-name-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="大小" width="120" align="right" sortable="custom" prop="sizeBytes">
          <template #default="{ row }">
            <span class="cell-muted">
              {{ row.type === 'FOLDER' ? '—' : formatFileSize(row.sizeBytes) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="修改时间" width="180" sortable="custom" prop="updatedAt">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <el-tooltip v-if="row.type === 'FILE'" content="预览" placement="top">
                <el-button text size="small" @click="handlePreview(row as Node)">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="row.type === 'FILE'" content="下载" placement="top">
                <el-button text size="small" @click="handleDownload(row as Node)">
                  <el-icon><Download /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="分享" placement="top">
                <el-button text size="small" @click="openShareDialog(row as Node)">
                  <el-icon><ShareIcon /></el-icon>
                </el-button>
              </el-tooltip>
              <el-dropdown trigger="click" @command="(cmd: string) => handleRowCommand(cmd, row as Node)">
                <el-button text size="small">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="rename">
                      <el-icon><Edit /></el-icon>&nbsp;重命名
                    </el-dropdown-item>
                    <el-dropdown-item command="move">
                      <el-icon><Rank /></el-icon>&nbsp;移动到
                    </el-dropdown-item>
                    <el-dropdown-item command="copy">
                      <el-icon><CopyDocument /></el-icon>&nbsp;复制
                    </el-dropdown-item>
                    <el-dropdown-item command="detail">
                      <el-icon><InfoFilled /></el-icon>&nbsp;详情
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>&nbsp;移入回收站
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div v-if="total > 0" class="files-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="fetchNodes"
          @size-change="fetchNodes"
        />
      </div>
    </div>

    <!-- ============ 上传对话框 ============ -->
    <el-dialog v-model="showUpload" title="上传文件" width="500px" align-center :close-on-click-modal="false">
      <el-upload
        drag
        multiple
        :auto-upload="false"
        :limit="10"
        :on-change="onUploadChange"
        :on-remove="onUploadRemove"
        :file-list="uploadFileList"
        accept="*/*"
      >
        <el-icon class="upload-icon" :size="48"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">单文件上限 100 MiB，每次最多 10 个文件</div>
        </template>
      </el-upload>
      <div v-if="uploading" class="upload-progress">
        <span>正在上传 {{ uploadCurrentName }}...</span>
        <el-progress :percentage="uploadProgress" :stroke-width="6" />
      </div>
      <template #footer>
        <el-button @click="showUpload = false" :disabled="uploading">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="uploadRawFiles.length === 0" @click="handleUpload">
          开始上传 ({{ uploadRawFiles.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 新建目录对话框 ============ -->
    <el-dialog v-model="showNewFolder" title="新建目录" width="400px" align-center :close-on-click-modal="false">
      <el-input v-model="newFolderName" placeholder="请输入目录名称" maxlength="255" @keyup.enter="handleCreateFolder" />
      <template #footer>
        <el-button @click="showNewFolder = false">取消</el-button>
        <el-button type="primary" :loading="creatingFolder" @click="handleCreateFolder">创建</el-button>
      </template>
    </el-dialog>

    <!-- ============ 重命名对话框 ============ -->
    <el-dialog v-model="showRename" title="重命名" width="400px" align-center :close-on-click-modal="false">
      <el-input v-model="renameValue" placeholder="请输入新名称" maxlength="255" @keyup.enter="handleRename" />
      <template #footer>
        <el-button @click="showRename = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="handleRename">确认</el-button>
      </template>
    </el-dialog>

    <!-- ============ 移动对话框 ============ -->
    <el-dialog v-model="showMove" title="移动到..." width="480px" align-center :close-on-click-modal="false">
      <div class="move-dialog">
        <div class="move-breadcrumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>
              <a @click="browseTo(null)">根目录</a>
            </el-breadcrumb-item>
            <el-breadcrumb-item v-for="b in browseBreadcrumbs" :key="b.id ?? 'broot'">
              <a @click="browseTo(b.id)">{{ b.name }}</a>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="move-folder-list" v-loading="browsing">
          <div
            v-for="folder in browseFolders"
            :key="folder.id"
            class="move-folder-item"
            @click="browseTo(folder.id)"
          >
            <el-icon :size="20" color="#f59e0b"><Folder /></el-icon>
            <span class="text-ellipsis">{{ folder.name }}</span>
            <el-icon class="chevron"><ArrowRight /></el-icon>
          </div>
          <div v-if="!browsing && browseFolders.length === 0" class="move-empty">
            此目录下没有子目录
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showMove = false">取消</el-button>
        <el-button type="primary" :loading="moving" @click="handleMoveConfirm">
          移动到当前位置
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 分享对话框 ============ -->
    <el-dialog v-model="showShareDialog" title="创建分享" width="480px" align-center :close-on-click-modal="false">
      <el-form ref="shareFormRef" :model="shareForm" :rules="shareRules" label-width="90px" label-position="left">
        <el-form-item label="分享标题" prop="title">
          <el-input v-model="shareForm.title" placeholder="给分享取个名字" maxlength="128" />
        </el-form-item>
        <el-form-item label="访问口令">
          <el-input v-model="shareForm.password" placeholder="留空表示无口令" show-password maxlength="32" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="shareForm.expiresAt"
            type="datetime"
            placeholder="留空表示永久有效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="下载次数">
          <el-input-number
            v-model="shareForm.downloadLimit"
            :min="1"
            :max="99999"
            placeholder="留空不限"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showShareDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingShare" @click="handleCreateShare">创建分享</el-button>
      </template>
    </el-dialog>

    <!-- ============ 分享结果对话框 ============ -->
    <el-dialog v-model="showShareResult" title="分享已创建" width="480px" align-center>
      <div v-if="shareResult" class="share-result">
        <div class="share-result-icon">
          <el-icon :size="40" color="var(--cb-success)"><CircleCheckFilled /></el-icon>
        </div>
        <div class="share-result-code">
          <el-tag size="large" effect="dark" type="success">{{ shareResult.shareCode }}</el-tag>
        </div>
        <el-input :model-value="shareResultUrl" readonly>
          <template #append>
            <el-button @click="copyShareUrl">复制链接</el-button>
          </template>
        </el-input>
      </div>
      <template #footer>
        <el-button type="primary" @click="showShareResult = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- ============ 预览对话框 ============ -->
    <el-dialog v-model="showPreview" :title="previewNode?.name || '预览'" width="80%" top="5vh" align-center>
      <div class="preview-container" v-loading="previewLoading">
        <img v-if="previewType === 'image'" :src="previewUrl" :alt="previewNode?.name" class="preview-image" />
        <pre v-else-if="previewType === 'text'" class="preview-text">{{ previewContent }}</pre>
        <iframe v-else-if="previewType === 'pdf'" :src="previewUrl" class="preview-pdf" />
        <div v-else-if="previewType === 'unsupported'" class="preview-unsupported">
          <el-icon :size="48" color="var(--cb-text-muted)"><WarningFilled /></el-icon>
          <p>不支持预览此文件类型</p>
          <el-button type="primary" @click="handleDownload(previewNode!)">下载文件</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- ============ 详情抽屉 ============ -->
    <el-drawer v-model="showDetail" :title="detailData?.name || '详情'" size="380px">
      <div v-if="detailData" class="detail-body">
        <div class="detail-icon">
          <FileIcon :name="detailData.name" :type="detailData.type" :size="48" />
        </div>
        <div class="detail-name">{{ detailData.name }}</div>
        <el-divider />
        <div class="detail-item">
          <span class="detail-label">类型</span>
          <span>{{ detailData.type === 'FOLDER' ? '目录' : '文件' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">大小</span>
          <span>{{ detailData.type === 'FOLDER' ? '—' : formatFileSize(detailData.sizeBytes) }}</span>
        </div>
        <div v-if="detailData.contentType" class="detail-item">
          <span class="detail-label">MIME</span>
          <span class="detail-mono">{{ detailData.contentType }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">创建时间</span>
          <span>{{ formatTime(detailData.createdAt) }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">修改时间</span>
          <span>{{ formatTime(detailData.updatedAt) }}</span>
        </div>
        <div v-if="detailData.checksum" class="detail-item">
          <span class="detail-label">SHA-256</span>
          <span class="detail-mono detail-checksum">{{ detailData.checksum }}</span>
        </div>
        <div class="detail-breadcrumbs" v-if="detailData.breadcrumbs?.length">
          <span class="detail-label">路径</span>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="b in detailData.breadcrumbs" :key="b.id ?? 'broot'">
              {{ b.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import {
  Upload, FolderAdd, Refresh, CopyDocument, Delete, View, Download,
  MoreFilled, Edit, Rank, InfoFilled, Share as ShareIcon, UploadFilled, WarningFilled,
  Folder, ArrowRight, CircleCheckFilled,
} from '@element-plus/icons-vue'
import { nodeApi, fileApi, shareApi, downloadBlob } from '@/api'
import { formatFileSize, formatTime } from '@/utils/format'
import FileIcon from '@/components/FileIcon.vue'
import type {
  Node, NodeDetail, BreadcrumbItem, SortField, SortDirection,
  CreateShareRequest, Share,
} from '@/types'

const route = useRoute()
const router = useRouter()

// ============ 列表状态 ============
const nodes = ref<Node[]>([])
const loading = ref(false)
const currentParentId = ref<number | null>(null)
const breadcrumbs = ref<BreadcrumbItem[]>([])
/** 当前文件夹的直接父级 ID（根目录为 null），供「返回上级」使用，不依赖 breadcrumbs 数组结构 */
const currentNodeParentId = ref<number | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const sort = ref<SortField>('name')
const direction = ref<SortDirection>('asc')
const selectedNodes = ref<Node[]>([])
const searchKeyword = ref('')

const selectedIds = ref<number[]>([])
watch(selectedNodes, (val) => { selectedIds.value = val.map((n) => n.id) })

// ============ 初始化 ============
onMounted(() => {
  searchKeyword.value = (route.query.keyword as string) || ''
  fetchNodes()
})

watch(() => route.query.keyword, (val) => {
  searchKeyword.value = (val as string) || ''
  page.value = 1
  if (searchKeyword.value) {
    currentParentId.value = null
    breadcrumbs.value = []
  }
  fetchNodes()
})

// ============ 加载数据 ============
async function fetchNodes() {
  loading.value = true
  try {
    const data = await nodeApi.listNodes({
      parentId: currentParentId.value,
      keyword: searchKeyword.value || undefined,
      sort: sort.value,
      direction: direction.value,
      page: page.value,
      size: size.value,
    })
    nodes.value = data.items
    total.value = data.total
    if (currentParentId.value && !searchKeyword.value) {
      await refreshBreadcrumbs()
    } else if (!searchKeyword.value) {
      breadcrumbs.value = []
    }
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

async function refreshBreadcrumbs() {
  if (!currentParentId.value) {
    breadcrumbs.value = []
    currentNodeParentId.value = null
    return
  }
  try {
    const detail = await nodeApi.getNode(currentParentId.value)
    breadcrumbs.value = detail.breadcrumbs
    // 缓存直接父级 ID，供「返回上级」使用
    currentNodeParentId.value = detail.parentId
  } catch {
    // 忽略
  }
}

function navigateTo(id: number | null) {
  if (searchKeyword.value) {
    searchKeyword.value = ''
    router.replace({ name: 'Files' })
  }
  currentParentId.value = id
  page.value = 1
  selectedNodes.value = []
  fetchNodes()
}

/** 返回上一级目录（使用 NodeDetail.parentId，不依赖 breadcrumbs 数组结构） */
function goBack() {
  navigateTo(currentNodeParentId.value)
}

function onSortChange() { page.value = 1; fetchNodes() }
function toggleDirection() {
  direction.value = direction.value === 'asc' ? 'desc' : 'asc'
  fetchNodes()
}
function handleSortChange({ prop, order }: { prop: string | null; order: string | null }) {
  if (prop) sort.value = prop as SortField
  if (order === 'ascending') direction.value = 'asc'
  else if (order === 'descending') direction.value = 'desc'
  fetchNodes()
}
function clearSearch() {
  router.replace({ name: 'Files' })
}
function handleSelectionChange(sel: Node[]) { selectedNodes.value = sel }
function handleRowDoubleClick(row: Node) {
  if (row.type === 'FOLDER') navigateTo(row.id)
}

// ============ 下载 ============
async function handleDownload(row: Node) {
  try {
    const { blob, filename } = await fileApi.downloadFile(row.id)
    downloadBlob(blob, filename)
    ElMessage.success('下载已开始')
  } catch { /* 拦截器处理 */ }
}

// ============ 预览 ============
const showPreview = ref(false)
const previewNode = ref<Node | null>(null)
const previewUrl = ref('')
const previewContent = ref('')
const previewType = ref<'image' | 'text' | 'pdf' | 'unsupported'>('unsupported')
const previewLoading = ref(false)

async function handlePreview(row: Node) {
  previewNode.value = row
  previewLoading.value = true
  showPreview.value = true
  const mime = row.contentType || ''
  try {
    if (mime.startsWith('image/')) {
      previewType.value = 'image'
      previewUrl.value = await fileApi.previewFile(row.id)
    } else if (mime === 'text/plain' || mime.startsWith('text/') || mime === 'application/json' || mime === 'application/xml') {
      previewType.value = 'text'
      const { blob } = await fileApi.downloadFile(row.id)
      previewContent.value = await blob.text()
    } else if (mime === 'application/pdf') {
      previewType.value = 'pdf'
      previewUrl.value = await fileApi.previewFile(row.id)
    } else {
      previewType.value = 'unsupported'
    }
  } catch {
    ElMessage.error('预览失败')
    previewType.value = 'unsupported'
  } finally {
    previewLoading.value = false
  }
}

// ============ 上传 ============
const showUpload = ref(false)
const uploadFileList = ref<UploadFile[]>([])
const uploadRawFiles = ref<File[]>([])
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadCurrentName = ref('')

function onUploadChange(file: UploadFile) {
  if (file.raw) uploadRawFiles.value.push(file.raw)
}
function onUploadRemove(file: UploadFile) {
  uploadRawFiles.value = uploadRawFiles.value.filter((f) => f.name !== file.name)
}

async function handleUpload() {
  if (uploadRawFiles.value.length === 0) return
  uploading.value = true
  let ok = 0
  for (const file of uploadRawFiles.value) {
    uploadCurrentName.value = file.name
    uploadProgress.value = 0
    try {
      await fileApi.uploadFile(file, {
        parentId: currentParentId.value,
        conflictPolicy: 'RENAME',
        onProgress: (p) => { uploadProgress.value = p },
      })
      ok++
    } catch { /* 拦截器处理 */ }
  }
  uploading.value = false
  showUpload.value = false
  uploadRawFiles.value = []
  uploadFileList.value = []
  if (ok > 0) {
    ElMessage.success(`成功上传 ${ok} 个文件`)
    fetchNodes()
  }
}

// ============ 新建目录 ============
const showNewFolder = ref(false)
const newFolderName = ref('')
const creatingFolder = ref(false)

async function handleCreateFolder() {
  const name = newFolderName.value.trim()
  if (!name) { ElMessage.warning('请输入目录名称'); return }
  creatingFolder.value = true
  try {
    await nodeApi.createFolder({ parentId: currentParentId.value, name })
    ElMessage.success('目录已创建')
    showNewFolder.value = false
    newFolderName.value = ''
    fetchNodes()
  } catch { /* 拦截器处理 */ } finally {
    creatingFolder.value = false
  }
}

// ============ 重命名 ============
const showRename = ref(false)
const renameValue = ref('')
const renaming = ref(false)
const renameTarget = ref<Node | null>(null)

function showRenameDialog(row: Node) {
  renameTarget.value = row
  renameValue.value = row.name
  showRename.value = true
}

async function handleRename() {
  const name = renameValue.value.trim()
  if (!name || !renameTarget.value) return
  renaming.value = true
  try {
    await nodeApi.renameNode(renameTarget.value.id, { name })
    ElMessage.success('重命名成功')
    showRename.value = false
    fetchNodes()
  } catch { /* 拦截器处理 */ } finally {
    renaming.value = false
  }
}

// ============ 移动（文件夹浏览器） ============
const showMove = ref(false)
const moving = ref(false)
const browsing = ref(false)
const browseFolders = ref<Node[]>([])
const browseBreadcrumbs = ref<BreadcrumbItem[]>([])
const browseParentId = ref<number | null>(null)
const moveSourceIds = ref<number[]>([])

function openMoveDialog(ids: number[]) {
  moveSourceIds.value = ids
  browseParentId.value = currentParentId.value
  showMove.value = true
  loadBrowseFolders()
}

async function loadBrowseFolders() {
  browsing.value = true
  try {
    const data = await nodeApi.listNodes({
      parentId: browseParentId.value,
      type: 'FOLDER',
      size: 100,
    })
    browseFolders.value = data.items
    // 面包屑
    if (browseParentId.value) {
      const detail = await nodeApi.getNode(browseParentId.value)
      browseBreadcrumbs.value = detail.breadcrumbs
    } else {
      browseBreadcrumbs.value = []
    }
  } catch { /* 拦截器处理 */ } finally {
    browsing.value = false
  }
}

function browseTo(id: number | null) {
  browseParentId.value = id
  loadBrowseFolders()
}

async function handleMoveConfirm() {
  moving.value = true
  try {
    for (const id of moveSourceIds.value) {
      await nodeApi.moveNode(id, {
        targetParentId: browseParentId.value,
        conflictPolicy: 'RENAME',
      })
    }
    ElMessage.success('移动成功')
    showMove.value = false
    fetchNodes()
  } catch { /* 拦截器处理 */ } finally {
    moving.value = false
  }
}

// ============ 复制 ============
async function handleBatchCopy() {
  if (selectedIds.value.length === 0) return
  try {
    await nodeApi.copyNodes({
      nodeIds: selectedIds.value,
      targetParentId: currentParentId.value,
      conflictPolicy: 'RENAME',
    })
    ElMessage.success('复制成功')
    fetchNodes()
  } catch { /* 拦截器处理 */ }
}

// ============ 删除 ============
async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定将选中的 ${selectedIds.value.length} 项移入回收站？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch { return }

  try {
    await nodeApi.batchTrashNodes({ nodeIds: selectedIds.value })
    ElMessage.success('已移入回收站')
    selectedNodes.value = []
    fetchNodes()
  } catch { /* 拦截器处理 */ }
}

async function handleTrashNode(row: Node) {
  try {
    await ElMessageBox.confirm(`确定将 "${row.name}" 移入回收站？`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch { return }

  try {
    await nodeApi.trashNode(row.id)
    ElMessage.success('已移入回收站')
    fetchNodes()
  } catch { /* 拦截器处理 */ }
}

// ============ 分享 ============
const showShareDialog = ref(false)
const shareFormRef = ref<FormInstance>()
const creatingShare = ref(false)
const shareForm = reactive({
  nodeIds: [] as number[],
  title: '',
  password: '',
  expiresAt: null as Date | null,
  downloadLimit: null as number | null,
})
const shareRules: FormRules = {
  title: [{ required: true, message: '请输入分享标题', trigger: 'blur' }],
}

function openShareDialog(row: Node) {
  shareForm.nodeIds = [row.id]
  shareForm.title = row.name
  shareForm.password = ''
  shareForm.expiresAt = null
  shareForm.downloadLimit = null
  showShareDialog.value = true
}

async function handleCreateShare() {
  if (!shareFormRef.value) return
  await shareFormRef.value.validate(async (valid) => {
    if (!valid) return
    creatingShare.value = true
    try {
      const req: CreateShareRequest = {
        nodeIds: shareForm.nodeIds,
        title: shareForm.title,
      }
      if (shareForm.password) req.password = shareForm.password
      if (shareForm.expiresAt) {
        const d = new Date(shareForm.expiresAt)
        req.expiresAt = {
          seconds: Math.floor(d.getTime() / 1000),
          nanos: (d.getTime() % 1000) * 1_000_000,
        }
      }
      if (shareForm.downloadLimit) req.downloadLimit = shareForm.downloadLimit

      const result = await shareApi.createShare(req)
      ElMessage.success('分享已创建')
      showShareDialog.value = false
      shareResult.value = result
      showShareResult.value = true
    } catch { /* 拦截器处理 */ } finally {
      creatingShare.value = false
    }
  })
}

// ============ 分享结果 ============
const showShareResult = ref(false)
const shareResult = ref<Share | null>(null)

const shareResultUrl = computed(() => {
  if (!shareResult.value) return ''
  const code = shareResult.value.shareCode
  return `${window.location.origin}/share/${code}`
})

function copyShareUrl() {
  navigator.clipboard.writeText(shareResultUrl.value)
  ElMessage.success('链接已复制')
}

// ============ 详情 ============
const showDetail = ref(false)
const detailData = ref<NodeDetail | null>(null)

async function showNodeDetail(row: Node) {
  showDetail.value = true
  detailData.value = null
  try {
    detailData.value = await nodeApi.getNode(row.id)
  } catch { /* 拦截器处理 */ }
}

// ============ 行命令分发 ============
function handleRowCommand(command: string, row: Node) {
  switch (command) {
    case 'rename': showRenameDialog(row); break
    case 'move': openMoveDialog([row.id]); break
    case 'copy': copySingle(row); break
    case 'detail': showNodeDetail(row); break
    case 'delete': handleTrashNode(row); break
  }
}

async function copySingle(row: Node) {
  try {
    await nodeApi.copyNodes({
      nodeIds: [row.id],
      targetParentId: currentParentId.value,
      conflictPolicy: 'RENAME',
    })
    ElMessage.success('复制成功')
    fetchNodes()
  } catch { /* 拦截器处理 */ }
}
</script>

<style scoped>
.files-view {
  max-width: 1200px;
  margin: 0 auto;
}

.files-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--cb-bg-page);
  border-radius: var(--cb-radius);
}

.back-btn {
  flex-shrink: 0;
}

.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.breadcrumb-label {
  font-size: 12px;
  color: var(--cb-text-muted);
  flex-shrink: 0;
}

.breadcrumb-current {
  font-weight: 600;
  color: var(--cb-text-primary);
}

.breadcrumb-link {
  cursor: pointer;
  color: var(--cb-text-secondary);
  transition: color 0.15s;
}
.breadcrumb-link:hover {
  color: var(--cb-primary);
}

.files-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.selection-count {
  font-size: 13px;
  color: var(--cb-text-secondary);
  margin-left: 4px;
  margin-right: 4px;
}

.search-alert {
  margin-bottom: 16px;
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
  cursor: default;
}

.node-name.clickable {
  cursor: pointer;
}

.node-name.clickable:hover .node-name-text {
  color: var(--cb-primary);
}

.node-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s;
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

.files-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--cb-border-light);
}

/* 上传 */
.upload-icon {
  color: var(--cb-primary);
  margin-bottom: 8px;
}

.upload-progress {
  margin-top: 16px;
}

.upload-progress span {
  font-size: 13px;
  color: var(--cb-text-secondary);
  display: block;
  margin-bottom: 6px;
}

/* 移动对话框 */
.move-dialog {
  min-height: 240px;
}

.move-breadcrumb {
  padding: 10px 12px;
  background: var(--cb-bg-page);
  border-radius: var(--cb-radius);
  margin-bottom: 12px;
}

.move-breadcrumb a {
  cursor: pointer;
  color: var(--cb-text-secondary);
}
.move-breadcrumb a:hover {
  color: var(--cb-primary);
}

.move-folder-list {
  max-height: 280px;
  overflow-y: auto;
}

.move-folder-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--cb-radius);
  cursor: pointer;
  transition: background 0.15s;
}

.move-folder-item:hover {
  background: var(--cb-bg-hover);
}

.move-folder-item .chevron {
  margin-left: auto;
  color: var(--cb-text-muted);
}

.move-empty {
  text-align: center;
  padding: 40px;
  color: var(--cb-text-muted);
  font-size: 14px;
}

/* 分享结果 */
.share-result {
  text-align: center;
}

.share-result-icon {
  margin-bottom: 16px;
}

.share-result-code {
  margin-bottom: 16px;
}

/* 预览 */
.preview-container {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  border-radius: var(--cb-radius);
}

.preview-text {
  width: 100%;
  max-height: 70vh;
  overflow: auto;
  background: var(--cb-bg-page);
  padding: 16px;
  border-radius: var(--cb-radius);
  font-family: var(--cb-font-mono);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.preview-pdf {
  width: 100%;
  height: 70vh;
  border: none;
  border-radius: var(--cb-radius);
}

.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px;
}

/* 详情 */
.detail-body {
  padding: 0 4px;
}

.detail-icon {
  text-align: center;
  margin-bottom: 12px;
}

.detail-name {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
  word-break: break-all;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 10px 0;
  gap: 12px;
}

.detail-label {
  color: var(--cb-text-muted);
  font-size: 13px;
  flex-shrink: 0;
}

.detail-mono {
  font-family: var(--cb-font-mono);
  font-size: 12px;
  text-align: right;
  word-break: break-all;
}

.detail-checksum {
  color: var(--cb-text-secondary);
}

.detail-breadcrumbs {
  padding: 10px 0;
}

.detail-breadcrumbs .detail-label {
  display: block;
  margin-bottom: 8px;
}
</style>
