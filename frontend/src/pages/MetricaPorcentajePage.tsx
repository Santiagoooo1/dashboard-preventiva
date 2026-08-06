import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import type {
  FiltroMetricaDto,
  MetricaClinicaRequestDto,
  MetricaClinicaResponseDto,
  ResultadoMetricaResponseDto,
} from '../api/types'
import { getCatalogo } from '../api/frontendCatalogApi'
import { crearMetrica, listarMetricas, obtenerMetadataMetricas, previewMetrica } from '../api/metricasApi'
import { listarPaneles } from '../api/panelesApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { FormField } from '../components/FormField'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { FiltroBuilder } from '../components/metrics/FiltroBuilder'
import { WidgetActual } from '../components/widgets/WidgetActual'
import { filtrosParaPayload } from '../components/metrics/constructorMetrica'
import { MetricaCreadaPanel } from '../components/metrics/MetricaCreadaPanel'
import styles from './MetricaDesdeColumnaPage.module.css'

/**
 * Porcentaje condicional (Fase 6.9I.2).
 *
 * <p>Separa las tres decisiones que un porcentaje clínico esconde y que, mal
 * planteadas, producen indicadores incomparables entre servicios:
 * a quién se mira (filtros base), qué cuenta arriba y qué cuenta abajo.
 *
 * <p>No es un motor aparte: produce una métrica PORCENTAJE con la misma
 * `ConfiguracionMetricaDto` que el modo avanzado, y la ejecuta el mismo
 * servicio. Lo único que aporta es plantear las preguntas en el orden correcto
 * y obligar a etiquetar numerador y denominador.
 */
