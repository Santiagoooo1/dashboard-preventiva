import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import type { AplicacionDashboardIlqDto, CompatibilidadDashboardIlqDto } from '../../api/types'
import { aplicarDashboardIlq, comprobarCompatibilidadIlq } from '../../api/dashboardIlqApi'
import { useApiResource } from '../../hooks/useApiResource'
import { Card } from '../Card'
import { ErrorBanner } from '../ErrorBanner'
import styles from './DashboardIlqCard.module.css'

interface DashboardIlqCardProps {
  datasetId: string | number
  /** Compacto: solo se pinta si el dataset es compatible (uso tras importar). */
  soloSiCompatible?: boolean
}

/**
 * Aplica el dashboard clínico de ILQ, profilaxis y Drago (Fase 6.9I.4).
 *
 * <p>La comprobación de compatibilidad es de solo lectura y se hace siempre;
 * aplicar exige que el usuario lo pida. Un dataset de vigilancia de ILQ no se
 * convierte en uno solo porque las columnas encajen: reglas clínicas sin
 * confirmación es exactamente lo que no debe pasar.
 *
 * <p>Cuando el dataset no encaja, no es un error: simplemente no le corresponde
 * este dashboard y se dice cuáles son los campos que faltarían.
 */
export function DashboardIlqCard({ datasetId, soloSiCompatible = false }: DashboardIlqCardProps) {
  const navigate = useNavigate()

  const { data, loading, error, reload } = useApiResource<CompatibilidadDashboardIlqDto>(
    (signal) => comprobarCompatibilidadIlq(datasetId, signal),
    [datasetId],
  )

  const [aplicando, setAplicando] = useState(false)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [resultado, setResultado] = useState<AplicacionDashboardIlqDto | null>(null)

  // Mientras carga, o si falla la comprobación, esta tarjeta simplemente no
  // aparece: es una oferta, no una parte imprescindible de la pantalla.
  if (loading || error || !data) return null
  if (soloSiCompatible && !data.compatible) return null

  const aplicar = async () => {
    setErrorAccion(null)
    setAplicando(true)
    try {
      setResultado(await aplicarDashboardIlq(datasetId))
      reload()
    } catch (err) {
      setErrorAccion(
        err instanceof Error
          ? err.message
          : 'No se pudo crear el dashboard clínico. No se ha modificado nada.',
      )
    } finally {
      setAplicando(false)
    }
  }

  // --- Ya aplicado en esta sesión ---
  if (resultado) {
    return (
      <Card title="Dashboard clínico listo" className={styles.tarjetaListo}>
        <p className={styles.mensaje}>
          {resultado.panelCreado
            ? 'Dashboard creado correctamente.'
            : 'El dashboard clínico ya estaba creado y se ha actualizado.'}{' '}
          {resultado.totalMetricas} métricas y {resultado.totalWidgets} widgets en «{resultado.panelNombre}».
        </p>
        <div className={styles.acciones}>
          <button
            type="button"
            className="btn btnPrimary"
            onClick={() => navigate(`/paneles/${resultado.panelId}/dashboard`)}
          >
            Abrir dashboard
          </button>
        </div>
      </Card>
    )
  }

  // --- Dataset sin los campos necesarios ---
  if (!data.compatible) {
    return (
      <Card title="Dashboard clínico de ILQ" className={styles.tarjetaNoAplica}>
        <p className={styles.mensaje}>
          El dataset no contiene todos los campos necesarios para el dashboard de ILQ, profilaxis y Drago. Puedes
          seguir creando tus propias métricas con normalidad.
        </p>
        <details className={styles.detalle}>
          <summary>Ver qué campos faltan ({data.camposAusentes.length})</summary>
          <div className={styles.listas}>
            {data.camposEncontrados.length > 0 && (
              <div>
                <p className={styles.listaTitulo}>Encontrados</p>
                <ul className={styles.lista}>
                  {data.camposEncontrados.map((c) => (
                    <li key={c}>{c}</li>
                  ))}
                </ul>
              </div>
            )}
            <div>
              <p className={styles.listaTitulo}>Ausentes</p>
              <ul className={`${styles.lista} ${styles.listaAusentes}`}>
                {data.camposAusentes.map((c) => (
                  <li key={c}>{c}</li>
                ))}
              </ul>
            </div>
          </div>
        </details>
      </Card>
    )
  }

  // --- Compatible: se ofrece crear o actualizar ---
  return (
    <Card
      title={data.yaAplicado ? 'Dashboard clínico de ILQ' : 'El dataset es compatible con el dashboard ILQ'}
      className={styles.tarjetaCompatible}
    >
      <ErrorBanner mensaje={errorAccion} />
      <p className={styles.mensaje}>
        {data.yaAplicado
          ? 'Este dataset ya tiene el dashboard de vigilancia de ILQ, profilaxis antibiótica y Drago. Puedes actualizarlo para recuperar las fórmulas de la plantilla.'
          : `Se reconocen los ${data.camposEncontrados.length} campos necesarios. Puedes generar el panel de vigilancia de ILQ, profilaxis antibiótica y Drago con ${data.totalElementos} indicadores.`}
      </p>
      {data.yaAplicado && (
        <p className={styles.nota}>
          Actualizar no duplica nada y conserva los títulos que hayas personalizado; solo restablece el cálculo
          clínico de cada indicador.
        </p>
      )}
      <div className={styles.acciones}>
        <button type="button" className="btn btnPrimary" disabled={aplicando} onClick={aplicar}>
          {aplicando
            ? 'Aplicando…'
            : data.yaAplicado
              ? 'Actualizar dashboard ILQ, profilaxis y Drago'
              : 'Crear dashboard ILQ, profilaxis y Drago'}
        </button>
        {data.yaAplicado && data.panelId && (
          <Link className="btn btnSecondary" to={`/paneles/${data.panelId}/dashboard`}>
            Abrir dashboard
          </Link>
        )}
      </div>
    </Card>
  )
}
