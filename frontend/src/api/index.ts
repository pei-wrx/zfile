/**
 * CloudBox API 接口模块
 * 对齐 openapi.yaml (v1.0) — 2026-08-05 更新
 */
import http, { unwrap } from './request'
import type {
  ApiResponse,
  PageData,
  User,
  TokenData,
  StorageUsage,
  Node,
  NodeDetail,
  Share,
  PublicShare,
  ShareTokenData,
  ShareSquareItem,
  RegisterRequest,
  LoginRequest,
  RefreshTokenRequest,
  UpdateProfileRequest,
  ChangePasswordRequest,
  ListNodeParams,
  CreateFolderRequest,
  RenameNodeRequest,
  MoveNodeRequest,
  CopyNodesRequest,
  NodeIdsRequest,
  RestoreNodeRequest,
  CreateShareRequest,
  ListSharesParams,
  ListShareSquareParams,
  ListUsersParams,
  UpdateUserStatusRequest,
  UpdateQuotaRequest,
  ConflictPolicy,
} from '@/types'

// ============ 请求辅助函数 ============
const get = <T>(url: string, params?: object): Promise<T> =>
  http.get<ApiResponse<T>>(url, { params }).then((res) => unwrap<T>(res))

const post = <T>(url: string, data?: object): Promise<T> =>
  http.post<ApiResponse<T>>(url, data).then((res) => unwrap<T>(res))

const patch = <T>(url: string, data?: object): Promise<T> =>
  http.patch<ApiResponse<T>>(url, data).then((res) => unwrap<T>(res))

const put = <T>(url: string, data?: object): Promise<T> =>
  http.put<ApiResponse<T>>(url, data).then((res) => unwrap<T>(res))

const del = <T>(url: string): Promise<T> =>
  http.delete<ApiResponse<T>>(url).then((res) => unwrap<T>(res))

// ============ 下载辅助 ============

/** 从 Content-Disposition 头提取文件名 */
function extractFilename(disposition: string | undefined): string {
  if (!disposition) return 'download'
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;\s]+)/i)
  if (utf8Match) return decodeURIComponent(utf8Match[1])
  const plainMatch = disposition.match(/filename="?([^";\s]+)"?/i)
  return plainMatch ? plainMatch[1] : 'download'
}

/** 触发浏览器下载 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 200)
}

// ============ 认证 ============
export const authApi = {
  /** 注册用户 — 返回 User */
  register: (data: RegisterRequest): Promise<User> =>
    post<User>('/auth/register', data),

  /** 登录，返回 TokenData（新接口含精简 userInfo，仅 id/username/role） */
  login: (data: LoginRequest): Promise<TokenData> =>
    post<TokenData>('/auth/login', data),

  /** 刷新令牌 */
  refresh: (refreshToken: string): Promise<TokenData> =>
    post<TokenData>('/auth/refresh', { refreshToken }),

  /** 退出登录 */
  logout: (refreshToken: string): Promise<null> =>
    post<null>('/auth/logout', { refreshToken }),
}

// ============ 当前用户 ============
export const userApi = {
  getCurrentUser: (): Promise<User> => get<User>('/users/me'),

  /** 修改资料 — 新接口支持 username 和 email 均可选 */
  updateProfile: (data: UpdateProfileRequest): Promise<User> =>
    patch<User>('/users/me', data),

  changePassword: (data: ChangePasswordRequest): Promise<null> =>
    put<null>('/users/me/password', data),

  getStorageUsage: (): Promise<StorageUsage> =>
    get<StorageUsage>('/users/me/storage'),
}

// ============ 节点与目录 ============
export const nodeApi = {
  /** 查询目录内容或搜索节点 */
  listNodes: (params: ListNodeParams = {}): Promise<PageData<Node>> =>
    get<PageData<Node>>('/nodes', params),

  /** 获取节点详情和面包屑 */
  getNode: (nodeId: number): Promise<NodeDetail> =>
    get<NodeDetail>(`/nodes/${nodeId}`),

  /** 新建目录 */
  createFolder: (data: CreateFolderRequest): Promise<Node> =>
    post<Node>('/folders', data),

  /** 重命名节点 */
  renameNode: (nodeId: number, data: RenameNodeRequest): Promise<Node> =>
    patch<Node>(`/nodes/${nodeId}`, data),

  /** 移动节点 */
  moveNode: (nodeId: number, data: MoveNodeRequest): Promise<Node> =>
    post<Node>(`/nodes/${nodeId}/move`, data),

  /** 批量复制节点 */
  copyNodes: (data: CopyNodesRequest): Promise<Node[]> =>
    post<Node[]>('/nodes/copy', data),

  /** 移入回收站 */
  trashNode: (nodeId: number): Promise<null> => del<null>(`/nodes/${nodeId}`),

  /** 批量移入回收站 */
  batchTrashNodes: (data: NodeIdsRequest): Promise<null> =>
    post<null>('/nodes/batch-delete', data),
}

