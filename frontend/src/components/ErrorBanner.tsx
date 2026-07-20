interface ErrorBannerProps {
  mensaje: string | null
}

export function ErrorBanner({ mensaje }: ErrorBannerProps) {
  if (!mensaje) {
    return null
  }
  return (
    <div className="bannerError" role="alert">
      {mensaje}
    </div>
  )
}
