import { useState } from 'react'
import { Expand, MapPinned } from 'lucide-react'
import { facilityFor } from '../assets/spaceMedia'
import type { Space } from '../types/api'
import { Modal } from './ui'
export function FloorPlan({ space }: { space: Space }) {
  const [expanded, setExpanded] = useState(false)
  const facility = facilityFor(space)
  if (!facility?.plan) return null
  return (
    <section className="panel floor-plan">
      <div className="section-heading">
        <div>
          <p className="eyebrow">SPACE GUIDE</p>
          <h2 className="flex items-center gap-2">
            <MapPinned size={18} />
            {facility.title} 배치도
          </h2>
        </div>
        <button className="text-link flex items-center gap-2" onClick={() => setExpanded(true)}>
          <Expand size={15} />
          크게 보기
        </button>
      </div>
      <img src={facility.plan} alt={facility.alt} className="floor-plan-image" loading="lazy" />
      <p className="small muted mt-4">
        배치도에서 위치를 확인한 뒤{' '}
        {facility.id === 'reading'
          ? '아래 좌석 목록에서 번호를 선택하세요.'
          : '선택한 공간의 예약 시간을 확인하세요.'}{' '}
        예약 가능 여부는 선택한 시간 기준으로 확인됩니다.
      </p>
      {expanded && (
        <Modal title={`${facility.title} 배치도`} wide onClose={() => setExpanded(false)}>
          <div
            className="plan-zoom"
            tabIndex={0}
            aria-label="배치도 확대 영역, 좌우로 스크롤할 수 있습니다"
          >
            <img src={facility.plan} alt={facility.alt} />
          </div>
        </Modal>
      )}
    </section>
  )
}
