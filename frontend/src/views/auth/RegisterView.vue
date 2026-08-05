<template>
  <div class="auth-page">
    <div class="auth-background" aria-hidden="true">
      <div class="blob blob-1" />
      <div class="blob blob-2" />
      <div class="blob blob-3" />
    </div>

    <div class="auth-card fade-in-up">
      <div class="auth-header">
        <div class="auth-logo">CB</div>
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-subtitle">加入 CloudBox 开始管理你的文件</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名（4-32 位字母、数字或下划线）"
            clearable
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="form.email"
            placeholder="邮箱"
            clearable
          >
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（至少 8 位）"
            show-password
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            show-password
            @keyup.enter="handleRegister"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="auth-submit"
            :loading="loading"
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>

    <div class="auth-copyright">
      © 2026 CloudBox · 私有文件管理与分享系统
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

// ============ 校验规则（对齐 OpenAPI Schema） ============
const validateUsername = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入用户名'))
  } else if (!/^[A-Za-z0-9_]{4,32}$/.test(value)) {
    callback(new Error('用户名需为 4-32 位字母、数字或下划线'))
  } else {
    callback()
  }
}

const validateEmail = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入邮箱'))
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

const validatePassword = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (value.length < 8) {
    callback(new Error('密码长度不能少于 8 位'))
  } else if (value.length > 72) {
    callback(new Error('密码长度不能超过 72 位'))
  } else {
    callback()
  }
}

const validateConfirm = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
}

async function handleRegister() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const { username, email, password } = form
      await authStore.register({ username, email, password })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch {
      // 错误已由 axios 拦截器统一处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: 20px;
  overflow: hidden;
}

.auth-background {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  z-index: 0;
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
  pointer-events: none;
}

.blob-1 {
  width: 400px;
  height: 400px;
  background: #4f7cff;
  top: -100px;
  right: -100px;
  animation: float 8s ease-in-out infinite;
}

.blob-2 {
  width: 300px;
  height: 300px;
  background: #8b5cf6;
  bottom: -80px;
  left: -80px;
  animation: float 10s ease-in-out infinite reverse;
}

.blob-3 {
  width: 200px;
  height: 200px;
  background: #0ea5e9;
  top: 40%;
  left: 50%;
  animation: float 12s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -30px); }
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: var(--cb-radius-xl);
  padding: 40px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.2);
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.auth-logo {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: var(--cb-radius-lg);
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
  box-shadow: var(--cb-shadow-md);
}

.auth-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--cb-text-primary);
  margin-bottom: 8px;
}

.auth-subtitle {
  font-size: 14px;
  color: var(--cb-text-muted);
}

.auth-submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--cb-radius);
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-primary-light));
  border: none;
  letter-spacing: 1px;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  box-shadow: var(--cb-shadow-md);
}

.auth-submit:hover {
  transform: translateY(-1px);
  box-shadow: var(--cb-shadow-lg);
}

.auth-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--cb-text-secondary);
}

.auth-footer a {
  font-weight: 600;
}

.auth-copyright {
  position: relative;
  z-index: 1;
  margin-top: 24px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}
</style>
