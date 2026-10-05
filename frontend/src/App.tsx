import { lazy, Suspense } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import { RouteGuard } from './components/RouteGuard'
import { Empty, Loading } from './components/ui'
const LoginPage = lazy(() => import('./pages/LoginPage'))
const SpacesPage = lazy(() => import('./pages/SpacesPage'))
const SpaceDetailPage = lazy(() => import('./pages/SpaceDetailPage'))
const ReservationsPage = lazy(() => import('./pages/ReservationsPage'))
const ReservationDetailPage = lazy(() => import('./pages/ReservationDetailPage'))
const AdminPage = lazy(() => import('./pages/AdminPage'))
const AdminSpacePage = lazy(() => import('./pages/AdminSpacePage'))
const AdminReservationsPage = lazy(() => import('./pages/AdminReservationsPage'))
export default function App() {
  return (
    <Suspense fallback={<Loading message="화면을 불러오는 중입니다." />}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/spaces" replace />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="spaces" element={<SpacesPage />} />
          <Route path="spaces/:spaceId" element={<SpaceDetailPage />} />
          <Route element={<RouteGuard />}>
            <Route path="reservations" element={<ReservationsPage />} />
            <Route path="reservations/:reservationId" element={<ReservationDetailPage />} />
          </Route>
          <Route element={<RouteGuard admin />}>
            <Route path="admin" element={<AdminPage />} />
            <Route path="admin/spaces/:spaceId" element={<AdminSpacePage />} />
            <Route path="admin/reservations" element={<AdminReservationsPage />} />
            <Route
              path="admin/reservations/:reservationId"
              element={<ReservationDetailPage admin />}
            />
          </Route>
          <Route
            path="*"
            element={
              <Empty title="페이지를 찾을 수 없습니다.">
                <Link className="text-link" to="/spaces">
                  공간 목록으로 이동
                </Link>
              </Empty>
            }
          />
        </Route>
      </Routes>
    </Suspense>
  )
}
