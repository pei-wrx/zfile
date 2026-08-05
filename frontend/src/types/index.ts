/**
 * CloudBox API 类型定义
 * 对齐 openapi.yaml (v1.0) 的接口规范 — 2026-08-05 更新
 */

// ============ 通用 ============
export interface ApiResponse<T = unknown> {
  code: string
  message: string
  data: T
  requestId?: string
  errors?: ErrorDetail[] | null
}

export interface ErrorDetail {
  field?: string
  message?: string
  rejectedValue?: unknown
}

export interface PageData<T> {
  items: T[]
  page: number
  size: number
  total: number
  pages: number
}

// ============ 时间戳（后端 java.time.Instant 序列化为 {seconds, nanos}） ============
export interface InstantPayload {
  seconds: number
  nanos: number
}

// ============ 枚举 ============
export type NodeType = 'FILE' | 'FOLDER'
export type NodeStatus = 'ACTIVE' | 'TRASHED'
export type UserStatus = 'ACTIVE' | 'DISABLED'
export type UserRole = 'USER' | 'ADMIN'
export type ShareStatus = 'ACTIVE' | 'CANCELLED' | 'EXPIRED'
export type ConflictPolicy = 'REJECT' | 'RENAME'
export type SortField = 'name' | 'size' | 'createdAt' | 'updatedAt'
export type SortDirection = 'asc' | 'desc'

// ============ 用户与认证 ============
export interface User {
  id: number
  username: string
  email: string
  role: UserRole
  status: UserStatus
  quotaBytes: number
  usedBytes: number
  createdAt: string
  updatedAt: string
}

/** 登录/刷新响应中的精简用户信息（仅3字段） */
export interface UserInfo {
  id: number
  username: string
  role: UserRole
}

export interface TokenData {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  /** 新接口仅返回精简用户信息，完整信息需调用 GET /users/me */
  userInfo: UserInfo
}

export interface StorageUsage {
  quotaBytes: number
  usedBytes: number
  availableBytes: number
  usageRatio: number
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  /** 新接口字段名改为 username（旧接口用 account） */
  username: string
  password: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface UpdateProfileRequest {
  /** 新接口新增可修改 username */
  username?: string
  email?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

// ============ 节点与文件 ============
export interface Node {
  id: number
  parentId: number | null
  type: NodeType
  name: string
  sizeBytes: number
  contentType: string | null
  status: NodeStatus
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface BreadcrumbItem {
  id: number | null
  name: string
}

export interface NodeDetail extends Node {
  checksum: string | null
  breadcrumbs: BreadcrumbItem[]
}

export interface ListNodeParams {
  parentId?: number | null
  keyword?: string
  type?: NodeType
  sort?: SortField
  direction?: SortDirection
  page?: number
  size?: number
}

export interface CreateFolderRequest {
  parentId?: number | null
  name: string
}

export interface RenameNodeRequest {
  name: string
}

export interface MoveNodeRequest {
  targetParentId?: number | null
  conflictPolicy: ConflictPolicy
}

export interface CopyNodesRequest {
  nodeIds: number[]
  targetParentId?: number | null
  conflictPolicy: ConflictPolicy
}

export interface NodeIdsRequest {
  nodeIds: number[]
}

export interface RestoreNodeRequest {
  targetParentId?: number | null
  conflictPolicy?: ConflictPolicy
}

// ============ 分享 ============
export interface Share {
  id: number
  shareCode: string
  shareUrl: string
  title: string
  /** 新接口字段名改为 hasPassword（旧接口用 passwordRequired） */
  hasPassword: boolean
  /** 新接口为 Java Instant 格式 { seconds, nanos } */
  expiresAt: InstantPayload | null
  downloadLimit: number | null
  downloadCount: number
  status: ShareStatus
  nodeIds: number[]
  createdAt: string
}

export interface PublicShare {
  shareCode: string
  shareUrl: string
  title: string
  /** 是否需要访问口令，后端公开分享接口的标准字段。 */
  passwordRequired: boolean
  /** 兼容旧版后端返回的字段名。 */
  hasPassword?: boolean
  /** 新接口为 Java Instant 格式 */
  expiresAt: InstantPayload | null
  downloadLimit: number | null
  downloadCount: number
  status: ShareStatus
  createdAt: string
}

export interface ShareTokenData {
  shareToken: string
  expiresIn: number
}

export interface CreateShareRequest {
  nodeIds: number[]
  title: string
  password?: string | null
  /** 新接口为 Java Instant 格式 */
  expiresAt?: InstantPayload | null
  downloadLimit?: number | null
}

export interface VerifyShareRequest {
  password: string
}

export interface ListSharesParams {
  status?: ShareStatus
  page?: number
  size?: number
}

/** 分享广场列表项（与 PublicShare 结构一致，用于 /public/shares 列表接口） */
export type ShareSquareItem = PublicShare

export interface ListShareSquareParams {
  page?: number
  size?: number
}

// ============ 管理员 ============
export interface UpdateUserStatusRequest {
  status: UserStatus
}

export interface UpdateQuotaRequest {
  quotaBytes: number
}

export interface ListUsersParams {
  keyword?: string
  status?: UserStatus
  page?: number
  size?: number
}
