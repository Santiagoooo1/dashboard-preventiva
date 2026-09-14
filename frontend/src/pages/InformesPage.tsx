import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import type { InformeClinicoResponseDto } from '../api/types'
import { crearInforme, eliminarInforme, listarInformes } from '../api/informesApi'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { DataTable } from '../components/DataTable'

/** Lista de informes del usuario (Fase 6.9Q). */
export function InformesPage() {
  const navigate = useNavigate()
  const [informes, setInformes] = useState<InformeClinicoResponseDto[]>([])
  const [error, setError] = useState<string | null>(null)
  const [creando, setCreando] = useState(false)

  const cargar = () => {
    listarInformes()
      .then(setInformes)
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudieron cargar los informes.'))
  }

  useEffect(cargar, [])

  const nuevo = async () => {
    setCreando(true)
    setError(null)
    try {
      const informe = await crearInforme({ nombre: 'Nuevo informe' })
      navigate(`/informes/${informe.id}/editar`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo crear el informe.')
      setCreando(false)
    }
  }

  const borrar = async (informe: InformeClinicoResponseDto) => {
    if (!window.confirm(`¿Eliminar «${informe.tituloVisible}»? Los datos originales no se tocan.`)) return
    try {
      await eliminarInforme(informe.id)
      cargar()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo eliminar el informe.')
    }
  }

  return (
    <Card title="Mis informes">
      <p>
        Un informe es una plantilla: guarda qué indicadores muestra, no sus cifras. Cada vez que se abre, los datos
        son los del momento.
      </p>

      <ErrorBanner mensaje={error} />

      <div style={{ marginBottom: 'var(--spacing-md)' }}>
        <button type="button" className="btn btnPrimary" disabled={creando} onClick={nuevo}>
          {creando ? 'Creando…' : 'Nuevo informe'}
        </button>
      </div>

      {informes.length === 0 ? (
        <p>Todavía no has creado ningún informe.</p>
      ) : (
        <DataTable
          columns={[
            { key: 'tituloVisible', header: 'Título' },
            {
              key: 'actualizadoEn',
              header: 'Última modificación',
              render: (i) => formatearFecha(i.actualizadoEn),
            },
            { key: 'totalPaginas', header: 'Páginas', render: (i) => i.totalPaginas ?? 0 },
            {
              key: 'acciones',
              header: 'Acciones',
              render: (i) => (
                <span style={{ display: 'flex', gap: 'var(--spacing-xs)', flexWrap: 'wrap' }}>
                  <Link className="btn btnSecondary" to={`/informes/${i.id}/editar`}>
                    Editar
                  </Link>
                  <Link className="btn btnSecondary" to={`/informes/${i.id}/vista-previa`}>
                    Vista previa
                  </Link>
                  <button type="button" className="btn btnDanger" onClick={() => borrar(i)}>
                    Eliminar
                  </button>
                </span>
              ),
            },
          ]}
          rows={informes}
          getRowKey={(i) => i.id}
        />
      )}
    </Card>
  )
}

function formatearFecha(iso: string): string {
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) return '—'
  const dd = String(fecha.getDate()).padStart(2, '0')
  const mm = String(fecha.getMonth() + 1).padStart(2, '0')
  const hh = String(fecha.getHours()).padStart(2, '0')
  const mi = String(fecha.getMinutes()).padStart(2, '0')
  return `${dd}/${mm}/${fecha.getFullYear()} ${hh}:${mi}`
}
