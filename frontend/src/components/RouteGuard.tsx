import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/authContext'
import { Empty, ErrorNotice, Loading } from './ui'
export function RouteGuard({ admin = false }: { admin?: boolean }) {
  const auth = useAuth()
  const location = useLocation()
  if (auth.loading) return <Loading message="로그인 상태를 확인하고 있습니다." />
  if (auth.error) return <ErrorNotice error={auth.error} retry={auth.refresh} />
  if (!auth.user)
    return (
      <Navigate
        to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
        replace
      />
    )
  if (admin && auth.user.role !== 'ADMIN')
    return <Empty title="관리자 권한이 필요합니다.">담당 공간 관리자만 접근할 수 있습니다.</Empty>
  return <Outlet />
}
