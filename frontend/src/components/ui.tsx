import { useEffect, useId, useRef, type ReactNode } from 'react'
import { AlertCircle, ArrowLeft, ArrowRight, LoaderCircle, X } from 'lucide-react'
import { errorMessage } from '../api/client'

export function Loading({ message = '정보를 불러오는 중입니다.' }: { message?: string }) {
  return (
    <div className="state" role="status">
      <LoaderCircle className="animate-spin" size={24} />
      <p>{message}</p>
    </div>
  )
}
export function ErrorNotice({ error, retry }: { error: unknown; retry?: () => void }) {
  if (!error) return null
  return (
    <div className="notice error" role="alert">
      <AlertCircle size={19} className="shrink-0" />
      <div>
        <p>{errorMessage(error)}</p>
        {retry && (
          <button className="text-link mt-2" onClick={retry}>
            다시 시도
          </button>
        )}
      </div>
    </div>
  )
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state">
      <div className="empty-mark">—</div>
      <h2>{title}</h2>
      {children && <p className="muted">{children}</p>}
    </div>
  )
}
export function PageHeading({
  eyebrow,
  title,
  children,
  action,
}: {
  eyebrow?: string
  title: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {children && <p className="muted mt-2">{children}</p>}
      </div>
      {action}
    </div>
  )
}
export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="페이지 이동" className="pagination">
      <button className="btn secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        <ArrowLeft size={16} />
        이전
      </button>
      <span aria-live="polite">
        {page + 1} / {totalPages}
      </span>
      <button
        className="btn secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        다음
        <ArrowRight size={16} />
      </button>
    </nav>
  )
}
export function Modal({
  title,
  children,
  onClose,
  busy = false,
  wide = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  busy?: boolean
  wide?: boolean
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()
  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])
  return (
    <dialog
      ref={ref}
      aria-labelledby={id}
      className={`modal ${wide ? 'modal-wide' : ''}`}
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) onClose()
      }}
    >
      <header className="flex items-center justify-between gap-4">
        <h2 id={id}>{title}</h2>
        <button className="icon-button" aria-label="닫기" onClick={onClose} disabled={busy}>
          <X size={20} />
        </button>
      </header>
      <div className="mt-5">{children}</div>
    </dialog>
  )
}
