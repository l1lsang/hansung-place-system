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
import { useNow } from '../hooks/useNow'
export default function ReservationDetailPage({ admin = false }: { admin?: boolean }) {
  const { reservationId } = useParams()
  const id = Number(reservationId)
  const auth = useAuth()
  const location = useLocation()
  const cache = useQueryClient()
  const action = useAction()
  const [confirm, setConfirm] = useState(false)
  const [reason, setReason] = useState('')
  const now = useNow()
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
  const state = reservationState(r, now)
  const activeSeat = r.kind === 'SEAT_USE' && state === '이용 중'
  const actionable = state === '예정' || state === '이용 중'
  const actionLabel = admin
    ? activeSeat
      ? '좌석 강제 종료'
      : '예약 강제 취소'
    : activeSeat
      ? '좌석 반납'
      : '예약 취소'
  return (
    <>
      <Link className="back-link" to={admin ? '/admin/reservations' : '/reservations'}>
        <ArrowLeft size={16} />
        예약 목록
      </Link>
      <PageHeading
        eyebrow={`RESERVATION #${r.id}`}
        title="예약 상세"
        action={<span className={`badge ${actionable ? 'blue' : ''}`}>{state}</span>}
      />
      {location.state?.created && actionable && (
        <div className="notice success mb-6" role="status">
          <CheckCircle2 size={21} />
          <span>
            {activeSeat
              ? '좌석 이용이 시작되었습니다. 퇴실할 때 좌석을 반납해주세요.'
              : '예약이 완료되었습니다. 이용 시간을 확인해주세요.'}
          </span>
        </div>
      )}
      <section className="panel max-w-3xl">
        <h2>{space.data?.name ?? `공간 #${r.spaceId}`}</h2>
        <dl className="summary-list">
          {r.owner && (
            <div>
              <dt>예약자</dt>
              <dd>
                {r.owner.name} · {r.owner.studentId}
                <br />
                <span className="small muted">{r.owner.email}</span>
              </dd>
            </div>
          )}
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
          {r.endedAt && (
            <div>
              <dt>실제 이용 종료</dt>
              <dd>{formatRange({ startTime: r.startTime, endTime: r.endedAt })}</dd>
            </div>
          )}
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
        {actionable && (
          <button className="btn danger mt-5" onClick={() => setConfirm(true)}>
            {actionLabel}
          </button>
        )}
        {Boolean(r.actions?.length) && (
          <div className="mt-5">
            <h3>처리 이력</h3>
            <ul className="action-history">
              {r.actions?.map((item) => (
                <li key={item.id}>
                  <strong>
                    {
                      {
                        CANCEL: '본인 예약 취소',
                        RETURN_SEAT: '좌석 반납',
                        ADMIN_CANCEL: '관리자 예약 취소',
                        ADMIN_END: '관리자 좌석 종료',
                      }[item.action]
                    }
                  </strong>
                  <span className="small muted">
                    {
                      formatRange({ startTime: item.createdAt, endTime: item.createdAt }).split(
                        ' ~ ',
                      )[0]
                    }
                  </span>
                  {item.reason && <p>사유: {item.reason}</p>}
                </li>
              ))}
            </ul>
          </div>
        )}
        <Link className="text-link mt-5 block" to={`/spaces/${r.spaceId}`}>
          공간 상세 보기 →
        </Link>
      </section>
      {confirm && (
        <Modal
          title={`${actionLabel}할까요?`}
          busy={action.pending}
          onClose={() => {
            setConfirm(false)
            action.clearError()
          }}
        >
          <p>
            {activeSeat
              ? '지금 좌석 이용을 종료하고 남은 시간을 다른 사람이 이용할 수 있도록 반납합니다.'
              : '취소한 예약은 되돌릴 수 없습니다. 다시 이용하려면 새로 예약해주세요.'}
          </p>
          {admin && (
            <label className="mt-4">
              처리 사유
              <textarea
                required
                maxLength={200}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="예: 시설 점검으로 인한 예약 취소"
              />
            </label>
          )}
          <ErrorNotice error={action.error} />
          <div className="modal-actions">
            <button
              className="btn secondary"
              disabled={action.pending}
              onClick={() => setConfirm(false)}
            >
              돌아가기
            </button>
            <button
              className="btn danger"
              disabled={action.pending || (admin && !reason.trim())}
              onClick={() =>
                void action.run(
                  async () => {
                    if (admin) await adminApi.cancelReservation(id, reason.trim())
                    else if (activeSeat) await reservationsApi.returnSeat(id)
                    else await reservationsApi.cancel(id)
                  },
                  async () => {
                    await Promise.all([
                      cache.invalidateQueries({ queryKey: ['reservation'] }),
                      cache.invalidateQueries({ queryKey: ['reservations'] }),
                      cache.invalidateQueries({ queryKey: ['availability'] }),
                      cache.invalidateQueries({ queryKey: ['seat-availability'] }),
                      cache.invalidateQueries({ queryKey: ['admin'] }),
                    ])
                    setConfirm(false)
                  },
                )
              }
            >
              {action.pending ? '처리 중…' : activeSeat ? '반납·종료 확정' : '취소 확정'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
