import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, Plus } from 'lucide-react'
import { adminApi } from '../api/admin'
import { useAuth } from '../hooks/authContext'
import { Empty, ErrorNotice, Loading, PageHeading, Pagination } from '../components/ui'
import { label } from '../utils/labels'
import { today } from '../utils/time'
export default function AdminPage() {
  const auth = useAuth()
  const [page, setPage] = useState(0)
  const [date, setDate] = useState(today)
  const summary = useQuery({
    queryKey: ['admin', 'summary', auth.user?.id, date],
    queryFn: ({ signal }) => adminApi.summary(date, signal),
  })
  const result = useQuery({
    queryKey: ['admin', 'spaces', auth.user?.id, page],
    queryFn: ({ signal }) => adminApi.spaces(page, signal),
  })
  return (
    <>
      <PageHeading
        eyebrow="SPACE MANAGEMENT"
        title="공간 운영 관리"
        action={
          <Link className="btn primary" to="/admin/spaces/new">
            <Plus size={17} />
            공간 등록
          </Link>
        }
      >
        담당 공간의 예약, 운영시간, 차단 일정을 관리합니다.
      </PageHeading>
      <section className="panel mb-6">
        <div className="section-heading">
          <h2>예약 현황</h2>
          <label>
            조회 날짜
            <input
              type="date"
              required
              value={date}
              onChange={(e) => {
                if (e.target.value) setDate(e.target.value)
              }}
            />
          </label>
        </div>
        {summary.isPending ? (
          <Loading />
        ) : summary.isError ? (
          <ErrorNotice error={summary.error} retry={() => void summary.refetch()} />
        ) : (
          <>
            <div className="admin-stat-grid">
              {[
                ['예약 건수', `${summary.data.reservationCount}건`],
                ['예약 시간 합계', `${(summary.data.reservedMinutes / 60).toFixed(1)}시간`],
                ['현재 이용 중', `${summary.data.activeCount}건`],
                [
                  '예약 중지 공간',
                  `${summary.data.disabledSpaces} / ${summary.data.managedSpaces}개`,
                ],
              ].map(([name, value]) => (
                <div key={name}>
                  <span>{name}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>
            <p className="small muted mt-3">
              담당 공간만 집계합니다. 취소 제외 · 한국 표준시 기준 · 좌석 반납 시 실제 종료시간 반영
            </p>
          </>
        )}
      </section>
      <div className="tabs">
        <Link className="active" to="/admin">
          담당 공간
        </Link>
        <Link to="/admin/reservations">예약 조회</Link>
      </div>
      {result.isPending ? (
        <Loading />
      ) : result.isError ? (
        <ErrorNotice error={result.error} retry={() => void result.refetch()} />
      ) : (
        <>
          <div className="section-heading">
            <h2>
              담당 공간 <span className="count">{result.data.totalElements}</span>
            </h2>
          </div>
          {result.data.content.length ? (
            <div className="panel p-0">
              {result.data.content.map((space) => (
                <Link className="reservation-row" key={space.id} to={`/admin/spaces/${space.id}`}>
                  <div className="flex-1">
                    <h3>{space.name}</h3>
                    <p className="small muted mt-2">
                      {label(space.venue)} · {space.location || '위치 미등록'}
                    </p>
                  </div>
                  <span className={`badge ${space.bookingEnabled ? 'green' : ''}`}>
                    {space.bookingEnabled ? '예약 운영 중' : '예약 중지'}
                  </span>
                  <ArrowUpRight size={18} />
                </Link>
              ))}
            </div>
          ) : (
            <Empty title="배정된 공간이 없습니다.">
              운영 담당자에게 공간 관리 권한을 요청해주세요. 새 공간을 등록하면 담당자로 연결됩니다.
            </Empty>
          )}
          <Pagination page={page} totalPages={result.data.totalPages} onChange={setPage} />
        </>
      )}
    </>
  )
}
