import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../api/admin'
import { useAction } from '../hooks/useAction'
import { weekdays, type OperatingPolicy, type Period, type PolicyInput } from '../types/api'
import { ErrorNotice } from './ui'
const periods: { value: Period; label: string }[] = [
  { value: 'SEMESTER', label: '학기' },
  { value: 'VACATION', label: '방학' },
  { value: 'EXAM', label: '시험기간' },
]
const days = ['월요일', '화요일', '수요일', '목요일', '금요일', '토요일', '일요일']
export function PolicyEditor({ policy, facility }: { policy: OperatingPolicy; facility?: string }) {
  const [form, setForm] = useState<PolicyInput>(() => ({
    enabled: policy.enabled,
    academicPeriod: policy.academicPeriod ?? 'SEMESTER',
    examStartDate: policy.examStartDate,
    examEndDate: policy.examEndDate,
    hours: policy.configured
      ? policy.hours
      : periods.flatMap((p) =>
          weekdays.map((dayOfWeek) => ({
            period: p.value,
            dayOfWeek,
            closed: true,
            openTime: null,
            closeTime: null,
          })),
        ),
  }))
  const [tab, setTab] = useState<Period>('SEMESTER')
  const [validation, setValidation] = useState('')
  const [saved, setSaved] = useState(false)
  const action = useAction()
  const cache = useQueryClient()
  function change(update: Partial<PolicyInput>) {
    setSaved(false)
    setForm((current) => ({ ...current, ...update }))
  }
  function hour(index: number, update: Partial<PolicyInput['hours'][number]>) {
    setSaved(false)
    setForm((current) => ({
      ...current,
      hours: current.hours.map((h, i) => (i === index ? { ...h, ...update } : h)),
    }))
  }
  function loadReferenceHours() {
    change({
      hours: periods.flatMap(({ value: period }) =>
        weekdays.map((dayOfWeek, day) => {
          let closed = false,
            openTime = '09:00',
            closeTime = facility === 'park' ? '17:00' : '20:00'
          if (facility === 'reading') {
            openTime = period === 'EXAM' ? '00:00' : '06:30'
            closeTime = period === 'EXAM' ? '24:00' : '23:00'
          }
          if (facility === 'library') {
            closed = day === 6 || (period === 'VACATION' && day === 5)
            openTime =
              period === 'VACATION'
                ? '10:00'
                : day === 5 && period === 'SEMESTER'
                  ? '11:00'
                  : '09:00'
            closeTime =
              period === 'VACATION'
                ? '16:00'
                : day === 5
                  ? period === 'EXAM'
                    ? '17:00'
                    : '15:00'
                  : '21:00'
          }
          return {
            period,
            dayOfWeek,
            closed,
            openTime: closed ? null : openTime,
            closeTime: closed ? null : closeTime,
          }
        }),
      ),
    })
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    setSaved(false)
    if (
      Boolean(form.examStartDate) !== Boolean(form.examEndDate) ||
      (form.examStartDate && form.examEndDate && form.examStartDate > form.examEndDate)
    ) {
      setValidation('시험기간 시작일과 종료일을 올바르게 입력해주세요.')
      return
    }
    const invalid = form.hours.find(
      (h) =>
        !h.closed &&
        (!h.openTime ||
          !h.closeTime ||
          h.openTime >= h.closeTime ||
          !/^([01]\d|2[0-3]):[0-5]\d$/.test(h.openTime) ||
          !/^(([01]\d|2[0-3]):[0-5]\d|24:00)$/.test(h.closeTime)),
    )
    if (invalid) {
      setTab(invalid.period)
      setValidation('운영시간을 확인해주세요. 종료는 시작 이후이며 자정은 24:00으로 입력합니다.')
      return
    }
    void action.run(
      () => adminApi.savePolicy(policy.spaceId, form),
      async () => {
        await Promise.all([
          cache.invalidateQueries({ queryKey: ['policy', policy.spaceId] }),
          cache.invalidateQueries({ queryKey: ['availability'] }),
          cache.invalidateQueries({ queryKey: ['seat-availability'] }),
        ])
        setSaved(true)
      },
    )
  }
  return (
    <form onSubmit={submit} className="stack">
      <div>
        <h2>운영시간 · 시험기간 정책</h2>
        <p className="muted small mt-2">
          한국 표준시 기준입니다. 시험기간에는 해당 기간의 주간 시간표가 기본 시간표를 대체합니다.
        </p>
      </div>
      {!policy.configured && (
        <p className="notice">
          아직 저장된 정책이 없습니다. 아래 시간표를 설정하고 정책 적용을 켜주세요.
        </p>
      )}
      <fieldset disabled={action.pending} className="stack">
        {facility && (
          <>
            <button type="button" className="btn secondary self-start" onClick={loadReferenceHours}>
              참고 프로젝트 운영시간 불러오기
            </button>
            <p className="small muted">
              시간표만 채웁니다. 현재 학교 운영 기준을 확인하고 정책 적용·시험기간 날짜를 설정한 뒤
              저장해주세요. 학술정보관 시험기간 예시는 학기 중 기준입니다.
            </p>
          </>
        )}
        <label className="check-label">
          <input
            type="checkbox"
            checked={form.enabled}
            onChange={(e) => change({ enabled: e.target.checked })}
          />
          새 예약에 운영시간 정책 적용
        </label>
        <label>
          기본 운영 기준
          <select
            value={form.academicPeriod}
            onChange={(e) => change({ academicPeriod: e.target.value as 'SEMESTER' | 'VACATION' })}
          >
            <option value="SEMESTER">학기</option>
            <option value="VACATION">방학</option>
          </select>
        </label>
        <div className="form-grid">
          <label>
            시험기간 시작일
            <input
              type="date"
              min="2000-01-01"
              max="9998-12-31"
              value={form.examStartDate ?? ''}
              onChange={(e) => change({ examStartDate: e.target.value || null })}
            />
          </label>
          <label>
            시험기간 종료일
            <input
              type="date"
              min={form.examStartDate || '2000-01-01'}
              max="9998-12-31"
              value={form.examEndDate ?? ''}
              onChange={(e) => change({ examEndDate: e.target.value || null })}
            />
          </label>
        </div>
        <button
          type="button"
          className="text-link self-start"
          onClick={() => change({ examStartDate: null, examEndDate: null })}
        >
          시험기간 지정 해제
        </button>
        <div className="tabs" aria-label="운영 시간표 선택">
          {periods.map((p) => (
            <button
              type="button"
              key={p.value}
              aria-pressed={tab === p.value}
              className={tab === p.value ? 'active' : ''}
              onClick={() => setTab(p.value)}
            >
              {p.label}
            </button>
          ))}
        </div>
        <div className="stack">
          {form.hours.map((h, index) =>
            h.period !== tab ? null : (
              <div className="hours-editor" key={h.dayOfWeek}>
                <strong>{days[weekdays.indexOf(h.dayOfWeek)]}</strong>
                <label className="check-label">
                  <input
                    type="checkbox"
                    checked={h.closed}
                    onChange={(e) =>
                      hour(index, {
                        closed: e.target.checked,
                        openTime: e.target.checked ? null : '09:00',
                        closeTime: e.target.checked ? null : '18:00',
                      })
                    }
                  />
                  휴무
                </label>
                {!h.closed && (
                  <>
                    <label>
                      <span className="sr-only">{days[weekdays.indexOf(h.dayOfWeek)]} 시작</span>
                      <input
                        type="time"
                        value={h.openTime ?? ''}
                        required
                        onChange={(e) => hour(index, { openTime: e.target.value })}
                      />
                    </label>
                    <span>~</span>
                    <label>
                      <span className="sr-only">{days[weekdays.indexOf(h.dayOfWeek)]} 종료</span>
                      <input
                        placeholder="24:00"
                        inputMode="text"
                        pattern="([01][0-9]|2[0-3]):[0-5][0-9]|24:00"
                        required
                        value={h.closeTime ?? ''}
                        onChange={(e) => hour(index, { closeTime: e.target.value })}
                      />
                    </label>
                  </>
                )}
              </div>
            ),
          )}
        </div>
      </fieldset>
      <p className="small muted">
        00:00~24:00은 24시간 운영입니다. 자정을 넘는 운영은 두 요일로 나누어 설정하세요. 기존 예약은
        유지되며, 새 예약부터 적용됩니다.
      </p>
      <ErrorNotice error={validation ? new Error(validation) : action.error} />
      {saved && (
        <p className="notice success" role="status">
          운영 정책을 저장했습니다.
        </p>
      )}
      <button className="btn primary self-start" disabled={action.pending}>
        {action.pending ? '저장 중…' : '정책 전체 저장'}
      </button>
    </form>
  )
}
