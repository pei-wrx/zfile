/**
 * Axios 请求封装
 * - JWT 自动附加
 * - 401 自动刷新 Access Token（并发保护，单一刷新 Promise）
 * - 统一错误处理
 * - Blob / 流式响应透传
 * - 公共分享接口自动附加 X-Share-Token
 */
import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse, TokenData } from '@/types'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// ============ Token 管理（localStorage，避免循环依赖） ============
const TOKEN_KEY = 'cloudbox_access_token'
const REFRESH_KEY = 'cloudbox_refresh_token'
const USER_KEY = 'cloudbox_user'

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_KEY, token)
}

export function getStoredUser(): string | null {
  return localStorage.getItem(USER_KEY)
}

export function saveTokenData(data: TokenData): void {
  setAccessToken(data.accessToken)
  setRefreshToken(data.refreshToken)
  // 新接口仅返回精简 userInfo（id/username/role）；完整 User 由后续 /users/me 填充
  localStorage.setItem(
    USER_KEY,
    JSON.stringify({
      ...data.userInfo,
      email: '',
      status: 'ACTIVE',
      quotaBytes: 0,
      usedBytes: 0,
      createdAt: '',
      updatedAt: '',
    }),
  )
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

// ============ Share Token 管理 ============
const SHARE_TOKEN_KEY_PREFIX = 'cloudbox_share_token:'
const LEGACY_SHARE_TOKEN_KEY = 'cloudbox_share_token'

interface StoredShareToken {
  token: string
  expiresAt: number
}

function getShareTokenKey(shareCode: string): string {
  return `${SHARE_TOKEN_KEY_PREFIX}${encodeURIComponent(shareCode)}`
}

export function getShareToken(shareCode: string): string | null {
  const key = getShareTokenKey(shareCode)
  const storedValue = localStorage.getItem(key)
  if (!storedValue) return null

  try {
    const stored = JSON.parse(storedValue) as StoredShareToken
    if (!stored.token || !stored.expiresAt || stored.expiresAt <= Date.now()) {
      localStorage.removeItem(key)
      return null
    }
    return stored.token
  } catch {
    localStorage.removeItem(key)
    return null
  }
}

export function setShareToken(shareCode: string, token: string, expiresIn: number): void {
  const ttlSeconds = Number.isFinite(expiresIn) && expiresIn > 0 ? expiresIn : 1800
  const stored: StoredShareToken = {
    token,
    expiresAt: Date.now() + ttlSeconds * 1000,
  }
  localStorage.setItem(getShareTokenKey(shareCode), JSON.stringify(stored))
  localStorage.removeItem(LEGACY_SHARE_TOKEN_KEY)
}

export function clearShareToken(shareCode: string): void {
  localStorage.removeItem(getShareTokenKey(shareCode))
}

function getShareCodeFromUrl(url?: string): string | null {
  const match = url?.match(/\/public\/shares\/([^/?#]+)/)
  if (!match?.[1]) return null

  try {
    return decodeURIComponent(match[1])
  } catch {
    return match[1]
  }
}

// ============ 刷新控制（并发保护） ============
let isRefreshing = false
let refreshPromise: Promise<TokenData> | null = null

/** 由 auth store 注入，避免 request → store → request 循环依赖 */
let logoutCallback: (() => void) | null = null

export function setLogoutCallback(cb: () => void): void {
  logoutCallback = cb
}

async function refreshAccessToken(): Promise<TokenData> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error('No refresh token')

  const res = await axios.post<ApiResponse<TokenData>>('/api/v1/auth/refresh', {
    refreshToken,
  })
  if (res.data.code !== 'OK') {
    throw new Error(res.data.message || 'Token refresh failed')
  }
  saveTokenData(res.data.data)
  return res.data.data
}

// ============ 请求拦截器 ============
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const shareCode = getShareCodeFromUrl(config.url)
  const token = getAccessToken()
  if (token && !shareCode) {
    config.headers.Authorization = `Bearer ${token}`
  }

  // 每个分享使用自己的 Share Token，避免访问不同分享时令牌互相覆盖。
  const shareToken = shareCode ? getShareToken(shareCode) : null
  if (shareToken) {
    config.headers['X-Share-Token'] = shareToken
  }
  return config
})

// ============ 响应拦截器 ============
http.interceptors.response.use(
  (response) => {
    // Blob / 流式响应直接透传（用于文件下载和预览）
    const responseType = response.config.responseType
    if (responseType === 'blob' || responseType === 'arraybuffer') {
      return response
    }
    // 业务错误（code !== 'OK'），抛出
    const data = response.data as ApiResponse
    if (data && data.code && data.code !== 'OK') {
      const error = new Error(data.message || '请求失败') as Error & {
        code: string
        response: typeof response
      }
      error.code = data.code
      error.response = response
      return Promise.reject(error)
    }
    return response
  },
  async (error: AxiosError<ApiResponse>) => {
    const { response, config } = error
    if (!config) return Promise.reject(error)
    const isPublicShareAccessRequest = getShareCodeFromUrl(config.url) !== null

    // 401 自动刷新
    if (response?.status === 401 && !isPublicShareAccessRequest) {
      const isAuthRequest =
        config.url?.includes('/auth/login') ||
        config.url?.includes('/auth/refresh') ||
        config.url?.includes('/auth/register')
      if (isAuthRequest) {
        return Promise.reject(error)
      }

      if (!isRefreshing) {
        isRefreshing = true
        refreshPromise = refreshAccessToken().finally(() => {
          isRefreshing = false
          refreshPromise = null
        })
      }

      try {
        const tokenData = await refreshPromise!
        config.headers.Authorization = `Bearer ${tokenData.accessToken}`
        return http(config)
      } catch {
        clearAuth()
        ElMessage.error('登录已过期，请重新登录')
        if (logoutCallback) {
          logoutCallback()
        }
        return Promise.reject(error)
      }
    }

    // 其他错误
    const message = response?.data?.message || error.message || '网络错误'
    if (response?.status !== 401 && !isPublicShareAccessRequest) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

/**
 * 提取响应中的 data 字段
 */
export function unwrap<T>(res: { data: ApiResponse<T> }): T {
  return res.data.data
}

export default http
