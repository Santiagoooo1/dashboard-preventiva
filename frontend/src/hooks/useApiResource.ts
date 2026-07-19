import { useCallback, useEffect, useState } from 'react'
import type { DependencyList } from 'react'

interface ApiResourceState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

interface UseApiResourceResult<T> extends ApiResourceState<T> {
  reload: () => void
}

export function useApiResource<T>(
  fetcher: (signal: AbortSignal) => Promise<T>,
  deps: DependencyList,
): UseApiResourceResult<T> {
  const [state, setState] = useState<ApiResourceState<T>>({ data: null, loading: true, error: null })
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setState({ data: null, loading: true, error: null })

    fetcher(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setState({ data, loading: false, error: null })
        }
      })
      .catch((err: unknown) => {
        if (!controller.signal.aborted) {
          const message = err instanceof Error ? err.message : 'Error desconocido.'
          setState({ data: null, loading: false, error: message })
        }
      })

    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return { ...state, reload }
}