export function MetricaPorcentajePage() {
  const { datasetId } = useParams<{ datasetId: string }>()
  // De qué panel venía el usuario, si venía de alguno.
  const [searchParams] = useSearchParams()
  const panelOrigenId = searchParams.get('panelId')

  const { data, loading, error } = useApiResource(
    async (signal) => {
      const [catalogo, metadata, paneles, metricas] = await Promise.all([
        getCatalogo(signal),
        obtenerMetadataMetricas(datasetId ?? '', signal),
        listarPaneles(datasetId ?? '', signal),
        listarMetricas(datasetId ?? '', signal),
      ])
      return { catalogo, metadata, paneles, metricas }
    },
    [datasetId],
  )

  const [filtrosBase, setFiltrosBase] = useState<FiltroMetricaDto[]>([])
  const [filtrosNumerador, setFiltrosNumerador] = useState<FiltroMetricaDto[]>([])
  const [filtrosDenominador, setFiltrosDenominador] = useState<FiltroMetricaDto[]>([])

  const [etiquetaNumerador, setEtiquetaNumerador] = useState('')
  const [etiquetaDenominador, setEtiquetaDenominador] = useState('')

  const [codigo, setCodigo] = useState('')
  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [decimales, setDecimales] = useState('2')

  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [preview, setPreview] = useState<ResultadoMetricaResponseDto | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [metricaCreada, setMetricaCreada] = useState<MetricaClinicaResponseDto | null>(null)
  const [abrirFormularioWidget, setAbrirFormularioWidget] = useState(false)

  const construirPayload = (): MetricaClinicaRequestDto => {
    const campos = data?.metadata.campos ?? []
    const operadores = data?.catalogo.operadoresFiltro ?? []

    return {
      codigo: codigo.trim(),
      nombre: nombre.trim(),
      descripcion: descripcion.trim() || null,
      tipoMetrica: 'PORCENTAJE',
      configuracion: {
        // Los filtros base acotan la población y se aplican también a numerador
        // y denominador: sin ellos esto sería un porcentaje corriente.
        filtros: filtrosParaPayload(filtrosBase, campos, operadores),
        numerador: { filtros: filtrosParaPayload(filtrosNumerador, campos, operadores) },
        denominador: { filtros: filtrosParaPayload(filtrosDenominador, campos, operadores) },
        etiquetaNumerador: etiquetaNumerador.trim() || null,
        etiquetaDenominador: etiquetaDenominador.trim() || null,
      },
      unidad: '%',
      decimales: decimales === '' ? null : Number(decimales),
      orden: null,
    }
  }

  const validar = (): Record<string, string> => {
    const nuevos: Record<string, string> = {}
    if (!codigo.trim()) nuevos.codigo = 'El código es obligatorio.'
    if (!nombre.trim()) nuevos.nombre = 'El nombre es obligatorio.'

    if (filtrosNumerador.length === 0) {
      nuevos.numerador = 'Define qué cuenta en el numerador: sin condición, contaría toda la población.'
    }

    const usados = new Set((data?.metricas ?? []).map((m) => m.codigo.toLowerCase()))
    if (codigo.trim() && usados.has(codigo.trim().toLowerCase())) {
      nuevos.codigo = 'Ya existe una métrica con este código en el dataset. Cambia el código.'
    }

    return nuevos
  }

  const previsualizar = async () => {
    setErrorBackend(null)
    setPreview(null)
    const nuevos = validar()
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      setPreview(await previewMetrica(datasetId ?? '', { metrica: construirPayload() }))
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'No se pudo previsualizar.')
    }
  }

  /**
   * Crea la métrica y deja el paso siguiente a mano. `conWidget` solo decide si
   * el formulario rápido aparece desplegado: el widget nunca se crea solo.
   */
  const guardar = async (conWidget: boolean) => {
    setErrorBackend(null)
    const nuevos = validar()
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    setGuardando(true)
    try {
      const metrica = await crearMetrica(datasetId ?? '', construirPayload())
      setMetricaCreada(metrica)
      setAbrirFormularioWidget(conWidget)
      setGuardando(false)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'No se pudo crear la métrica.')
      setGuardando(false)
    }
  }

  const crearOtra = () => {
    setMetricaCreada(null)
    setFiltrosBase([])
    setFiltrosNumerador([])
    setFiltrosDenominador([])
    setCodigo('')
    setNombre('')
    setPreview(null)
    setErrores({})
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <div className={styles.page}>
      <StateContainer loading={loading} error={error} empty={data === null}>
        {data && (
          <>
            <Breadcrumbs
              items={[
                { label: 'Datasets', to: '/datasets' },
                { label: data.metadata.dataset.codigo, to: `/datasets/${datasetId}` },
                { label: 'Métricas', to: `/datasets/${datasetId}/metricas` },
                { label: 'Nueva métrica', to: `/datasets/${datasetId}/metricas/nueva` },
                { label: 'Porcentaje condicional' },
              ]}
            />
            <h1>Porcentaje condicional</h1>
            <p className={styles.intro}>
              Un porcentaje clínico depende tanto del denominador como del numerador. Aquí defines los dos por
              separado, y ambos se muestran siempre junto al resultado: «199 de 249 — 79,92 %».
            </p>

            <ErrorBanner mensaje={errorBackend} />

            {metricaCreada && (
              <MetricaCreadaPanel
                datasetId={datasetId ?? ''}
                metrica={metricaCreada}
                campos={data.metadata.campos}
                paneles={data.paneles}
                granularidades={data.catalogo.granularidades}
                panelOrigenId={panelOrigenId}
                abrirFormulario={abrirFormularioWidget}
                onCrearOtra={crearOtra}
                rutaCatalogo={`/datasets/${datasetId}/metricas`}
              />
            )}

            <Card title="Paso 1 — ¿A quién se mira? (filtros base)">
              <p className={styles.ayuda}>
                Acota la población antes de calcular nada. Se aplica por igual al numerador y al denominador.
                Ejemplo: «solo los casos en que la profilaxis estaba indicada». Déjalo vacío para partir de todos
                los registros.
              </p>
              <FiltroBuilder
                filtros={filtrosBase}
                onChange={setFiltrosBase}
                campos={data.metadata.campos}
                operadoresCatalogo={data.catalogo.operadoresFiltro}
              />
            </Card>

            <Card title="Paso 2 — ¿Qué cuenta arriba? (numerador)">
              <p className={styles.ayuda}>
                Los casos que quieres contar. Ejemplo: «la adecuación fue ADECUADA».
              </p>
              {errores.numerador && <p className={styles.error}>{errores.numerador}</p>}
              <FiltroBuilder
                filtros={filtrosNumerador}
                onChange={setFiltrosNumerador}
                campos={data.metadata.campos}
                operadoresCatalogo={data.catalogo.operadoresFiltro}
              />
              <FormField
                label="Cómo se llama el numerador"
                help="Se muestra junto al resultado, para que se entienda qué se está contando."
              >
                <input
                  value={etiquetaNumerador}
                  onChange={(e) => setEtiquetaNumerador(e.target.value)}
                  placeholder="Ej.: Profilaxis adecuada"
                />
              </FormField>
            </Card>

            <Card title="Paso 3 — ¿Sobre cuántos? (denominador)">
              <p className={styles.ayuda}>
                La población evaluable. Si lo dejas vacío, el denominador son todos los registros que pasan los
                filtros base. Ejemplo típico: excluir los NO_APLICA y los que no tienen dato, porque no son casos
                en los que se pudiera hacer bien o mal.
              </p>
              <FiltroBuilder
                filtros={filtrosDenominador}
                onChange={setFiltrosDenominador}
                campos={data.metadata.campos}
                operadoresCatalogo={data.catalogo.operadoresFiltro}
              />
              <FormField
                label="Cómo se llama el denominador"
                help="Aparece junto al resultado y, cuando no hay ningún caso evaluable, explica por qué no hay valor."
              >
                <input
                  value={etiquetaDenominador}
                  onChange={(e) => setEtiquetaDenominador(e.target.value)}
                  placeholder="Ej.: Casos con adecuación evaluable"
                />
              </FormField>
              <p className={styles.ayuda}>
                Si ningún registro entra en el denominador, el resultado será «Sin base evaluable», nunca un 0 %:
                no es lo mismo que nada saliera bien que que no hubiera nada que evaluar.
              </p>
            </Card>

            <Card title="Paso 4 — Nombre y guardado">
              <div className={styles.grid}>
                <FormField label="Nombre" error={errores.nombre}>
                  <input
                    value={nombre}
                    onChange={(e) => setNombre(e.target.value)}
                    placeholder="Ej.: Adecuación de la profilaxis"
                  />
                </FormField>
                <FormField
                  label="Código interno"
                  help="Identificador estable dentro del dataset."
                  error={errores.codigo}
                >
                  <input
                    value={codigo}
                    onChange={(e) => setCodigo(e.target.value)}
                    placeholder="Ej.: profilaxis_adecuacion"
                  />
                </FormField>
                <FormField label="Decimales">
                  <input
                    type="number"
                    min={0}
                    step={1}
                    value={decimales}
                    onChange={(e) => setDecimales(e.target.value)}
                  />
                </FormField>
                <div className={styles.anchoCompleto}>
                  <FormField label="Descripción (opcional)">
                    <textarea rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
                  </FormField>
                </div>
              </div>

              <div className={styles.botones}>
                <button type="button" className="btn btnPrimary" disabled={guardando} onClick={() => guardar(false)}>
                  {guardando ? 'Guardando…' : 'Crear métrica'}
                </button>
                <button type="button" className="btn btnPrimary" disabled={guardando} onClick={() => guardar(true)}>
                  Crear y añadir al dashboard
                </button>
                <button type="button" className="btn btnSecondary" onClick={previsualizar}>
                  Previsualizar
                </button>
                <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas/nueva`}>
                  Cancelar
                </Link>
              </div>
            </Card>

            {preview && (
              <Card title="Vista previa" subtitle="Calculada sobre los datos actuales; no se ha guardado nada.">
                <WidgetActual resultado={preview} />
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
