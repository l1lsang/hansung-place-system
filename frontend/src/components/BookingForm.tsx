import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { reservationsApi } from '../api/reservations'
import { spacesApi } from '../api/spaces'
import { useAuth } from '../hooks/authContext'
import { useAction } from '../hooks/useAction'
import type { Member, Space } from '../types/api'
import { covers, formatDate, formatTime } from '../utils/time'
import { capacity } from '../utils/labels'
import { ErrorNotice, Modal } from './ui'

export function BookingForm({
  space,
  seatId,
  seatLabel,
  hasSeats,
  start,
  end,
  available,
  checking,
  purposeRequired = false,
}: {
  space: Space
  seatId?: number
  seatLabel?: string
  hasSeats: boolean
  start: string
  end: string
  available: boolean
  checking: boolean
  purposeRequired?: boolean
}) {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const action = useAction()
  const [members, setMembers] = useState<(Member & { key: number })[]>([])
  const [purpose, setPurpose] = useState('')
  const [validation, setValidation] = useState('')
  const [confirm, setConfirm] = useState(false)
  const total = members.length + 1
  function review(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    if (Date.parse(start) <= Date.now()) {
      setValidation('현재 시간 이후로 예약해주세요.')
      return
    }
    if (!available) {
      setValidation('선택한 시간을 다시 확인해주세요.')
      return
    }
    if (
      !hasSeats &&
      ((space.minCapacity && total < space.minCapacity) ||
        (space.maxCapacity && total > space.maxCapacity))
    ) {
      setValidation(`예약자를 포함해 ${capacity(space)} 조건에 맞게 입력해주세요.`)
      return
    }
    const ids = [auth.user?.studentId, ...members.map((m) => m.studentId.trim())]
    if (new Set(ids).size !== ids.length) {
      setValidation('예약자 또는 참여자 학번이 중복됩니다.')
      return
    }
    setConfirm(true)
  }
  async function book() {
    await action.run(
      async () => {
        const fresh = await spacesApi.availability(space.id, start, end, seatId)
        if (!covers(fresh.available, start, end))
          throw new Error('선택한 시간이 변경되었습니다. 다른 시간을 선택해주세요.')
        return reservationsApi.create({
          spaceId: space.id,
          seatId: hasSeats ? seatId! : null,
          startTime: start,
          endTime: end,
          kind: hasSeats ? 'SEAT_USE' : 'BOOKING',
          purpose: purpose.trim() || null,
          members: hasSeats
            ? []
            : members.map(({ studentId, name }) => ({
                studentId: studentId.trim(),
                name: name.trim(),
              })),
        })
      },
      async (result) => {
        await cache.invalidateQueries({ queryKey: ['reservations'] })
        navigate(`/reservations/${result.id}`, { state: { created: true } })
      },
    )
    void cache.invalidateQueries({ queryKey: ['availability'] })
    void cache.invalidateQueries({ queryKey: ['seat-availability'] })
  }
  return (
    <aside className="panel booking-summary" id="booking-summary" tabIndex={-1}>
      <p className="eyebrow">YOUR RESERVATION</p>
      <h2>예약 내용 확인</h2>
      <dl className="summary-list">
        <div>
          <dt>공간</dt>
          <dd>{space.name}</dd>
        </div>
        {hasSeats && (
          <div>
            <dt>좌석</dt>
            <dd>{seatId ? `${seatLabel ?? seatId}번` : '좌석을 선택해주세요'}</dd>
          </div>
        )}
        <div>
          <dt>날짜</dt>
          <dd>{formatDate(start)}</dd>
        </div>
        <div>
          <dt>시간</dt>
          <dd>
            {formatTime(start)} ~ {end.endsWith('T15:00:00.000Z') ? '24:00' : formatTime(end)}
          </dd>
        </div>
      </dl>
      {auth.user ? (
        <form onSubmit={review} className="stack">
          <p className="small muted">
            예약자: {auth.user.name} ({auth.user.studentId})
          </p>
          {!hasSeats && (
            <fieldset>
              <legend className="mb-2 font-semibold">
                참여자 <span className="muted font-normal">총 {total}명 · 예약자 포함</span>
              </legend>
              <p className="small muted mb-3">예약자 본인을 제외한 참여자만 입력해주세요.</p>
              {members.map((member, index) => (
                <div className="member-row" key={member.key}>
                  <label>
                    <span className="sr-only">참여자 {index + 1} 학번</span>
                    <input
                      placeholder="학번"
                      required
                      maxLength={20}
                      value={member.studentId}
                      onChange={(e) =>
                        setMembers((list) =>
                          list.map((m) =>
                            m.key === member.key ? { ...m, studentId: e.target.value } : m,
                          ),
                        )
                      }
                    />
                  </label>
                  <label>
                    <span className="sr-only">참여자 {index + 1} 이름</span>
                    <input
                      placeholder="이름"
                      required
                      maxLength={50}
                      value={member.name}
                      onChange={(e) =>
                        setMembers((list) =>
                          list.map((m) =>
                            m.key === member.key ? { ...m, name: e.target.value } : m,
                          ),
                        )
                      }
                    />
                  </label>
                  <button
                    type="button"
                    className="icon-button"
                    aria-label={`참여자 ${index + 1} 삭제`}
                    onClick={() => setMembers((list) => list.filter((m) => m.key !== member.key))}
                  >
                    <Trash2 size={17} />
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="btn secondary small w-full"
                disabled={members.length >= Math.min(100, (space.maxCapacity ?? 101) - 1)}
                onClick={() =>
                  setMembers((list) => [...list, { key: Date.now(), studentId: '', name: '' }])
                }
              >
                <Plus size={15} />
                참여자 추가
              </button>
            </fieldset>
          )}
          <label>
            이용 목적{' '}
            <span className="muted font-normal">({purposeRequired ? '필수' : '선택'})</span>
            <input
              maxLength={100}
              required={purposeRequired}
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              placeholder="예: 그룹 과제, 개인 학습"
            />
          </label>
          <ErrorNotice error={validation ? new Error(validation) : null} />
          <button
            className="btn primary w-full"
            disabled={!available || checking || !space.bookingEnabled || (hasSeats && !seatId)}
          >
            {checking ? '예약 가능 시간 확인 중…' : '예약 내용 확인'}
          </button>
        </form>
      ) : (
        <>
          <ErrorNotice error={auth.error} retry={auth.refresh} />
          <Link
            className="btn primary w-full"
            to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
          >
            로그인하고 예약하기
          </Link>
        </>
      )}
      <p className="small muted mt-4">예약 가능 여부는 신청 시 다시 확인합니다.</p>
      {confirm && (
        <Modal
          title="이 내용으로 예약할까요?"
          busy={action.pending}
          onClose={() => {
            setConfirm(false)
            action.clearError()
          }}
        >
          <p className="font-semibold">
            {space.name}
            {seatId ? ` · ${seatLabel ?? seatId}번 좌석` : ''}
          </p>
          <p className="muted mt-2">
            {formatDate(start)}
            <br />
            {formatTime(start)} ~ {formatTime(end)} · 총 {hasSeats ? 1 : total}명
          </p>
          <ErrorNotice error={action.error} />
          <div className="modal-actions">
            <button
              className="btn secondary"
              disabled={action.pending}
              onClick={() => setConfirm(false)}
            >
              돌아가기
            </button>
            <button className="btn primary" disabled={action.pending} onClick={() => void book()}>
              {action.pending ? '예약 중…' : '예약 확정'}
            </button>
          </div>
        </Modal>
      )}
    </aside>
  )
}
