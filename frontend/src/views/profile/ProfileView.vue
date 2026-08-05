<template>
  <div class="profile-view">
    <div class="page-header">
      <h2 class="page-title">个人中心</h2>
      <p class="page-subtitle">管理你的账户信息和存储空间</p>
    </div>

    <div class="profile-grid">
      <!-- ============ 用户卡片 ============ -->
      <div class="profile-card user-card fade-in-up">
        <el-avatar :size="72" class="big-avatar">
          {{ authStore.user?.username?.charAt(0).toUpperCase() || 'U' }}
        </el-avatar>
        <h3 class="user-username">{{ authStore.user?.username }}</h3>
        <p class="user-email">{{ authStore.user?.email }}</p>
        <div class="user-role">
          <el-tag v-if="authStore.isAdmin" type="warning" effect="dark">管理员</el-tag>
          <el-tag v-else effect="plain">普通用户</el-tag>
          <el-tag :type="getUserStatusTag(authStore.user?.status || '').type" size="small">
            {{ getUserStatusTag(authStore.user?.status || '').text }}
          </el-tag>
        </div>
        <div class="user-meta">
          注册于 {{ formatDate(authStore.user?.createdAt || '') }}
        </div>
      </div>

      <!-- ============ 存储用量卡片 ============ -->
      <div class="profile-card storage-card fade-in-up">
        <h3 class="card-title">存储空间</h3>
        <div class="storage-gauge">
          <el-progress
            type="dashboard"
            :percentage="usagePercent"
            :color="usageColor"
            :stroke-width="10"
            :width="140"
          />
        </div>
        <div class="storage-stats">
          <div class="stat-item">
            <span class="stat-value">{{ formatFileSize(storage?.usedBytes || 0) }}</span>
            <span class="stat-label">已使用</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ formatFileSize(storage?.availableBytes || 0) }}</span>
            <span class="stat-label">可用</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ formatFileSize(storage?.quotaBytes || 0) }}</span>
            <span class="stat-label">总配额</span>
          </div>
        </div>
        <el-button text :loading="storageLoading" @click="fetchStorage">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
    </div>

    <!-- ============ 修改资料 ============ -->
    <div class="profile-card form-card fade-in-up">
      <h3 class="card-title">修改资料</h3>
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="80px" style="max-width: 480px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="profileForm.username" placeholder="请输入用户名（3-32 位）" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存修改</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ============ 修改密码 ============ -->
    <div class="profile-card form-card fade-in-up">
      <h3 class="card-title">修改密码</h3>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px" style="max-width: 480px">
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="pwdForm.currentPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingPwd" @click="handleChangePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api'
import { formatFileSize, formatDate, getUserStatusTag } from '@/utils/format'
import type { StorageUsage } from '@/types'

const authStore = useAuthStore()
const router = useRouter()

// ============ 存储用量 ============
const storage = ref<StorageUsage | null>(null)
const storageLoading = ref(false)

const usagePercent = computed(() => {
  if (!storage.value) return 0
  return Math.min(Math.round(storage.value.usageRatio * 100), 100)
})

const usageColor = computed(() => {
  if (usagePercent.value > 90) return 'var(--cb-danger)'
  if (usagePercent.value > 70) return 'var(--cb-warning)'
  return 'var(--cb-primary)'
})

async function fetchStorage() {
  storageLoading.value = true
  try {
    storage.value = await userApi.getStorageUsage()
  } catch { /* 拦截器处理 */ } finally {
    storageLoading.value = false
  }
}

// ============ 修改资料 ============
const profileFormRef = ref<FormInstance>()
const savingProfile = ref(false)
const profileForm = reactive({
  username: authStore.user?.username || '',
  email: authStore.user?.email || '',
})
const profileRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度为 3-32 位', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '仅支持字母、数字和下划线', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
}

async function handleSaveProfile() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingProfile.value = true
    try {
      await userApi.updateProfile({
        username: profileForm.username,
        email: profileForm.email,
      })
      await authStore.fetchUser()
      ElMessage.success('资料已更新')
    } catch { /* 拦截器处理 */ } finally {
      savingProfile.value = false
    }
  })
}

// ============ 修改密码 ============
const pwdFormRef = ref<FormInstance>()
const savingPwd = ref(false)
const pwdForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: unknown, value: string, cb: (err?: Error) => void) => {
  if (value !== pwdForm.newPassword) cb(new Error('两次输入的密码不一致'))
  else cb()
}

const pwdRules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度 8-72 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleChangePassword() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingPwd.value = true
    try {
      await userApi.changePassword({
        currentPassword: pwdForm.currentPassword,
        newPassword: pwdForm.newPassword,
      })
      ElMessage.success('密码已修改，请重新登录')
      await authStore.logout()
      router.push({ name: 'Login' })
    } catch { /* 拦截器处理 */ } finally {
      savingPwd.value = false
    }
  })
}

onMounted(() => {
  fetchStorage()
})
</script>

<style scoped>
.profile-view {
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
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

.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

.profile-card {
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-lg);
  border: 1px solid var(--cb-border-light);
  box-shadow: var(--cb-shadow-sm);
  padding: 24px;
  margin-bottom: 16px;
}

/* 用户卡片 */
.user-card {
  text-align: center;
}

.big-avatar {
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-accent));
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 12px;
}

.user-username {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 4px;
}

.user-email {
  color: var(--cb-text-muted);
  font-size: 14px;
  margin-bottom: 12px;
}

.user-role {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
}

.user-meta {
  font-size: 12px;
  color: var(--cb-text-muted);
  border-top: 1px solid var(--cb-border-light);
  padding-top: 12px;
}

/* 存储卡片 */
.storage-card {
  text-align: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
  text-align: left;
}

.storage-gauge {
  margin-bottom: 16px;
}

.storage-stats {
  display: flex;
  justify-content: space-around;
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--cb-text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--cb-text-muted);
  margin-top: 4px;
}
</style>