// ============ 文件上传 / 下载 / 预览 ============
export const fileApi = {
  /**
   * 上传单个文件
   * @param file 文件对象
   * @param options.parentId 父目录 ID（省略表示根目录）
   * @param options.conflictPolicy 冲突策略，默认 REJECT
   * @param options.onProgress 上传进度回调（0-100）
   */
  uploadFile: (
    file: File,
    options: {
      parentId?: number | null
      conflictPolicy?: ConflictPolicy
      onProgress?: (percent: number) => void
    } = {},
  ): Promise<Node> => {
    const { parentId, conflictPolicy = 'REJECT', onProgress } = options
    const formData = new FormData()
    formData.append('file', file)
    if (parentId != null) {
      formData.append('parentId', String(parentId))
    }
    formData.append('conflictPolicy', conflictPolicy)

    return http
      .post<ApiResponse<Node>>('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 600000, // 10 分钟超时（支持大文件）
        onUploadProgress: (e) => {
          if (onProgress && e.total) {
            onProgress(Math.round((e.loaded * 100) / e.total))
          }
        },
      })
      .then((res) => unwrap<Node>(res))
  },

  /** 下载文件，返回 Blob 和文件名 */
  async downloadFile(fileId: number): Promise<{ blob: Blob; filename: string }> {
    const res = await http.get(`/files/${fileId}/content`, {
      responseType: 'blob',
    })
    return {
      blob: res.data as Blob,
      filename: extractFilename(res.headers['content-disposition']),
    }
  },

  /** 预览文件（内联），返回 Blob URL */
  async previewFile(fileId: number): Promise<string> {
    const res = await http.get(`/files/${fileId}/preview`, {
      responseType: 'blob',
      timeout: 600000,
    })
    return URL.createObjectURL(res.data as Blob)
  },
}

// ============ 回收站 ============
export const trashApi = {
  /** 查询回收站根节点 */
  listTrash: (page = 1, size = 20): Promise<PageData<Node>> =>
    get<PageData<Node>>('/trash', { page, size }),

  /** 恢复回收站节点 */
  restoreNode: (nodeId: number, data?: RestoreNodeRequest): Promise<Node> =>
    post<Node>(`/trash/${nodeId}/restore`, data ?? {}),

  /** 彻底删除回收站节点 */
  permanentlyDeleteNode: (nodeId: number): Promise<null> =>
    del<null>(`/trash/${nodeId}`),

  /** 清空当前用户回收站 */
  emptyTrash: (): Promise<null> => del<null>('/trash'),
}

// ============ 分享管理 ============
export const shareApi = {
  /** 创建分享（expiresAt 格式为 { seconds, nanos }） */
  createShare: (data: CreateShareRequest): Promise<Share> =>
    post<Share>('/shares', data),

  /** 查询我的分享 */
  listMyShares: (params: ListSharesParams = {}): Promise<PageData<Share>> =>
    get<PageData<Share>>('/shares', params),

  /** 获取分享详情 */
  getMyShare: (shareId: number): Promise<Share> =>
    get<Share>(`/shares/${shareId}`),

  /** 取消分享 */
  cancelShare: (shareId: number): Promise<null> => del<null>(`/shares/${shareId}`),
}

// ============ 公共分享访问 ============
export const publicShareApi = {
  /** 获取分享公开信息（字段：passwordRequired、expiresAt、downloadLimit、downloadCount、shareUrl） */
  getPublicShare: (shareCode: string): Promise<PublicShare> =>
    get<PublicShare>(`/public/shares/${shareCode}`),

  /** 验证分享口令，返回 Share Token */
  verifyPassword: (shareCode: string, password: string): Promise<ShareTokenData> =>
    post<ShareTokenData>(`/public/shares/${shareCode}/verify`, { password }),

  /** 浏览分享内容 */
  listNodes: (shareCode: string, parentId?: number | null): Promise<Node[]> =>
    get<Node[]>(
      `/public/shares/${shareCode}/nodes`,
      parentId != null ? { parentId } : undefined,
    ),

  /** 下载分享中的文件 */
  async downloadFile(
    shareCode: string,
    fileId: number,
  ): Promise<{ blob: Blob; filename: string }> {
    const res = await http.get(
      `/public/shares/${shareCode}/files/${fileId}/content`,
      { responseType: 'blob' },
    )
    return {
      blob: res.data as Blob,
      filename: extractFilename(res.headers['content-disposition']),
    }
  },

  /** 预览分享中的文件（内联预览，不计入下载次数），返回 Blob URL */
  async previewFile(
    shareCode: string,
    fileId: number,
  ): Promise<string> {
    const res = await http.get(
      `/public/shares/${shareCode}/files/${fileId}/preview`,
      { responseType: 'blob', timeout: 600000 },
    )
    return URL.createObjectURL(res.data as Blob)
  },

  /**
   * 分享广场 — 分页查询所有有效公开分享
   * 后端需实现 GET /public/shares 接口（分页返回 ShareSquareItem）
   * 筛选条件：status=ACTIVE 且未过期且下载次数未满
   */
  listSquare: (params: ListShareSquareParams = {}): Promise<PageData<ShareSquareItem>> =>
    get<PageData<ShareSquareItem>>('/public/shares', params),
}

// ============ 管理员 ============
export const adminApi = {
  /** 分页查询用户 */
  listUsers: (params: ListUsersParams = {}): Promise<PageData<User>> =>
    get<PageData<User>>('/admin/users', params),

  /** 启用或禁用用户 */
  updateUserStatus: (
    userId: number,
    data: UpdateUserStatusRequest,
  ): Promise<User> => patch<User>(`/admin/users/${userId}/status`, data),

  /** 调整用户存储配额 */
  updateUserQuota: (
    userId: number,
    data: UpdateQuotaRequest,
  ): Promise<StorageUsage> =>
    patch<StorageUsage>(`/admin/users/${userId}/quota`, data),
}
