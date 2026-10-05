import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  Building2,
  CalendarDays,
  ChevronRight,
  GraduationCap,
  LogOut,
  Menu,
  ShieldCheck,
  X,
} from 'lucide-react'
import { useAuth } from '../hooks/authContext'
import { useAction } from '../hooks/useAction'
import { ErrorNotice } from '../components/ui'

export default function AppLayout() {
  const [menu, setMenu] = useState(false)
  const auth = useAuth()
  const action = useAction()
  const location = useLocation()
  const main = useRef<HTMLElement>(null)
  const sidebar = useRef<HTMLElement>(null)
  useEffect(() => {
    main.current?.focus()
    window.scrollTo(0, 0)
  }, [location.pathname])
  useEffect(() => {
    if (!menu) return
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    sidebar.current?.querySelector<HTMLButtonElement>('.mobile-close')?.focus()
    function trap(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenu(false)
        return
      }
      if (event.key !== 'Tab') return
      const items = Array.from(
        sidebar.current?.querySelectorAll<HTMLElement>('a, button:not(:disabled)') ?? [],
      )
      const first = items[0],
        last = items.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last?.focus()
      }
      if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first?.focus()
      }
    }
    document.addEventListener('keydown', trap)
    return () => {
      document.body.style.overflow = overflow
      document.removeEventListener('keydown', trap)
      previous?.focus()
    }
  }, [menu])
  return (
    <div className="app-shell">
      <a href="#main" className="skip-link">
        본문 바로가기
      </a>
      <aside
        ref={sidebar}
        role={menu ? 'dialog' : undefined}
        aria-modal={menu || undefined}
        aria-label={menu ? '주 메뉴' : undefined}
        className={`sidebar ${menu ? 'is-open' : ''}`}
      >
        <Link to="/spaces" className="brand" onClick={() => setMenu(false)}>
          <span className="brand-symbol">
            <GraduationCap size={26} />
          </span>
          <span>
            <b>HSP</b>
            <small>HANSUNG SPACE PLATFORM</small>
          </span>
        </Link>
        <div className="sidebar-caption">한성대학교 통합 공간 예약</div>
        <button
          className="mobile-close icon-button"
          aria-label="메뉴 닫기"
          onClick={() => setMenu(false)}
        >
          <X />
        </button>
        <nav aria-label="주 메뉴" onClick={() => setMenu(false)}>
          <p className="nav-label">공간 이용</p>
          <NavLink to="/spaces">
            <Building2 size={19} />
            공간 찾기
            <ChevronRight size={16} className="ml-auto" />
          </NavLink>
          <NavLink to="/reservations">
            <CalendarDays size={19} />내 예약
          </NavLink>
          {auth.user?.role === 'ADMIN' && (
            <>
              <p className="nav-label mt-8">운영 관리</p>
              <NavLink to="/admin">
                <ShieldCheck size={19} />
                관리자
              </NavLink>
            </>
          )}
        </nav>
        <div className="sidebar-bottom">
          <span className="small-label">함께 쓰는 캠퍼스</span>
          <p>필요한 공간을, 필요한 시간에.</p>
          <small>
            사용하지 않는 예약은
            <br />
            다음 이용자를 위해 취소해주세요.
          </small>
        </div>
      </aside>
      {menu && (
        <button className="menu-overlay" aria-label="메뉴 닫기" onClick={() => setMenu(false)} />
      )}
      <div className="workspace" inert={menu}>
        <header className="topbar">
          <div className="flex items-center gap-3">
            <button
              className="mobile-menu icon-button"
              aria-label="메뉴 열기"
              aria-expanded={menu}
              onClick={() => setMenu(true)}
            >
              <Menu size={22} />
            </button>
            <span className="topbar-title">
              한성대학교 <span className="muted">/</span> 공간 예약
            </span>
          </div>
          <div className="flex items-center gap-3">
            {auth.user ? (
              <>
                <span className="user-name">
                  {auth.user.name}
                  <span className="muted"> 님</span>
                </span>
                <button
                  className="icon-button"
                  disabled={action.pending}
                  onClick={() => void action.run(auth.logout)}
                  aria-label="로그아웃"
                >
                  <LogOut size={18} />
                </button>
              </>
            ) : (
              <Link className="btn small secondary" to="/login">
                로그인
              </Link>
            )}
          </div>
        </header>
        <main id="main" ref={main} tabIndex={-1} className="main-content">
          <ErrorNotice error={action.error} />
          <Outlet />
        </main>
        <footer className="footer">
          <span>HSP · Hansung University</span>
          <span>모든 예약 시간은 한국 표준시(KST) 기준입니다.</span>
        </footer>
      </div>
    </div>
  )
}
