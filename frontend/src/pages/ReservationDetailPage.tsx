import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2 } from 'lucide-react'
import { adminApi } from '../api/admin'
import { reservationsApi } from '../api/reservations'
import { spacesApi } from '../api/spaces'
import { useAuth } from '../hooks/authContext'
import { useAction } from '../hooks/useAction'
import { Empty, ErrorNotice, Loading, Modal, PageHeading } from '../components/ui'
import { formatRange, reservationState } from '../utils/time'
export default function ReservationDetailPage({ admin = false }: { admin?: boolean }) {
  const { reservationId } = useParams()
  const id = Number(reservationId)
  const auth = useAuth()
  const location = useLocation()
  const cache = useQueryClient()
  const action = useAction()
  const [confirm, setConfirm] = useState(false)
  const valid = Number.isSafeInteger(id) && id > 0
  const result = useQuery({
    queryKey: [admin ? 'admin' : 'reservation', 'detail', auth.user?.id, id],
    enabled: valid,
    queryFn: ({ signal }) =>
      admin ? adminApi.reservation(id, signal) : reservationsApi.get(id, signal),
  })
  const space = useQuery({
    queryKey: ['space', result.data?.spaceId],
    enabled: Boolean(result.data),
    queryFn: ({ signal }) => spacesApi.get(result.data!.spaceId, signal),
  })
  if (!valid) return <Empty title="올바르지 않은 예약 주소입니다." />
  if (result.isPending) return <Loading />
  if (result.isError)
    return <ErrorNotice error={result.error} retry={() => void result.refetch()} />
  const r = result.data
  const state = reservationState(r)
  return (
    <>
      <Link className="back-link" to={admin ? '/admin/reservations' : '/reservations'}>
        <ArrowLeft size={16} />
        예약 목록
      </Link>
      <PageHeading
        eyebrow={`RESERVATION #${r.id}`}
        title="예약 상세"
        action={<span className={`badge ${state === '예정' ? 'blue' : ''}`}>{state}</span>}
      />
      {location.state?.created && state === '예정' && (
        <div className="notice success mb-6" role="status">
          <CheckCircle2 size={21} />
          <span>예약이 완료되었습니다. 이용 시간을 확인해주세요.</span>
        </div>
      )}
      <section className="panel max-w-3xl">
        <h2>{space.data?.name ?? `공간 #${r.spaceId}`}</h2>
        <dl className="summary-list">
          <div>
            <dt>예약 시간</dt>
            <dd>{formatRange(r)}</dd>
          </div>
          <div>
            <dt>이용 유형</dt>
            <dd>{r.kind === 'SEAT_USE' ? '좌석 이용' : '공간 예약'}</dd>
          </div>
          {r.seatId && (
            <div>
              <dt>좌석 식별번호</dt>
              <dd>#{r.seatId}</dd>
            </div>
          )}
          <div>
            <dt>이용 인원</dt>
            <dd>{1 + r.members.length}명 (예약자 포함)</dd>
          </div>
          <div>
            <dt>이용 목적</dt>
            <dd>{r.purpose || '입력하지 않음'}</dd>
          </div>
        </dl>
        {r.members.length > 0 && (
          <>
            <h3 className="mt-5">참여자</h3>
            <ul className="member-list">
              {r.members.map((m) => (
                <li key={m.studentId}>
                  <span>{m.name}</span>
                  <span className="muted">{m.studentId}</span>
                </li>
              ))}
            </ul>
          </>
        )}
        {!admin && state === '예정' && (
          <button className="btn danger mt-5" onClick={() => setConfirm(true)}>
            예약 취소
          </button>
        )}
        <Link className="text-link mt-5 block" to={`/spaces/${r.spaceId}`}>
          공간 상세 보기 →
        </Link>
      </section>
      {confirm && (
        <Modal
          title="예약을 취소할까요?"
          busy={action.pending}
          onClose={() => {
            setConfirm(false)
            action.clearError()
          }}
        >
          <p>취소한 예약은 되돌릴 수 없습니다. 다시 이용하려면 새로 예약해주세요.</p>
          <ErrorNotice error={action.error} />
          <div className="modal-actions">
            <button
              className="btn secondary"
              disabled={action.pending}
              onClick={() => setConfirm(false)}
            >
              예약 유지
            </button>
            <button
              className="btn danger"
              disabled={action.pending}
              onClick={() =>
                void action.run(
                  () => reservationsApi.cancel(id),
                  async () => {
                    await Promise.all([
                      cache.invalidateQueries({ queryKey: ['reservation'] }),
                      cache.invalidateQueries({ queryKey: ['reservations'] }),
                      cache.invalidateQueries({ queryKey: ['availability'] }),
                      cache.invalidateQueries({ queryKey: ['seat-availability'] }),
                    ])
                    setConfirm(false)
                  },
                )
              }
            >
              {action.pending ? '취소 중…' : '취소 확정'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
