import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../api/admin'
import { useAuth } from '../hooks/authContext'
import { ErrorNotice, Loading, PageHeading, Pagination } from '../components/ui'
import { ReservationList } from '../components/ReservationList'
export default function AdminReservationsPage() {
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const spaceId = Number(params.get('spaceId')) || undefined
  const auth = useAuth()
  const result = useQuery({
    queryKey: ['admin', 'reservations', auth.user?.id, page, spaceId],
    queryFn: ({ signal }) => adminApi.reservations(page, spaceId, signal),
  })
  return (
    <>
      <PageHeading eyebrow="RESERVATIONS" title="담당 공간 예약 조회">
        {spaceId ? `공간 #${spaceId}의 예약` : '접근 권한이 있는 공간의 예약만 표시됩니다.'}
      </PageHeading>
      <div className="tabs">
        <Link to="/admin">담당 공간</Link>
        <Link className="active" to="/admin/reservations">
          예약 조회
        </Link>
      </div>
      {spaceId && (
        <Link className="text-link mb-5 inline-block" to="/admin/reservations">
          전체 담당 공간 보기
        </Link>
      )}
      {result.isPending ? (
        <Loading />
      ) : result.isError ? (
        <ErrorNotice error={result.error} retry={() => void result.refetch()} />
      ) : (
        <>
          <ReservationList items={result.data.content} admin />
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
