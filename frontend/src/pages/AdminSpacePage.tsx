import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { adminApi } from '../api/admin'
import { spacesApi } from '../api/spaces'
import { useAction } from '../hooks/useAction'
import { Empty, ErrorNotice, Loading, PageHeading } from '../components/ui'
import { SpaceForm } from '../components/SpaceForm'
import { PolicyEditor } from '../components/PolicyEditor'
import { BlockManager } from '../components/BlockManager'
export default function AdminSpacePage() {
  const { spaceId } = useParams()
  const create = spaceId === 'new'
  const id = Number(spaceId)
  const valid = Number.isSafeInteger(id) && id > 0
  const navigate = useNavigate()
  const [tab, setTab] = useState('info')
  const [success, setSuccess] = useState(false)
  const cache = useQueryClient()
  const action = useAction()
  // The scoped admin endpoint must succeed before showing any edit controls.
  const scope = useQuery({
    queryKey: ['admin', 'scope', id],
    enabled: valid,
    queryFn: ({ signal }) => adminApi.reservations(0, id, signal),
  })
  const space = useQuery({
    queryKey: ['space', id],
    enabled: valid && scope.isSuccess,
    queryFn: ({ signal }) => spacesApi.get(id, signal),
  })
  const policy = useQuery({
    queryKey: ['policy', id],
    enabled: valid && scope.isSuccess,
    queryFn: ({ signal }) => spacesApi.policy(id, signal),
  })
  if (!create && !valid) return <Empty title="올바르지 않은 공간 주소입니다." />
  if (!create && (scope.isError || space.isError))
    return (
      <ErrorNotice
        error={scope.error ?? space.error}
        retry={() => {
          void scope.refetch()
          void space.refetch()
        }}
      />
    )
  if (!create && (scope.isPending || space.isPending)) return <Loading />
  return (
    <>
      <Link to="/admin" className="back-link">
        <ArrowLeft size={16} />
        담당 공간 목록
      </Link>
      <PageHeading
        eyebrow="SPACE SETTINGS"
        title={create ? '새 공간 등록' : space.data!.name}
        action={
          !create && (
            <Link className="btn secondary" to={`/admin/reservations?spaceId=${id}`}>
              이 공간 예약 보기
            </Link>
          )
        }
      />
      {!create && (
        <div className="tabs">
          {[
            { key: 'info', name: '기본 정보' },
            { key: 'policy', name: '운영시간·시험기간' },
            { key: 'blocks', name: '차단 일정' },
          ].map((t) => (
            <button
              key={t.key}
              className={tab === t.key ? 'active' : ''}
              aria-pressed={tab === t.key}
              onClick={() => setTab(t.key)}
            >
              {t.name}
            </button>
          ))}
        </div>
      )}
      <section className="panel max-w-4xl">
        {(create || tab === 'info') && (
          <>
            {success && (
              <p role="status" className="notice success mb-5">
                공간 정보를 저장했습니다.
              </p>
            )}
            <SpaceForm
              key={spaceId}
              initial={create ? undefined : space.data}
              pending={action.pending}
              error={action.error}
              onSubmit={(input) => {
                setSuccess(false)
                void action.run(
                  () => (create ? adminApi.createSpace(input) : adminApi.updateSpace(id, input)),
                  async (result) => {
                    cache.setQueryData(['space', result.id], result)
                    await Promise.all([
                      cache.invalidateQueries({ queryKey: ['admin'] }),
                      cache.invalidateQueries({ queryKey: ['spaces'] }),
                      cache.invalidateQueries({ queryKey: ['availability'] }),
                      cache.invalidateQueries({ queryKey: ['seat-availability'] }),
                    ])
                    setSuccess(true)
                    if (create) navigate(`/admin/spaces/${result.id}`, { replace: true })
                  },
                )
              }}
            />
          </>
        )}
        {!create &&
          tab === 'policy' &&
          (policy.isPending ? (
            <Loading />
          ) : policy.isError ? (
            <ErrorNotice error={policy.error} retry={() => void policy.refetch()} />
          ) : (
            <PolicyEditor key={id} policy={policy.data} />
          ))}
        {!create && tab === 'blocks' && <BlockManager spaceId={id} />}
      </section>
    </>
  )
}
