// ============================================================
// 格式化工具
// ============================================================

// ISO 字符串 / Date → 'YYYY-MM-DD'
export function formatDate(value) {
  if (value == null || value === '') return ''
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value)) {
    return value.slice(0, 10)
  }
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 展示单元格值：date 类型统一格式化
export function formatCell(field, value) {
  if (value == null || value === '') return ''
  if (field?.type === 'date') return formatDate(value)
  return String(value)
}

// 触发浏览器下载
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
