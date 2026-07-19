import type { ReactNode } from 'react'

interface StateContainerProps {
  loading: boolean
  error: string | null
  empty?: boolean
  emptyMessage?: string
  children: ReactNode
}

export function StateContainer({ loading, error, empty, emptyMessage, children }: StateContainerProps) {
  if (loading) {
    return (
      <p className="stateLoading" role="status">
        Cargando…
      </p>
    )
  }

  if (error) {
    return (
      <p className="stateError" role="alert">
        Error: {error}
      </p>
    )
  }

  if (empty) {
    return <p className="stateEmpty">{emptyMessage ?? 'No hay datos disponibles.'}</p>
  }

  return <>{children}</>
}
