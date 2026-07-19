import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div>
      <h1>Página no encontrada</h1>
      <p>La ruta solicitada no existe.</p>
      <Link to="/">Volver al inicio</Link>
    </div>
  )
}
