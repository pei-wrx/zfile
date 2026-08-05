/**
 * 格式化工具函数
 * 支持 ISO 8601 字符串和后端 java.time.Instant（{seconds, nanos}）两种时间格式
 */
import type { InstantPayload } from '@/types'

/** 时间值：ISO 8601 字符串、Java Instant 对象、或 null/undefined */
type TimeValue = string | InstantPayload | null | undefined

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  if (bytes < 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const unit = units[i] ?? units[units.length - 1]!
  const value = bytes / Math.pow(k, i)
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${unit}`
}

/**
 * 安全解析 ISO 8601 时间字符串。
 * 后端按需求文档以 UTC 存储，但返回的字符串可能省略 'Z' 后缀
 * （如 2026-08-05T03:14:00），此时浏览器会当作本地时间解析，
 * 导致 UTC+8 环境下小时偏移 8 小时。本函数检测无时区标记时补 'Z'。
 */
function parseISODate(dateStr: string): Date {
  if (!dateStr) return new Date(NaN)
  const trimmed = dateStr.trim()
  // 已包含时区标记：Z / z / +08:00 / -0500 → 直接解析
  if (/[zZ]$|[+-]\d{2}:?\d{2}$/.test(trimmed)) {
    return new Date(trimmed)
  }
  // 无时区标记 → 假定为 UTC，补 'Z' 后解析
  return new Date(trimmed + 'Z')
}

/**
 * 将任意时间值转为 Date 对象
 * - ISO 8601 字符串 → parseISODate 处理时区
 * - { seconds, nanos } → 毫秒时间戳（后端 java.time.Instant 格式）
 * - null/undefined → Invalid Date
 */
function toDate(value: TimeValue): Date {
  if (!value) return new Date(NaN)
  if (typeof value === 'string') return parseISODate(value)
  if (typeof value === 'object' && 'seconds' in value) {
    const ms = value.seconds * 1000 + Math.floor((value.nanos ?? 0) / 1_000_000)
    return new Date(ms)
  }
  return new Date(NaN)
}

/**
 * 格式化时间为友好相对格式（支持字符串和 {seconds, nanos}）
 */
export function formatTime(value: TimeValue): string {
  if (!value) return '—'
  const date = toDate(value)
  if (isNaN(date.getTime())) {
    return typeof value === 'string' ? value : '—'
  }
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const oneDay = 86400000
  const pad = (n: number) => String(n).padStart(2, '0')
  const hhmm = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < oneDay) return hhmm
  const y = date.getFullYear()
  const m = pad(date.getMonth() + 1)
  const d = pad(date.getDate())
  if (diff < oneDay * 2) return `昨天 ${hhmm}`
  if (y === now.getFullYear()) return `${m}-${d} ${hhmm}`
  return `${y}-${m}-${d} ${hhmm}`
}

/**
 * 格式化时间为绝对日期格式（用于注册时间等需要精确日期的场景）
 */
export function formatDate(value: TimeValue): string {
  if (!value) return '—'
  const date = toDate(value)
  if (isNaN(date.getTime())) {
    return typeof value === 'string' ? value : '—'
  }
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}`
}

/**
 * 判断时间值是否已过期（null/undefined 视为永不过期）
 */
export function isExpired(value: TimeValue): boolean {
  if (!value) return false
  const date = toDate(value)
  if (isNaN(date.getTime())) return false
  return date.getTime() < Date.now()
}

/**
 * 获取文件图标（基于文件名后缀，返回 Element Plus 图标组件名）
 */
export function getFileIcon(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  const iconMap: Record<string, string> = {
    // 图片
    jpg: 'PictureFilled', jpeg: 'PictureFilled', png: 'PictureFilled',
    gif: 'PictureFilled', webp: 'PictureFilled', svg: 'PictureFilled',
    bmp: 'PictureFilled', ico: 'PictureFilled',
    // 视频
    mp4: 'VideoCameraFilled', avi: 'VideoCameraFilled', mov: 'VideoCameraFilled',
    mkv: 'VideoCameraFilled', flv: 'VideoCameraFilled', wmv: 'VideoCameraFilled',
    // 音频
    mp3: 'Headset', wav: 'Headset', flac: 'Headset', aac: 'Headset',
    ogg: 'Headset', wma: 'Headset',
    // 文档
    pdf: 'Document', doc: 'Document', docx: 'Document',
    xls: 'DataAnalysis', xlsx: 'DataAnalysis',
    ppt: 'Present', pptx: 'Present',
    txt: 'Tickets', md: 'Tickets',
    // 代码
    js: 'Code', ts: 'Code', jsx: 'Code', tsx: 'Code',
    vue: 'Code', py: 'Code', java: 'Code', go: 'Code',
    rs: 'Code', c: 'Code', cpp: 'Code', h: 'Code',
    html: 'Code', css: 'Code', scss: 'Code', less: 'Code',
    json: 'Code', xml: 'Code', yaml: 'Code', yml: 'Code',
    toml: 'Code', sql: 'Code',
    // 压缩包
    zip: 'FolderOpened', rar: 'FolderOpened', '7z': 'FolderOpened',
    tar: 'FolderOpened', gz: 'FolderOpened', bz2: 'FolderOpened',
  }
  return iconMap[ext] || 'Document'
}

/**
 * 获取文件类型颜色
 */
export function getFileColor(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return '#ec4899'
  if (['mp4', 'avi', 'mov', 'mkv', 'flv', 'wmv'].includes(ext)) return '#8b5cf6'
  if (['mp3', 'wav', 'flac', 'aac', 'ogg'].includes(ext)) return '#10b981'
  if (['pdf'].includes(ext)) return '#ef4444'
  if (['doc', 'docx'].includes(ext)) return '#3b82f6'
  if (['xls', 'xlsx'].includes(ext)) return '#22c55e'
  if (['ppt', 'pptx'].includes(ext)) return '#f97316'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '#f59e0b'
  if (['js', 'ts', 'jsx', 'tsx', 'vue', 'py', 'java', 'go', 'html', 'css', 'json'].includes(ext)) return '#6366f1'
  return '#94a3b8'
}

type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

/**
 * 获取分享状态标签
 */
export function getShareStatusTag(status: string): { type: TagType; text: string } {
  const map: Record<string, { type: TagType; text: string }> = {
    ACTIVE: { type: 'success', text: '有效' },
    CANCELLED: { type: 'info', text: '已取消' },
    EXPIRED: { type: 'warning', text: '已过期' },
  }
  return map[status] || { type: 'info', text: status }
}

/**
 * 获取用户状态标签
 */
export function getUserStatusTag(status: string): { type: TagType; text: string } {
  const map: Record<string, { type: TagType; text: string }> = {
    ACTIVE: { type: 'success', text: '正常' },
    DISABLED: { type: 'danger', text: '已禁用' },
  }
  return map[status] || { type: 'info', text: status }
}

/**
 * 获取节点状态标签
 */
export function getNodeStatusTag(status: string): { type: TagType; text: string } {
  const map: Record<string, { type: TagType; text: string }> = {
    ACTIVE: { type: 'success', text: '正常' },
    TRASHED: { type: 'warning', text: '回收站' },
  }
  return map[status] || { type: 'info', text: status }
}
