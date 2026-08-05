<template>
  <div class="app-layout">
    <!-- ============ 侧边栏 ============ -->
    <aside class="sidebar" :class="{ collapsed }">
      <!-- 品牌区 -->
      <div class="brand" @click="router.push({ name: 'Files' })">
        <div class="brand-logo">CB</div>
        <div v-show="!collapsed" class="brand-text">
          <span class="brand-name">CloudBox</span>
          <span class="brand-subtitle">私有文件管理</span>
        </div>
      </div>

      <!-- 主导航 -->
      <nav class="nav-main">
        <router-link
          v-for="item in navItems"
          :key="item.name"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item.active) }"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed" class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- 管理员分区 -->
      <div v-if="authStore.isAdmin" class="nav-section">
        <div v-show="!collapsed" class="nav-section-title">管理</div>
        <router-link
          to="/admin/users"
          class="nav-item"
          :class="{ active: route.path.startsWith('/admin') }"
        >
          <el-icon :size="18"><Setting /></el-icon>
          <span v-show="!collapsed" class="nav-label">用户管理</span>
        </router-link>
      </div>

      <!-- 存储用量卡片 -->
      <div v-if="!collapsed" class="sidebar-footer">
        <div v-if="storage" class="storage-card">
          <div class="storage-header">
            <el-icon :size="14" color="var(--cb-primary)"><Coin /></el-icon>
            <span>存储空间</span>
          </div>
          <el-progress
            :percentage="Math.min(Math.round(storage.usageRatio * 100), 100)"
            :color="storageColor"
            :show-text="false"
            :stroke-width="6"
          />
          <div class="storage-text">
            <span>{{ formatFileSize(storage.usedBytes) }}</span>
            <span class="text-muted"> / {{ formatFileSize(storage.quotaBytes) }}</span>
          </div>
        </div>
      </div>

      <!-- 折叠按钮 -->
      <div class="collapse-toggle" @click="collapsed = !collapsed">
        <el-icon :size="18"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
      </div>
    </aside>

    <!-- ============ 主区域 ============ -->
    <div class="main-area">
      <!-- 顶部栏 -->
      <header class="topbar">
        <div class="topbar-search">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件或目录..."
            clearable
            class="search-input"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="topbar-actions">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-profile">
              <el-avatar :size="32" class="user-avatar">
                {{ authStore.user?.username?.charAt(0).toUpperCase() || 'U' }}
              </el-avatar>
              <span class="user-name">{{ authStore.user?.username || '用户' }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><UserFilled /></el-icon>
                  个人资料
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, ArrowDown, UserFilled, SwitchButton, Coin, Setting,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api'
import { formatFileSize } from '@/utils/format'
import type { StorageUsage } from '@/types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// ============ 侧边栏 ============
const collapsed = ref(false)

const navItems = [
  { name: 'files', label: '我的文件', icon: 'Folder', to: { name: 'Files' }, active: 'Files' },
  { name: 'shares', label: '我的分享', icon: 'Share', to: { name: 'MyShares' }, active: 'MyShares' },
  { name: 'square', label: '分享广场', icon: 'Promotion', to: { name: 'ShareSquare' }, active: 'ShareSquare' },
  { name: 'trash', label: '回收站', icon: 'Delete', to: { name: 'Trash' }, active: 'Trash' },
  { name: 'profile', label: '个人中心', icon: 'User', to: { name: 'Profile' }, active: 'Profile' },
]

function isActive(routeName: string): boolean {
  return (route.name as string) === routeName
}

// ============ 搜索 ============
const searchKeyword = ref('')

function handleSearch() {
  const keyword = searchKeyword.value.trim()
  router.push({ name: 'Files', query: keyword ? { keyword } : {} })
}

// ============ 存储用量 ============
const storage = ref<StorageUsage | null>(null)

const storageColor = computed(() => {
  if (!storage.value) return 'var(--cb-primary)'
  const ratio = storage.value.usageRatio
  if (ratio > 0.9) return 'var(--cb-danger)'
  if (ratio > 0.7) return 'var(--cb-warning)'
  return 'var(--cb-primary)'
})

async function fetchStorage() {
  try {
    storage.value = await userApi.getStorageUsage()
  } catch {
    // 忽略
  }
}

// ============ 用户菜单 ============
async function handleCommand(command: string) {
  if (command === 'profile') {
    router.push({ name: 'Profile' })
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消',
      })
      await authStore.logout()
      ElMessage.success('已退出登录')
      router.push({ name: 'Login' })
    } catch {
      // 用户取消
    }
  }
}

onMounted(() => {
  fetchStorage()
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ============ 侧边栏 ============ */
.sidebar {
  width: var(--cb-sidebar-width);
  min-width: var(--cb-sidebar-width);
  background: var(--cb-bg-card);
  border-right: 1px solid var(--cb-border-light);
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease, min-width 0.25s ease;
  position: relative;
  z-index: 10;
}

.sidebar.collapsed {
  width: 64px;
  min-width: 64px;
}

/* 品牌区 */
.brand {
  height: var(--cb-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--cb-border-light);
  overflow: hidden;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: var(--cb-radius);
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
  letter-spacing: 0.5px;
  box-shadow: var(--cb-shadow-sm);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.brand-subtitle {
  font-size: 11px;
  color: var(--cb-text-muted);
}

/* 导航 */
.nav-main {
  flex: 1;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-section {
  padding: 0 8px;
}

.nav-section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--cb-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 4px 12px 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--cb-radius);
  color: var(--cb-text-regular);
  text-decoration: none;
  transition: all 0.15s ease;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  background: var(--cb-bg-hover);
  color: var(--cb-text-primary);
}

.nav-item.active {
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-primary-light));
  color: #fff;
  box-shadow: var(--cb-shadow-sm);
}

.nav-item.active:hover {
  color: #fff;
}

.nav-label {
  flex: 1;
}

/* 存储卡片 */
.sidebar-footer {
  padding: 0 12px 12px;
}

.storage-card {
  background: var(--cb-bg-page);
  border-radius: var(--cb-radius);
  padding: 12px;
}

.storage-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--cb-text-secondary);
  margin-bottom: 8px;
  font-weight: 600;
}

.storage-text {
  font-size: 12px;
  color: var(--cb-text-primary);
  margin-top: 6px;
}

.text-muted {
  color: var(--cb-text-muted);
}

/* 折叠按钮 */
.collapse-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  cursor: pointer;
  color: var(--cb-text-muted);
  transition: all 0.15s ease;
  border-top: 1px solid var(--cb-border-light);
}

.collapse-toggle:hover {
  background: var(--cb-bg-hover);
  color: var(--cb-text-primary);
}

/* ============ 主区域 ============ */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: var(--cb-header-height);
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: var(--cb-bg-card);
  border-bottom: 1px solid var(--cb-border-light);
  gap: 16px;
  flex-shrink: 0;
}

.topbar-search {
  flex: 1;
  max-width: 480px;
}

.search-input :deep(.el-input__wrapper) {
  background: var(--cb-bg-page);
  border: none;
  box-shadow: none;
  border-radius: var(--cb-radius);
}

.search-input :deep(.el-input__wrapper:hover),
.search-input :deep(.el-input__wrapper.is-focus) {
  background: var(--cb-bg-hover);
  box-shadow: 0 0 0 1px var(--cb-border-dark) inset;
}

.topbar-actions {
  margin-left: auto;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--cb-radius);
  transition: background 0.15s ease;
}

.user-profile:hover {
  background: var(--cb-bg-hover);
}

.user-avatar {
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-accent));
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--cb-text-primary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: var(--cb-bg-page);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
