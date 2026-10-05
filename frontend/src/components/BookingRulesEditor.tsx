import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../api/admin'
import { useAction } from '../hooks/useAction'
import type { BookingRules, BookingRulesInput } from '../types/api'
import { ErrorNotice } from './ui'

export function BookingRulesEditor({ rules }: { rules: BookingRules }) {
  const [form, setForm] = useState<BookingRulesInput>(() => ({
    enabled: rules.enabled,
    slotMinutes: rules.slotMinutes,
    minDurationMinutes: rules.minDurationMinutes,
    maxDurationMinutes: rules.maxDurationMinutes,
    advanceDays: rules.advanceDays,
    dailyMaxMinutes: rules.dailyMaxMinutes,
    usageScope: rules.usageScope,
    preventAdjacent: rules.preventAdjacent,
    purposeRequired: rules.purposeRequired,
    instantUseMinutes: rules.instantUseMinutes,
  }))
  const [saved, setSaved] = useState(false)
  const [validation, setValidation] = useState('')
  const action = useAction()
  const cache = useQueryClient()
  function change(update: Partial<BookingRulesInput>) {
    setSaved(false)
    setForm((current) => ({ ...current, ...update }))
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    setSaved(false)
    if (
      form.minDurationMinutes > form.maxDurationMinutes ||
      form.minDurationMinutes % form.slotMinutes ||
      form.maxDurationMinutes % form.slotMinutes ||
      (form.dailyMaxMinutes !== null && form.dailyMaxMinutes < form.minDurationMinutes)
    ) {
      setValidation(
        '최소·최대 시간은 예약 단위의 배수이며, 일일 한도는 최소 시간 이상이어야 합니다.',
      )
      return
    }
    void action.run(
      () => adminApi.saveBookingRules(rules.spaceId, form),
      async () => {
        await Promise.all(
          ['booking-rules', 'seat-availability', 'availability'].map((key) =>
            cache.invalidateQueries({ queryKey: [key] }),
          ),
        )
        setSaved(true)
      },
    )
  }
  return (
    <form className="stack" onSubmit={submit}>
      <div>
        <h2>예약 제한 · 즉시 이용시간</h2>
        <p className="small muted mt-2">
          관리자가 저장한 설정은 새 신청에 적용됩니다. 이미 저장된 예약은 유지됩니다.
        </p>
      </div>
      {!rules.configured && (
        <p className="notice">
          현재는 예약 제한이 적용되지 않습니다. 참고 프로젝트의 기준을 불러온 뒤 시설 운영 방침에
          맞춰 저장하세요.
        </p>
      )}
      <fieldset disabled={action.pending} className="stack">
        <button
          type="button"
          className="btn secondary self-start"
          onClick={() =>
            change({
              enabled: true,
              slotMinutes: 30,
              minDurationMinutes: 30,
              maxDurationMinutes: 180,
              advanceDays: 7,
              dailyMaxMinutes: 180,
              usageScope: 'VENUE',
              preventAdjacent: true,
              purposeRequired: true,
              instantUseMinutes: 180,
            })
          }
        >
          참고 프로젝트 그룹스터디실 기준 불러오기
        </button>
        <label className="check-label">
          <input
            type="checkbox"
            checked={form.enabled}
            onChange={(e) => change({ enabled: e.target.checked })}
          />
          시간 예약에 제한 규칙 적용
        </label>
        <div className="form-grid">
          <label>
            예약 단위
            <select
              value={form.slotMinutes}
              onChange={(e) => change({ slotMinutes: Number(e.target.value) })}
            >
              {[15, 30, 60].map((value) => (
                <option key={value} value={value}>
                  {value}분
                </option>
              ))}
            </select>
          </label>
          <label>
            예약 가능 일수 (오늘부터)
            <input
              type="number"
              required
              min={0}
              max={365}
              value={form.advanceDays}
              onChange={(e) => change({ advanceDays: Number(e.target.value) })}
            />
          </label>
          <label>
            최소 예약 시간 (분)
            <input
              type="number"
              required
              min={1}
              max={1440}
              value={form.minDurationMinutes}
              onChange={(e) => change({ minDurationMinutes: Number(e.target.value) })}
            />
          </label>
          <label>
            최대 예약 시간 (분)
            <input
              type="number"
              required
              min={1}
              max={1440}
              value={form.maxDurationMinutes}
              onChange={(e) => change({ maxDurationMinutes: Number(e.target.value) })}
            />
          </label>
          <label>
            하루 합계 한도 (분, 비우면 제한 없음)
            <input
              type="number"
              min={1}
              max={1440}
              value={form.dailyMaxMinutes ?? ''}
              onChange={(e) =>
                change({ dailyMaxMinutes: e.target.value ? Number(e.target.value) : null })
              }
            />
          </label>
          <label>
            합계·연속 예약 검사 범위
            <select
              value={form.usageScope}
              onChange={(e) => change({ usageScope: e.target.value as 'SPACE' | 'VENUE' })}
            >
              <option value="SPACE">이 공간</option>
              <option value="VENUE">같은 시설(venue)의 모든 공간</option>
            </select>
          </label>
        </div>
        <label className="check-label">
          <input
            type="checkbox"
            checked={form.preventAdjacent}
            onChange={(e) => change({ preventAdjacent: e.target.checked })}
          />
          동일 신청자의 중복·연속 예약 제한
        </label>
        <label className="check-label">
          <input
            type="checkbox"
            checked={form.purposeRequired}
            onChange={(e) => change({ purposeRequired: e.target.checked })}
          />
          이용 목적 필수 입력
        </label>
        <label>
          좌석 즉시 이용 기본 시간 (분)
          <input
            type="number"
            required
            min={1}
            max={1440}
            value={form.instantUseMinutes}
            onChange={(e) => change({ instantUseMinutes: Number(e.target.value) })}
          />
        </label>
        <p className="small muted">
          즉시 이용시간은 제한 규칙의 활성화 여부와 관계없이 적용됩니다. 즉시 이용은 운영시간·점유
          여부·1인 1좌석을 검사하며 위 시간 예약의 단위·일일 한도와 구분됩니다.
        </p>
        <ErrorNotice error={validation ? new Error(validation) : action.error} />
        {saved && (
          <p role="status" className="notice success">
            예약 규칙을 저장했습니다.
          </p>
        )}
        <button className="btn primary self-start" disabled={action.pending}>
          {action.pending ? '저장 중…' : '예약 규칙 저장'}
        </button>
      </fieldset>
    </form>
  )
}
