import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { today } from '../utils/time'
export function MonthCalendar({
  value,
  onChange,
}: {
  value: string
  onChange: (date: string) => void
}) {
  const [month, setMonth] = useState(value.slice(0, 7))
  const first = new Date(`${month}-01T12:00:00Z`)
  const days = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate()
  const minimum = today()
  function move(offset: number) {
    const next = new Date(first)
    next.setUTCMonth(next.getUTCMonth() + offset)
    setMonth(next.toISOString().slice(0, 7))
  }
  return (
    <section aria-label="예약 날짜 선택">
      <div className="section-heading">
        <h3>날짜 선택</h3>
        <span className="small muted">KST</span>
      </div>
      <div className="calendar-toolbar">
        <button
          className="icon-button"
          aria-label="이전 달"
          disabled={month <= minimum.slice(0, 7)}
          onClick={() => move(-1)}
        >
          <ChevronLeft size={18} />
        </button>
        <strong>
          {first.getUTCFullYear()}년 {first.getUTCMonth() + 1}월
        </strong>
        <button className="icon-button" aria-label="다음 달" onClick={() => move(1)}>
          <ChevronRight size={18} />
        </button>
      </div>
      <div className="calendar-grid">
        <div className="calendar-weekdays">
          {['일', '월', '화', '수', '목', '금', '토'].map((day) => (
            <span key={day}>{day}</span>
          ))}
        </div>
        {Array.from({ length: first.getUTCDay() }, (_, i) => (
          <span key={`blank${i}`} />
        ))}
        {Array.from({ length: days }, (_, i) => {
          const date = `${month}-${String(i + 1).padStart(2, '0')}`
          return (
            <button
              key={date}
              className={date === value ? 'selected' : ''}
              disabled={date < minimum}
              aria-pressed={date === value}
              aria-label={`${date}${date === minimum ? ', 오늘' : ''}`}
              onClick={() => onChange(date)}
            >
              {i + 1}
              {date === minimum && <span className="today-dot" />}
            </button>
          )
        })}
      </div>
    </section>
  )
}
