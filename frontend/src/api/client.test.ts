import { beforeEach, describe, expect, it, vi } from 'vitest'
import { onUnauthorized, request, resetCsrf } from './client'
const response = (data: unknown, status = 200) =>
  new Response(status === 204 ? null : JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
describe('session API client', () => {
  beforeEach(resetCsrf)
  it('sends cookies and obtains a CSRF token before writing JSON', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'one' }))
      .mockResolvedValueOnce(response({ id: 1 }, 201))
    vi.stubGlobal('fetch', fetch)
    await request('/api/reservations', { method: 'POST', body: { spaceId: 1 } })
    expect(fetch.mock.calls[1][1]).toMatchObject({
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'one', 'Content-Type': 'application/json' },
      body: '{"spaceId":1}',
    })
  })
  it('refreshes CSRF and retries only once when the security filter rejects it', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'one' }))
      .mockResolvedValueOnce(response({ code: 'CSRF_INVALID' }, 403))
      .mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'two' }))
      .mockResolvedValueOnce(response({ code: 'CSRF_INVALID' }, 403))
    vi.stubGlobal('fetch', fetch)
    await expect(request('/api/reservations/1', { method: 'DELETE' })).rejects.toMatchObject({
      status: 403,
    })
    expect(fetch).toHaveBeenCalledTimes(4)
  })
  it.each([400, 403, 404, 409, 500])(
    'preserves error DTO for %s without replaying mutations',
    async (status) => {
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'one' }))
        .mockResolvedValueOnce(response({ code: 'DOMAIN_ERROR', message: '서버의 안내' }, status))
      vi.stubGlobal('fetch', fetch)
      await expect(
        request('/api/reservations', { method: 'POST', body: {} }),
      ).rejects.toMatchObject({ status, code: 'DOMAIN_ERROR', message: '서버의 안내' })
      expect(fetch).toHaveBeenCalledTimes(2)
    },
  )
  it('clears auth on protected 401, but not an incorrect login password', async () => {
    const listener = vi.fn(),
      unsubscribe = onUnauthorized(listener)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async () => response({ code: 'UNAUTHENTICATED' }, 401)),
    )
    await expect(request('/api/reservations')).rejects.toMatchObject({ status: 401 })
    await expect(request('/api/auth/me', { anonymous: true })).rejects.toMatchObject({
      status: 401,
    })
    expect(listener).toHaveBeenCalledTimes(1)
    unsubscribe()
  })
  it('supports 204 and does not replay uncertain network writes', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(response({ headerName: 'X-XSRF-TOKEN', token: 'one' }))
      .mockResolvedValueOnce(response(null, 204))
      .mockRejectedValueOnce(new TypeError('offline'))
    vi.stubGlobal('fetch', fetch)
    await expect(request('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
    await expect(request('/api/reservations', { method: 'POST', body: {} })).rejects.toMatchObject({
      code: 'NETWORK_ERROR',
    })
    expect(fetch).toHaveBeenCalledTimes(3)
  })
})
