import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { reservationsApi } from '../api/reservations'
import { useAuth } from '../hooks/authContext'
import { ErrorNotice, Loading, PageHeading, Pagination } from '../components/ui'
import { ReservationList } from '../components/ReservationList'
export default function ReservationsPage() {
  const auth = useAuth()
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const result = useQuery({
    queryKey: ['reservations', auth.user?.id, page],
    queryFn: ({ signal }) => reservationsApi.list(page, signal),
  })
  return (
    <>
      <PageHeading
        eyebrow="MY RESERVATIONS"
        title="내 예약"
        action={
          <Link className="btn secondary" to="/spaces">
            공간 찾기
          </Link>
        }
      >
        예정된 일정과 지난 이용 내역을 확인하세요.
      </PageHeading>
      {result.isPending ? (
        <Loading />
      ) : result.isError ? (
        <ErrorNotice error={result.error} retry={() => void result.refetch()} />
      ) : (
        <>
          <div className="section-heading">
            <h2>
              전체 예약 <span className="count">{result.data.totalElements}</span>
            </h2>
            <span className="small muted">예약 시작 시간 최신순</span>
          </div>
          <ReservationList items={result.data.content} />
          <Pagination
            page={page}
            totalPages={result.data.totalPages}
            onChange={(p) => setParams({ page: String(p) })}
          />
        </>
      )}
    </>
  )
}
