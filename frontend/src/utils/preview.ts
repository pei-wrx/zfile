import type { Node } from '@/types'

export type PreviewType = 'image' | 'text' | 'pdf' | 'video' | 'unsupported'

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico']
const TEXT_EXTENSIONS = ['txt', 'md', 'json', 'xml', 'yaml', 'yml', 'csv', 'log', 'js', 'ts', 'vue', 'py', 'java', 'go', 'html', 'css', 'sql']
const VIDEO_EXTENSIONS = ['mp4', 'm4v', 'webm', 'ogv', 'ogg', 'mov', 'avi', 'mkv']

/** 根据 MIME 类型和文件扩展名判断可用的预览组件。 */
export function detectPreviewType(node: Pick<Node, 'name' | 'contentType'>): PreviewType {
  const mime = (node.contentType || '').split(';', 1)[0].trim().toLowerCase()
  if (mime.startsWith('image/')) return 'image'
  if (mime.startsWith('video/')) return 'video'
  if (mime === 'text/plain' || mime.startsWith('text/') || mime === 'application/json' || mime === 'application/xml') return 'text'
  if (mime === 'application/pdf') return 'pdf'

  const ext = node.name.split('.').pop()?.toLowerCase() || ''
  if (IMAGE_EXTENSIONS.includes(ext)) return 'image'
  if (VIDEO_EXTENSIONS.includes(ext)) return 'video'
  if (TEXT_EXTENSIONS.includes(ext)) return 'text'
  if (ext === 'pdf') return 'pdf'
  return 'unsupported'
}
