import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, Armchair, Building2, MapPin, Search, Users } from 'lucide-react'
import { spacesApi } from '../api/spaces'
import { Empty, ErrorNotice, Loading, PageHeading, Pagination } from '../components/ui'
import { capacity, label } from '../utils/labels'
import { campusPhoto, facilityFor } from '../assets/spaceMedia'
import { FacilityGuide } from '../components/FacilityGuide'

export default function SpacesPage() {
  const [params, setParams] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')
  const page = Math.max(0, Number(params.get('page')) || 0)
  const filters = {
    q: params.get('q') ?? '',
    type: params.get('type') ?? '',
    venue: params.get('venue') ?? '',
    minCapacity: Number(params.get('people')) || undefined,
    bookingEnabled: params.get('enabled') !== 'false',
    page,
    size: 12,
  }
  const result = useQuery({
    queryKey: ['spaces', filters],
    queryFn: ({ signal }) => spacesApi.list(filters, signal),
  })
  const catalog = useQuery({
    queryKey: ['spaces', 'catalog', filters.bookingEnabled],
    queryFn: ({ signal }) => spacesApi.catalog(filters.bookingEnabled, signal),
    staleTime: 60_000,
  })
  const types = [
    ...new Set([filters.type, ...(catalog.data ?? []).map((s) => s.type)].filter(Boolean)),
  ]
  const venues = [
    ...new Set([filters.venue, ...(catalog.data ?? []).map((s) => s.venue)].filter(Boolean)),
  ]
  function change(key: string, value: string) {
    const next = new URLSearchParams(params)
    next.delete('page')
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next)
  }
  function search(event: FormEvent) {
    event.preventDefault()
    change('q', q.trim())
  }
  return (
    <>
      <PageHeading eyebrow="HANSUNG SPACE PLATFORM" title="어느 공간을 예약할까요?">
        공간을 고르고, 함께할 시간을 채워보세요.
      </PageHeading>
      {!filters.q && !filters.venue && !filters.type && page === 0 && (
        <>
          <section className="campus-banner">
            <img src={campusPhoto} alt="한성대학교 캠퍼스 전경" fetchPriority="high" />
            <div>
              <p className="eyebrow">OUR CAMPUS, YOUR SPACE</p>
              <h2>
                상상을 현실로 만드는
                <br />
                우리의 공간
              </h2>
              <p>배움도, 만남도. 한성에서 함께 시작하세요.</p>
            </div>
            <span className="campus-banner-caption">HANSUNG UNIVERSITY</span>
          </section>
          <FacilityGuide />
        </>
      )}
      <section id="space-results" className="panel mb-7" aria-label="공간 검색 및 필터">
        <form onSubmit={search} className="search-row">
          <label className="search-input">
            <Search size={20} />
            <span className="sr-only">공간명 또는 위치 검색</span>
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              maxLength={100}
              placeholder="공간명 또는 위치로 검색"
            />
          </label>
          <button className="btn primary">검색</button>
        </form>
        <div className="filter-row">
          <label>
            시설
            <select
              value={filters.venue}
              disabled={catalog.isPending}
              onChange={(e) => change('venue', e.target.value)}
            >
              <option value="">{catalog.isPending ? '불러오는 중…' : '전체 시설'}</option>
              {venues.map((value) => (
                <option key={value} value={value}>
                  {label(value)}
                </option>
              ))}
            </select>
          </label>
          <label>
            공간 종류
            <select
              value={filters.type}
              disabled={catalog.isPending}
              onChange={(e) => change('type', e.target.value)}
            >
              <option value="">전체 종류</option>
              {types.map((value) => (
                <option key={value} value={value}>
                  {label(value)}
                </option>
              ))}
            </select>
          </label>
          <label>
            이용 인원
            <input
              type="number"
              min="1"
              max="101"
              value={params.get('people') ?? ''}
              placeholder="전체"
              onChange={(e) => change('people', e.target.value)}
            />
          </label>
          <label>
            예약 운영
            <select
              value={String(filters.bookingEnabled)}
              onChange={(e) => change('enabled', e.target.value)}
            >
              <option value="true">예약 운영 중</option>
              <option value="false">예약 중지</option>
            </select>
          </label>
          <button
            className="text-link self-end pb-3"
            onClick={() => {
              setQ('')
              setParams({})
            }}
          >
            필터 초기화
          </button>
        </div>
        <ErrorNotice error={catalog.error} retry={() => void catalog.refetch()} />
      </section>
      {result.isPending ? (
        <Loading />
      ) : result.isError ? (
        <ErrorNotice error={result.error} retry={() => void result.refetch()} />
      ) : (
        <>
          <div className="section-heading">
            <h2>
              공간 목록 <span className="count">{result.data.totalElements}</span>
            </h2>
            <span className="small muted">공간별 예약 조건을 확인해주세요.</span>
          </div>
          {!result.data.content.length ? (
            <Empty title="조건에 맞는 공간이 없습니다.">검색어 또는 필터를 변경해보세요.</Empty>
          ) : (
            <div className="space-grid">
              {result.data.content.map((space) => (
                <Link key={space.id} className="space-card" to={`/spaces/${space.id}`}>
                  {facilityFor(space) && (
                    <div
                      className={`space-card-image ${facilityFor(space)?.id === 'library' ? 'photo' : ''}`}
                    >
                      <img
                        src={facilityFor(space)!.image}
                        alt={facilityFor(space)!.alt}
                        loading="lazy"
                      />
                    </div>
                  )}
                  <div className="space-card-top">
                    <span className="space-icon">
                      {space.type === 'READING_ROOM' ? (
                        <Armchair size={30} />
                      ) : (
                        <Building2 size={30} />
                      )}
                    </span>
                    <span className={`badge ${space.bookingEnabled ? 'green' : ''}`}>
                      {space.bookingEnabled ? '예약 운영 중' : '예약 중지'}
                    </span>
                  </div>
                  <div className="space-card-body">
                    <p className="small-label">{label(space.venue)}</p>
                    <h3>{space.name}</h3>
                    <p className="meta">
                      <MapPin size={15} />
                      {space.location || '위치 안내 준비 중'}
                    </p>
                    <p className="meta">
                      <Users size={15} />
                      {space.type === 'READING_ROOM' ? '좌석별 1인 이용' : capacity(space)}
                    </p>
                    {space.facilities && (
                      <p className="small muted line-clamp-2 mt-3">{space.facilities}</p>
                    )}
                  </div>
                  <div className="space-card-footer">
                    <span>{label(space.type)}</span>
                    <span className="flex items-center gap-2">
                      공간 보기
                      <ArrowUpRight size={17} />
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          )}
          <Pagination
            page={page}
            totalPages={result.data.totalPages}
            onChange={(p) => {
              const next = new URLSearchParams(params)
              next.set('page', String(p))
              setParams(next)
            }}
          />
        </>
      )}
    </>
  )
}
