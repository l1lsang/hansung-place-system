import { Link } from 'react-router-dom'
import { ArrowUpRight, MapPinned } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { spacesApi } from '../api/spaces'
import { facilities, facilityFor } from '../assets/spaceMedia'
import { ErrorNotice } from './ui'
export function FacilityGuide() {
  const catalog = useQuery({
    queryKey: ['spaces', 'catalog', true],
    queryFn: ({ signal }) => spacesApi.catalog(true, signal),
    staleTime: 60_000,
  })
  return (
    <section className="facility-guide" aria-label="캠퍼스 시설 안내">
      <div className="section-heading">
        <h2>캠퍼스 시설 안내</h2>
        <span className="small muted">공간을 살펴보고 나에게 맞는 곳을 찾아보세요.</span>
      </div>
      <div className="facility-grid">
        {facilities.map((facility) => {
          const available = (catalog.data ?? []).filter(
            (space) => facilityFor(space)?.id === facility.id,
          )
          const venues = new Set(available.map((s) => s.venue))
          return (
            <article className="facility-card" key={facility.id}>
              <div className={`facility-image ${facility.id === 'library' ? 'photo' : ''}`}>
                <img src={facility.image} alt={facility.alt} loading="lazy" />
                <span className="facility-image-label">
                  {facility.id === 'library' ? 'CAMPUS VIEW' : 'SPACE MAP'}
                </span>
              </div>
              <div className="facility-card-body">
                <p className="eyebrow">{facility.english}</p>
                <h3>{facility.title}</h3>
                <p className="muted small mt-2">{facility.description}</p>
                <div className="facility-card-footer">
                  <span className="flex items-center gap-2">
                    <MapPinned size={15} />
                    {catalog.isPending
                      ? '공간 확인 중…'
                      : catalog.isError
                        ? '예약 운영 확인 필요'
                        : available.length
                          ? `${available.length}개 공간 예약 운영 중`
                          : '현재 예약 운영 공간 없음'}
                  </span>
                  {available.length > 0 && (
                    <Link
                      to={
                        available.length === 1
                          ? `/spaces/${available[0].id}`
                          : `/spaces?${new URLSearchParams(venues.size === 1 ? { venue: available[0].venue } : { q: facility.title })}#space-results`
                      }
                      className="facility-cta"
                      aria-label={`${facility.title} 예약 공간 보기`}
                    >
                      공간 보기
                      <ArrowUpRight size={16} />
                    </Link>
                  )}
                </div>
              </div>
            </article>
          )
        })}
      </div>
      <ErrorNotice error={catalog.error} retry={() => void catalog.refetch()} />
    </section>
  )
}
