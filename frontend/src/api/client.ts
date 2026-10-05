const baseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '')
export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}
type Csrf = { headerName: string; token: string }
let csrf: Promise<Csrf> | undefined
const unauthorizedListeners = new Set<() => void>()
export function onUnauthorized(listener: () => void) {
  unauthorizedListeners.add(listener)
  return () => {
    unauthorizedListeners.delete(listener)
  }
}
export function resetCsrf() {
  csrf = undefined
}
const fallback: Record<number, string> = {
  400: '입력한 값을 확인해주세요.',
  401: '로그인이 필요합니다.',
  403: '접근 권한이 없습니다.',
  404: '요청한 정보를 찾을 수 없습니다.',
  409: '다른 요청과 충돌했습니다. 최신 정보를 확인해주세요.',
  500: '서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요.',
}
interface Options {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  anonymous?: boolean
}
export async function request<T>(
  path: string,
  options: Options = {},
  csrfRetry = true,
): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    csrf ??= request<Csrf>('/api/auth/csrf', { anonymous: true }).catch((error) => {
      resetCsrf()
      throw error
    })
    const token = await csrf
    headers[token.headerName] = token.token
  }
  let response: Response
  try {
    response = await fetch(baseUrl + path, {
      method,
      headers,
      credentials: 'include',
      signal: options.signal,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      method === 'GET'
        ? '서버에 연결할 수 없습니다. 네트워크 연결을 확인하고 다시 시도해주세요.'
        : '응답을 받지 못했습니다. 요청이 처리되었을 수 있으니 목록을 확인한 뒤 다시 시도해주세요.',
    )
  }
  const text = await response.text()
  let body: unknown
  try {
    body = text ? JSON.parse(text) : undefined
  } catch {
    body = undefined
  }
  if (!response.ok) {
    const data = body as { code?: unknown; message?: unknown } | undefined
    const code = typeof data?.code === 'string' ? data.code : 'HTTP_ERROR'
    if (response.status === 403 && code === 'CSRF_INVALID' && csrfRetry && method !== 'GET') {
      resetCsrf()
      return request<T>(path, options, false) // rejected before controller; one safe retry only
    }
    if (response.status === 401 && !options.anonymous) {
      resetCsrf()
      unauthorizedListeners.forEach((listener) => listener())
    }
    throw new ApiError(
      response.status,
      code,
      typeof data?.message === 'string'
        ? data.message
        : (fallback[response.status] ?? '요청을 처리하지 못했습니다.'),
    )
  }
  if (response.status !== 204 && body === undefined)
    throw new ApiError(502, 'INVALID_RESPONSE', '서버 응답을 읽을 수 없습니다.')
  return body as T
}
export function query(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  return `?${search.toString()}`
}
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '요청을 처리하지 못했습니다.'
}
