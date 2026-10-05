import { useState, type FormEvent } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { ArrowRight, Building2, CalendarCheck, LockKeyhole } from 'lucide-react'
import { useAuth } from '../hooks/authContext'
import { useAction } from '../hooks/useAction'
import { ErrorNotice } from '../components/ui'
import { campusPhoto } from '../assets/spaceMedia'

export default function LoginPage() {
  const [params] = useSearchParams()
  const next = params.get('next') ?? '/spaces'
  const redirect =
    next.startsWith('/') &&
    !next.startsWith('//') &&
    !next.includes('\\') &&
    !next.startsWith('/login')
      ? next
      : '/spaces'
  const auth = useAuth()
  const action = useAction()
  const [id, setId] = useState('')
  const [password, setPassword] = useState('')
  const [validation, setValidation] = useState('')
  if (auth.user) return <Navigate to={redirect} replace />
  function submit(event: FormEvent) {
    event.preventDefault()
    if (new TextEncoder().encode(password).length > 72) {
      setValidation('비밀번호는 UTF-8 기준 72바이트 이내로 입력해주세요.')
      return
    }
    setValidation('')
    void action.run(
      () => auth.login(id.trim(), password),
      () => setPassword(''),
    )
  }
  return (
    <div className="login-grid">
      <section className="login-intro">
        <img className="login-campus" src={campusPhoto} alt="" fetchPriority="high" />
        <div className="login-copy">
          <p className="eyebrow">HANSUNG SPACE PLATFORM</p>
          <h1>
            상상을 현실로 만드는
            <br />
            우리의 공간
          </h1>
          <p>
            학생들의 아이디어가 시작되는 곳,
            <br />
            한성의 공간을 더 가까이 만나보세요.
          </p>
          <div className="login-features">
            <div>
              <Building2 />
              <span>공간과 좌석 찾기</span>
            </div>
            <div>
              <CalendarCheck />
              <span>내 일정에 맞게 예약하기</span>
            </div>
          </div>
        </div>
        <span className="login-wordmark" aria-hidden="true">
          HSP
        </span>
      </section>
      <section className="login-form">
        <span className="feature-icon">
          <LockKeyhole size={23} />
        </span>
        <h2 className="mt-5 text-2xl">로그인</h2>
        <p className="muted mt-2 mb-7">발급받은 HSP 계정으로 로그인해주세요.</p>
        <form onSubmit={submit} className="stack">
          <label>
            학번
            <input
              value={id}
              onChange={(e) => setId(e.target.value)}
              maxLength={20}
              autoComplete="username"
              required
              placeholder="학번 입력"
              disabled={action.pending}
            />
          </label>
          <label>
            비밀번호
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              maxLength={72}
              autoComplete="current-password"
              required
              placeholder="비밀번호 입력"
              disabled={action.pending}
            />
          </label>
          <ErrorNotice error={validation ? new Error(validation) : action.error} />
          <button className="btn primary w-full" disabled={action.pending || auth.loading}>
            {action.pending ? '로그인 중…' : '로그인'}
            <ArrowRight size={17} />
          </button>
        </form>
        <p className="small muted mt-6">
          계정 발급 및 비밀번호 문의는 서비스 운영 담당자에게 문의해주세요.
        </p>
        <Link to="/spaces" className="text-link mt-6 inline-block">
          로그인 없이 공간 둘러보기 →
        </Link>
      </section>
    </div>
  )
}
