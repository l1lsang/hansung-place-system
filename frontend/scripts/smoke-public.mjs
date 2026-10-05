// Read-only real API verification. Does not create users, reservations, or policies.
import assert from 'node:assert/strict'
const base = (process.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '')
async function get(path) {
  const response = await fetch(base + path, { signal: AbortSignal.timeout(15_000) })
  assert.equal(response.status, 200, path)
  const data = await response.json()
  console.log(`PASS GET ${path.split('?')[0]} (200)`)
  return data
}
const spaces = await get('/api/spaces?size=1')
assert.ok(Array.isArray(spaces.content))
if (spaces.content.length) {
  const id = spaces.content[0].id
  const detail = await get(`/api/spaces/${id}`)
  assert.equal(detail.id, id)
  const seats = await get(`/api/spaces/${id}/seats?size=1`)
  const policy = await get(`/api/spaces/${id}/policy`)
  assert.equal(policy.timeZone, 'Asia/Seoul')
  const startTime = new Date(Date.now() + 86400_000).toISOString()
  const endTime = new Date(Date.parse(startTime) + 3600_000).toISOString()
  const params = new URLSearchParams({ startTime, endTime })
  if (seats.content.length) params.set('seatId', seats.content[0].id)
  const availability = await get(`/api/spaces/${id}/availability?${params}`)
  assert.ok(Array.isArray(availability.available))
  console.log(
    `Public data: spaces=${spaces.totalElements}, seats=${seats.totalElements}, policyConfigured=${policy.configured}`,
  )
} else {
  console.log('SKIP detail/seat/availability: no spaces available')
}
const denied = await fetch(base + '/api/admin/spaces')
assert.equal(denied.status, 401)
console.log('PASS anonymous admin access rejected (401)')
