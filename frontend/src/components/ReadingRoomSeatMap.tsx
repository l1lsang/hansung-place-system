import { useRef, useState } from 'react'
import { Expand, MoveHorizontal } from 'lucide-react'
import readingPlan from './reading-room-map.png'
import { readingRoomPosition } from '../assets/readingRoomLayout'
import type { Seat } from '../types/api'
import { Modal } from './ui'
import { seatStatusLabels, type SeatStatus } from '../utils/seatStatus'
type Props = {
  seats: Seat[]
  statuses: Record<number, SeatStatus> | undefined
  selectedId: number | undefined
  checking: boolean
  onSelect: (seat: Seat) => void
}

function MapCanvas({ seats, statuses, selectedId, checking, onSelect }: Props) {
  const [zoom, setZoom] = useState<'fit' | 'detail'>('detail')
  const scroll = useRef<HTMLDivElement>(null)
  function pan(fraction: number) {
    const viewport = scroll.current
    if (viewport) viewport.scrollLeft = (viewport.scrollWidth - viewport.clientWidth) * fraction
  }
  return (
    <>
      <div className="seat-map-toolbar">
        <div className="seat-map-controls" role="group" aria-label="좌석표 배율">
          <button type="button" aria-pressed={zoom === 'fit'} onClick={() => setZoom('fit')}>
            전체 보기
          </button>
          <button type="button" aria-pressed={zoom === 'detail'} onClick={() => setZoom('detail')}>
            확대 보기
          </button>
        </div>
        {zoom === 'detail' && (
          <div className="seat-map-controls" role="group" aria-label="좌석표 이동">
            <button type="button" onClick={() => pan(0)}>
              왼쪽
            </button>
            <button type="button" onClick={() => pan(0.5)}>
              가운데
            </button>
            <button type="button" onClick={() => pan(1)}>
              오른쪽
            </button>
          </div>
        )}
      </div>
      <div
        ref={scroll}
        className="seat-map-scroll"
        tabIndex={0}
        role="region"
        aria-label="집중열람실 좌석표. 좌우로 스크롤할 수 있습니다."
        aria-busy={checking}
      >
        <div className={`seat-map-canvas ${zoom === 'detail' ? 'is-zoomed' : ''}`}>
          <img
            src={readingPlan}
            alt="집중열람실 001~162번 좌석의 실제 배치도"
            width={2262}
            height={998}
            draggable={false}
          />
          {seats.map((seat) => {
            const position = readingRoomPosition(seat.seatNumber)
            if (!position) return null
            const state = checking ? 'loading' : (statuses?.[seat.id] ?? 'loading')
            const chosen = selectedId === seat.id
            return (
              <button
                key={seat.id}
                type="button"
                className={`map-seat seat ${state} ${chosen ? 'selected' : ''}`}
                style={{ left: `${position.left}%`, top: `${position.top}%` }}
                disabled={state !== 'free'}
                aria-pressed={chosen}
                aria-label={`${seat.seatNumber}번 좌석, ${chosen ? '선택됨, ' : ''}${seatStatusLabels[state]}`}
                title={`${seat.seatNumber}번 · ${seatStatusLabels[state]}`}
                onClick={() => onSelect(seat)}
              >
                <span>{seat.seatNumber}</span>
                <small aria-hidden="true">
                  {state === 'loading'
                    ? '…'
                    : state === 'error'
                      ? '?'
                      : state === 'free'
                        ? chosen
                          ? '✓'
                          : '·'
                        : '×'}
                </small>
              </button>
            )
          })}
        </div>
      </div>
      <p className="seat-map-help">
        <MoveHorizontal size={16} />
        좌우로 이동하며 원하는 번호를 눌러주세요.
      </p>
    </>
  )
}

export function ReadingRoomSeatMap(props: Props) {
  const [expanded, setExpanded] = useState(false)
  const selected = props.seats.find((seat) => seat.id === props.selectedId)
  const unmapped = props.seats.filter((seat) => !readingRoomPosition(seat.seatNumber))
  const state = selected ? props.statuses?.[selected.id] : undefined
  return (
    <>
      <div className="seat-map-heading">
        <p className="small muted">이미지의 좌석 번호를 클릭하면 예약할 좌석이 선택됩니다.</p>
        <button type="button" className="btn secondary small" onClick={() => setExpanded(true)}>
          <Expand size={16} />
          크게 열기
        </button>
      </div>
      <MapCanvas {...props} />
      <div className="seat-map-selection">
        <p role="status">
          {selected ? (
            <>
              <strong>{selected.seatNumber}번 좌석</strong> 선택 ·{' '}
              {props.checking ? '확인 중' : seatStatusLabels[state ?? 'loading']}
            </>
          ) : (
            '원하는 좌석을 선택해주세요.'
          )}
        </p>
        {selected && (
          <a href="#booking-summary" className="text-link">
            예약 내용 확인 →
          </a>
        )}
      </div>
      {unmapped.length > 0 && (
        <div className="mt-4">
          <h3>배치도 외 좌석</h3>
          <p className="small muted mb-3">아래 좌석은 배치도에 위치가 표시되어 있지 않습니다.</p>
          <div className="seat-grid">
            {unmapped.map((seat) => {
              const state = props.checking ? 'loading' : (props.statuses?.[seat.id] ?? 'loading')
              return (
                <button
                  key={seat.id}
                  type="button"
                  className={`seat ${state} ${props.selectedId === seat.id ? 'selected' : ''}`}
                  disabled={state !== 'free'}
                  aria-pressed={props.selectedId === seat.id}
                  aria-label={`${seat.seatNumber}번 좌석, ${seatStatusLabels[state]}`}
                  onClick={() => props.onSelect(seat)}
                >
                  <span>{seat.seatNumber}</span>
                  <small>{seatStatusLabels[state]}</small>
                </button>
              )
            })}
          </div>
        </div>
      )}
      {expanded && (
        <Modal title="집중열람실 좌석 선택" wide onClose={() => setExpanded(false)}>
          <MapCanvas
            {...props}
            onSelect={(seat) => {
              props.onSelect(seat)
              setExpanded(false)
            }}
          />
          <p className="small muted mt-3">좌석을 선택하면 예약 내용에 반영됩니다.</p>
        </Modal>
      )}
    </>
  )
}
