import { useState, type FormEvent } from 'react'
import type { SpaceInput } from '../types/api'
import { ErrorNotice } from './ui'
export function SpaceForm({
  initial,
  onSubmit,
  pending,
  error,
}: {
  initial?: SpaceInput
  onSubmit: (input: SpaceInput) => void
  pending: boolean
  error: unknown
}) {
  const [form, setForm] = useState<SpaceInput>(
    initial ?? {
      spaceCode: '',
      name: '',
      type: 'STUDY_ROOM',
      venue: '',
      location: '',
      facilities: '',
      minCapacity: null,
      maxCapacity: null,
      bookingEnabled: true,
    },
  )
  const [validation, setValidation] = useState('')
  function update<K extends keyof SpaceInput>(key: K, value: SpaceInput[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    if (form.minCapacity && form.maxCapacity && form.minCapacity > form.maxCapacity) {
      setValidation('최소 인원은 최대 인원보다 클 수 없습니다.')
      return
    }
    onSubmit({
      ...form,
      spaceCode: form.spaceCode.trim(),
      name: form.name.trim(),
      type: form.type.trim(),
      venue: form.venue.trim(),
    })
  }
  return (
    <form onSubmit={submit} className="stack">
      <fieldset disabled={pending} className="form-grid">
        <label>
          공간명
          <input
            required
            maxLength={100}
            value={form.name}
            onChange={(e) => update('name', e.target.value)}
          />
        </label>
        <label>
          공간 코드
          <input
            required
            maxLength={50}
            value={form.spaceCode}
            onChange={(e) => update('spaceCode', e.target.value)}
            placeholder="예: STUDY_ROOM_A"
          />
        </label>
        <label>
          시설 코드
          <input
            required
            maxLength={30}
            value={form.venue}
            onChange={(e) => update('venue', e.target.value)}
            placeholder="예: LIBRARY"
          />
        </label>
        <label>
          공간 종류 코드
          <input
            required
            maxLength={30}
            list="space-types"
            value={form.type}
            onChange={(e) => update('type', e.target.value)}
          />
          <datalist id="space-types">
            <option value="STUDY_ROOM" />
            <option value="READING_ROOM" />
            <option value="MEETING_ROOM" />
          </datalist>
        </label>
        <label>
          위치
          <input
            maxLength={100}
            value={form.location ?? ''}
            onChange={(e) => update('location', e.target.value)}
          />
        </label>
        <label>
          시설 안내
          <input
            maxLength={255}
            value={form.facilities ?? ''}
            onChange={(e) => update('facilities', e.target.value)}
            placeholder="예: 모니터, 화이트보드"
          />
        </label>
        <label>
          최소 인원
          <input
            type="number"
            min="1"
            required={initial?.minCapacity != null}
            value={form.minCapacity ?? ''}
            onChange={(e) => update('minCapacity', e.target.value ? Number(e.target.value) : null)}
          />
        </label>
        <label>
          최대 인원
          <input
            type="number"
            min="1"
            required={initial?.maxCapacity != null}
            value={form.maxCapacity ?? ''}
            onChange={(e) => update('maxCapacity', e.target.value ? Number(e.target.value) : null)}
          />
        </label>
        <label className="check-label">
          <input
            type="checkbox"
            checked={form.bookingEnabled}
            onChange={(e) => update('bookingEnabled', e.target.checked)}
          />
          예약 활성화
        </label>
      </fieldset>
      {initial && (
        <p className="small muted">
          설정된 인원 제한은 숫자로 변경할 수 있습니다. 예약을 중지해도 기존 예약은 유지됩니다.
        </p>
      )}
      <ErrorNotice error={validation ? new Error(validation) : error} />
      <button className="btn primary self-start" disabled={pending}>
        {pending ? '저장 중…' : initial ? '변경 저장' : '공간 등록'}
      </button>
    </form>
  )
}
