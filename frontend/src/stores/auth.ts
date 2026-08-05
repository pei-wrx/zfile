/**
 * 认证状态管理
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, userApi } from '@/api'
import {
  saveTokenData,
  clearAuth as clearLocalAuth,
  getAccessToken,
  getStoredUser,
} from '@/api/request'
import type { LoginRequest, RegisterRequest, User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  // ============ State ============
  const user = ref<User | null>(null)
  const isLoggedIn = ref(false)

  // ============ Getters ============
  const isAdmin = computed(() => {
    const role = user.value?.role
    return typeof role === 'string' && role.toUpperCase() === 'ADMIN'
  })

  // ============ Actions ============

  /** 从 localStorage 恢复登录状态；返回 Promise 供路由守卫等待用户信息刷新完成 */
  let initPromise: Promise<void> | null = null

  async function init(): Promise<void> {
    if (initPromise) return initPromise
    initPromise = (async () => {
      const token = getAccessToken()
      const storedUser = getStoredUser()
      if (token && storedUser) {
        try {
          user.value = JSON.parse(storedUser)
          isLoggedIn.value = true
        } catch {
          clearAuth()
        }
      }
      // 登录态下强制刷新用户信息，保证 role/status/createdAt 等字段与后端一致
      if (isLoggedIn.value) {
        try {
          await fetchUser()
        } catch {
          // 刷新失败则沿用 localStorage 中已有信息；后续接口 401 会再次触发登录态校验
        }
      }
    })()
    return initPromise
  }

  /** 登录 */
  async function login(data: LoginRequest) {
    const tokenData = await authApi.login(data)
    saveTokenData(tokenData)
    // 新接口仅返回精简 userInfo（id/username/role），构建最小 User 占位
    user.value = {
      id: tokenData.userInfo.id,
      username: tokenData.userInfo.username,
      email: '',
      role: tokenData.userInfo.role,
      status: 'ACTIVE',
      quotaBytes: 0,
      usedBytes: 0,
      createdAt: '',
      updatedAt: '',
    } as User
    isLoggedIn.value = true
    initPromise = null
    // 立即拉取完整用户信息（含 email/status/quota/createdAt 等字段）
    try {
      await fetchUser()
    } catch {
      // 忽略刷新失败，保留登录响应中的精简信息
    }
    return tokenData
  }

  /** 注册 — 按 OpenAPI 规范返回 User，注册后需跳转登录页 */
  async function register(data: RegisterRequest): Promise<User> {
    return await authApi.register(data)
  }

  /** 拉取最新用户信息 */
  async function fetchUser() {
    const u = await userApi.getCurrentUser()
    user.value = u
    localStorage.setItem('cloudbox_user', JSON.stringify(u))
    return u
  }

  /** 清除认证状态（不调用退出接口） */
  function clearAuth() {
    user.value = null
    isLoggedIn.value = false
    initPromise = null
    clearLocalAuth()
  }

  /** 退出登录（调用退出接口并清除本地状态） */
  async function logout() {
    const refreshToken = localStorage.getItem('cloudbox_refresh_token')
    try {
      if (refreshToken) {
        await authApi.logout(refreshToken)
      }
    } catch {
      // 忽略退出请求错误
    } finally {
      clearAuth()
    }
  }

  return {
    user,
    isLoggedIn,
    isAdmin,
    init,
    login,
    register,
    fetchUser,
    logout,
    clearAuth,
  }
})
