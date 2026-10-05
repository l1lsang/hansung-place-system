import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../api/admin'
import { useAction } from '../hooks/useAction'
import { Empty, ErrorNotice, Loading, Modal, Pagination } from './ui'
import type { SpaceBlock } from '../types/api'
import { formatRange } from '../utils/time'
export function BlockManager({ spaceId }: { spaceId: number }) {
  const [page, setPage] = useState(0)
  const [start, setStart] = useState(''),
    [end, setEnd] = useState(''),
    [reason, setReason] = useState('')
  const [validation, setValidation] = useState(''),
    [success, setSuccess] = useState('')
  const [release, setRelease] = useState<SpaceBlock | null>(null)
  const action = useAction(),
    releaseAction = useAction()
  const cache = useQueryClient()
  const result = useQuery({
    queryKey: ['admin', 'blocks', spaceId, page],
    queryFn: ({ signal }) => adminApi.blocks(spaceId, page, signal),
  })
  async function refresh() {
    await Promise.all([
      cache.invalidateQueries({ queryKey: ['admin', 'blocks', spaceId] }),
      cache.invalidateQueries({ queryKey: ['availability'] }),
      cache.invalidateQueries({ queryKey: ['seat-availability'] }),
    ])
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    setSuccess('')
    const a = new Date(`${start}+09:00`),
      b = new Date(`${end}+09:00`)
    if (!(a < b) || b.getTime() <= Date.now()) {
      setValidation('종료 시간은 시작 시간과 현재 시간 이후여야 합니다.')
      return
    }
    void action.run(
      () =>
        adminApi.createBlock(spaceId, {
          startTime: a.toISOString(),
          endTime: b.toISOString(),
          reason: reason.trim() || null,
        }),
      async () => {
        await refresh()
        setSuccess('차단 일정을 등록했습니다.')
        setReason('')
      },
    )
  }
  return (
    <div className="stack gap-7">
      <form onSubmit={submit} className="stack">
        <div>
          <h2>차단 시간 등록</h2>
          <p className="muted small mt-2">
            점검·행사 등으로 공간 전체의 예약을 제한합니다. 기존 예약과 겹치면 등록할 수 없습니다.
          </p>
        </div>
        <fieldset disabled={action.pending} className="form-grid">
          <label>
            차단 시작 (KST)
            <input
              type="datetime-local"
              required
              value={start}
              onChange={(e) => setStart(e.target.value)}
            />
          </label>
          <label>
            차단 종료 (KST)
            <input
              type="datetime-local"
              required
              value={end}
              onChange={(e) => setEnd(e.target.value)}
            />
          </label>
          <label className="col-span-full">
            사유
            <input
              maxLength={100}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="예: 시설 정기 점검"
            />
          </label>
        </fieldset>
        <ErrorNotice error={validation ? new Error(validation) : action.error} />
        {success && (
          <p className="notice success" role="status">
            {success}
          </p>
        )}
        <button className="btn primary self-start" disabled={action.pending}>
          {action.pending ? '등록 중…' : '차단 등록'}
        </button>
      </form>
      <hr className="divider" />
      <section>
        <h2 className="mb-5">차단 일정</h2>
        {result.isPending ? (
          <Loading />
        ) : result.isError ? (
          <ErrorNotice error={result.error} retry={() => void result.refetch()} />
        ) : (
          <>
            {!result.data.content.length ? (
              <Empty title="등록된 차단 일정이 없습니다." />
            ) : (
              <ul className="block-list">
                {result.data.content.map((block) => (
                  <li key={block.id}>
                    <div>
                      <strong>{block.reason || '사유 미입력'}</strong>
                      <p className="small muted mt-2">{formatRange(block)}</p>
                    </div>
                    <button className="btn secondary small" onClick={() => setRelease(block)}>
                      차단 해제
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <Pagination page={page} totalPages={result.data.totalPages} onChange={setPage} />
          </>
        )}
      </section>
      {release && (
        <Modal
          title="차단을 해제할까요?"
          busy={releaseAction.pending}
          onClose={() => {
            setRelease(null)
            releaseAction.clearError()
          }}
        >
          <p>{formatRange(release)}</p>
          <ErrorNotice error={releaseAction.error} />
          <div className="modal-actions">
            <button
              className="btn secondary"
              disabled={releaseAction.pending}
              onClick={() => setRelease(null)}
            >
              돌아가기
            </button>
            <button
              className="btn primary"
              disabled={releaseAction.pending}
              onClick={() =>
                void releaseAction.run(
                  () => adminApi.releaseBlock(release.id),
                  async () => {
                    await refresh()
                    setRelease(null)
                    setSuccess('차단 일정을 해제했습니다.')
                  },
                )
              }
            >
              {releaseAction.pending ? '해제 중…' : '차단 해제'}
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
