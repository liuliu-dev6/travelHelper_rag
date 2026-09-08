import axios from 'axios'
import type { AxiosError, AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { SseEvent, StreamHandlers } from '@/types/chat'

export interface ApiResult<T> {
  success: boolean
  code: number
  message?: string
  data: T
}

let csrfToken: string | null = null
let csrfPromise: Promise<string> | null = null
const csrfExemptPaths = new Set([
  '/api/auth/register',
  '/api/auth/login',
  '/api/auth/password'
])

export async function ensureCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken
  if (!csrfPromise) {
    csrfPromise = fetch('/api/auth/csrf', { credentials: 'same-origin' })
      .then(async response => {
        if (!response.ok) throw new Error('无法初始化安全令牌')
        const result = await response.json() as ApiResult<{ token: string }>
        csrfToken = result.data.token
        return csrfToken
      })
      .finally(() => { csrfPromise = null })
  }
  return csrfPromise
}

export function resetCsrfToken() {
  csrfToken = null
}

const service: AxiosInstance = axios.create({
  baseURL: '',
  timeout: 120000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

service.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const method = (config.method || 'get').toLowerCase()
  const csrfExempt = csrfExemptPaths.has(config.url || '')
  if (!csrfExempt && !['get', 'head', 'options'].includes(method)) {
    config.headers['X-XSRF-TOKEN'] = await ensureCsrfToken()
  }
  return config
})

service.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  async (error: AxiosError<any>) => {
    const config = error.config as (InternalAxiosRequestConfig & { _csrfRetried?: boolean }) | undefined
    const method = (config?.method || 'get').toLowerCase()
    const csrfExempt = csrfExemptPaths.has(config?.url || '')
    if (error.response?.status === 403 && config && !csrfExempt && !config._csrfRetried && !['get', 'head', 'options'].includes(method)) {
      config._csrfRetried = true
      resetCsrfToken()
      config.headers['X-XSRF-TOKEN'] = await ensureCsrfToken()
      return service.request(config)
    }
    const message = error.response?.data?.message || error.response?.data?.detail || error.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

export function fetchStream(url: string, data: unknown, handlers: StreamHandlers): () => void {
  const controller = new AbortController()

  const dispatchLine = (line: string) => {
    const trimmed = line.trim()
    if (!trimmed.startsWith('data:')) return
    const raw = trimmed.substring(5).trim()
    if (!raw) return
    try {
      const event = JSON.parse(raw) as SseEvent
      handlers.onEvent?.(event)
      if (event.type === 'error') handlers.onError?.(event.error)
    } catch (error) {
      console.error('SSE JSON 解析失败:', error)
    }
  }

  void (async () => {
    try {
      const token = await ensureCsrfToken()
      const response = await fetch(`/api/travel/${url}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
        body: JSON.stringify(data),
        credentials: 'same-origin',
        signal: controller.signal
      })
      if (!response.ok) {
        const payload = await response.json().catch(() => null)
        throw new Error(payload?.message || `请求失败 (${response.status})`)
      }
      if (!response.body) throw new Error('浏览器不支持流式响应')

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() || ''
        lines.forEach(dispatchLine)
      }
      buffer += decoder.decode()
      if (buffer.trim()) dispatchLine(buffer)
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      handlers.onError?.(error instanceof Error ? error.message : '流读取异常')
    }
  })()

  return () => controller.abort()
}

export default service
