import { useRef, useState } from 'react'
export function useAction() {
  const lock = useRef(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  async function run<T>(action: () => Promise<T>, onSuccess?: (result: T) => void | Promise<void>) {
    if (lock.current) return
    lock.current = true
    setPending(true)
    setError(null)
    try {
      const result = await action()
      await onSuccess?.(result)
    } catch (cause) {
      setError(cause)
    } finally {
      lock.current = false
      setPending(false)
    }
  }
  return { pending, error, run, clearError: () => setError(null) }
}
