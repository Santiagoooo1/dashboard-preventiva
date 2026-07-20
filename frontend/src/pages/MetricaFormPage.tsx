import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import type {
  CampoMetricaMetadataDto,
  CatalogoFrontendResponseDto,
  ConfiguracionMetricaDto,
  FiltroMetricaDto,
  MetadataMetricasResponseDto,
  MetricaClinicaRequestDto,
  MetricaClinicaResponseDto,
  OperadorFiltroCatalogoDto,
  ResultadoMetricaResponseDto,
  TipoMetrica,
} from '../api/types'
import { getCatalogo } from '../api/frontendCatalogApi'
import { actualizarMetrica, crearMetrica, obtenerMetadataMetricas, obtenerMetrica, previewMetrica } from '../api/metricasApi'
import { useApiResource } from '../hooks/useApiResource'
import { StateContainer } from '../components/StateContainer'
import { Card } from '../components/Card'
import { FormField } from '../components/FormField'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ErrorBanner } from '../components/ErrorBanner'
import { avisoCodigo } from '../utils/validacion'
import { MetricaConfigForm } from '../components/metrics/MetricaConfigForm'
import { WidgetActual } from '../components/widgets/WidgetActual'
import styles from './MetricaFormPage.module.css'

interface DatosFormulario {
  catalogo: CatalogoFrontendResponseDto
  metadata: MetadataMetricasResponseDto
  metricaExistente: MetricaClinicaResponseDto | null
}

interface EstadoFormulario {
  codigo: string
  nombre: string
  descripcion: string
  tipoMetrica: TipoMetrica
  unidad: string
  decimales: string
  orden: string
  configuracion: ConfiguracionMetricaDto
}

function configuracionBase(tipo: TipoMetrica): ConfiguracionMetricaDto {
  switch (tipo) {
    case 'CONTEO':
      return { filtros: [] }
    case 'PORCENTAJE':
      return { numerador: { filtros: [] }, denominador: { filtros: [] } }
    case 'PROMEDIO':
    case 'SUMA':
      return { campoValor: null, filtros: [] }
    case 'DISTRIBUCION':
      return { campoAgrupacion: null, filtros: [] }
  }
}

// En el formulario, el valor de IN/NOT_IN se edita como texto separado por
// comas; el backend espera una lista. Estas dos funciones convierten en ambos
// sentidos al cargar una métrica existente y al construir el payload.
function filtrosParaFormulario(filtros: FiltroMetricaDto[] | null | undefined): FiltroMetricaDto[] {
  return (filtros ?? []).map((f) => ({
    ...f,
    valor: Array.isArray(f.valor) ? f.valor.join(', ') : f.valor,
  }))
}

function filtrosParaPayload(
  filtros: FiltroMetricaDto[],
  campos: CampoMetricaMetadataDto[],
  operadores: OperadorFiltroCatalogoDto[],
): FiltroMetricaDto[] {
  return filtros.map((f) => {
    const operador = operadores.find((o) => o.codigo === f.operador)
    if (!operador?.requiereLista || typeof f.valor !== 'string') {
      return f
    }
    const campo = campos.find((c) => c.codigo === f.campo)
    const esNumerico = campo?.tipoDato === 'ENTERO' || campo?.tipoDato === 'DECIMAL'
    const elementos = f.valor
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s !== '')
    return { ...f, valor: esNumerico ? elementos.map(Number) : elementos }
  })
}

function configuracionParaFormulario(tipo: TipoMetrica, config: ConfiguracionMetricaDto): ConfiguracionMetricaDto {
  if (tipo === 'PORCENTAJE') {
    return {
      numerador: { filtros: filtrosParaFormulario(config.numerador?.filtros) },
      denominador: { filtros: filtrosParaFormulario(config.denominador?.filtros) },
    }
  }
  return {
    ...configuracionBase(tipo),
    campoValor: config.campoValor ?? null,
    campoAgrupacion: config.campoAgrupacion ?? null,
    filtros: filtrosParaFormulario(config.filtros),
  }
}

export function MetricaFormPage() {
  const { datasetId, metricaId } = useParams<{ datasetId: string; metricaId?: string }>()
  const navigate = useNavigate()
  const esEdicion = metricaId !== undefined

  const { data, loading, error } = useApiResource<DatosFormulario>(
    async (signal) => {
      const [catalogo, metadata, metricaExistente] = await Promise.all([
        getCatalogo(signal),
        obtenerMetadataMetricas(datasetId ?? '', signal),
        metricaId ? obtenerMetrica(metricaId, signal) : Promise.resolve(null),
      ])
      return { catalogo, metadata, metricaExistente }
    },
    [datasetId, metricaId],
  )

  const [form, setForm] = useState<EstadoFormulario>({
    codigo: '',
    nombre: '',
    descripcion: '',
    tipoMetrica: 'CONTEO',
    unidad: '',
    decimales: '',
    orden: '',
    configuracion: configuracionBase('CONTEO'),
  })
  const [erroresForm, setErroresForm] = useState<Record<string, string>>({})
  const [errorBackend, setErrorBackend] = useState<string | null>(null)
  const [resultadoPreview, setResultadoPreview] = useState<ResultadoMetricaResponseDto | null>(null)
  const [fechaDesde, setFechaDesde] = useState('')
  const [fechaHasta, setFechaHasta] = useState('')
  const [guardando, setGuardando] = useState(false)

  useEffect(() => {
    const metrica = data?.metricaExistente
    if (metrica) {
      const tipo = metrica.tipoMetrica as TipoMetrica
      setForm({
        codigo: metrica.codigo,
        nombre: metrica.nombre,
        descripcion: metrica.descripcion ?? '',
        tipoMetrica: tipo,
        unidad: metrica.unidad ?? '',
        decimales: String(metrica.decimales ?? ''),
        orden: String(metrica.orden ?? ''),
        configuracion: configuracionParaFormulario(tipo, metrica.configuracion),
      })
    }
  }, [data])

  const cambiarTipo = (tipo: TipoMetrica) => {
    setForm((f) => ({ ...f, tipoMetrica: tipo, configuracion: configuracionBase(tipo) }))
    setResultadoPreview(null)
  }

  const validar = (): Record<string, string> => {
    const errores: Record<string, string> = {}
    if (!form.codigo.trim()) errores.codigo = 'El código es obligatorio.'
    if (!form.nombre.trim()) errores.nombre = 'El nombre es obligatorio.'

    const config = form.configuracion
    if ((form.tipoMetrica === 'PROMEDIO' || form.tipoMetrica === 'SUMA') && !config.campoValor) {
      errores['configuracion.campoValor'] = 'Selecciona el campo de valor.'
    }
    if (form.tipoMetrica === 'DISTRIBUCION' && !config.campoAgrupacion) {
      errores['configuracion.campoAgrupacion'] = 'Selecciona el campo de agrupación.'
    }

    const operadores = data?.catalogo.operadoresFiltro ?? []
    const validarFiltros = (filtros: FiltroMetricaDto[], clave: string) => {
      for (const filtro of filtros) {
        if (!filtro.campo) {
          errores[clave] = 'Cada filtro debe indicar un campo.'
          return
        }
        if (!filtro.operador) {
          errores[clave] = 'Cada filtro debe indicar un operador.'
          return
        }
        const operador = operadores.find((o) => o.codigo === filtro.operador)
        if (operador?.requiereLista) {
          const texto = typeof filtro.valor === 'string' ? filtro.valor : ''
          if (!texto.split(',').some((s) => s.trim() !== '')) {
            errores[clave] = 'Los operadores de lista requieren al menos un valor.'
            return
          }
        } else if (operador?.requiereValor) {
          if (filtro.valor === null || filtro.valor === undefined || filtro.valor === '') {
            errores[clave] = 'Este operador requiere un valor.'
            return
          }
        }
      }
    }

    if (form.tipoMetrica === 'PORCENTAJE') {
      validarFiltros(config.numerador?.filtros ?? [], 'configuracion.numerador')
      validarFiltros(config.denominador?.filtros ?? [], 'configuracion.denominador')
    } else {
      validarFiltros(config.filtros ?? [], 'configuracion.filtros')
    }

    return errores
  }

  const construirPayload = (): MetricaClinicaRequestDto => {
    const campos = data?.metadata.campos ?? []
    const operadores = data?.catalogo.operadoresFiltro ?? []
    const config = form.configuracion

    const configuracion: ConfiguracionMetricaDto =
      form.tipoMetrica === 'PORCENTAJE'
        ? {
            numerador: { filtros: filtrosParaPayload(config.numerador?.filtros ?? [], campos, operadores) },
            denominador: { filtros: filtrosParaPayload(config.denominador?.filtros ?? [], campos, operadores) },
          }
        : {
            ...(form.tipoMetrica === 'PROMEDIO' || form.tipoMetrica === 'SUMA'
              ? { campoValor: config.campoValor }
              : {}),
            ...(form.tipoMetrica === 'DISTRIBUCION' ? { campoAgrupacion: config.campoAgrupacion } : {}),
            filtros: filtrosParaPayload(config.filtros ?? [], campos, operadores),
          }

    return {
      codigo: form.codigo.trim(),
      nombre: form.nombre.trim(),
      descripcion: form.descripcion.trim() || null,
      tipoMetrica: form.tipoMetrica,
      configuracion,
      unidad: form.unidad.trim() || null,
      decimales: form.decimales === '' ? null : Number(form.decimales),
      orden: form.orden === '' ? null : Number(form.orden),
    }
  }

  const previsualizar = async () => {
    setErrorBackend(null)
    setResultadoPreview(null)
    const errores = validar()
    setErroresForm(errores)
    if (Object.keys(errores).length > 0) return

    try {
      const resultado = await previewMetrica(datasetId ?? '', {
        metrica: construirPayload(),
        fechaDesde: fechaDesde || null,
        fechaHasta: fechaHasta || null,
      })
      setResultadoPreview(resultado)
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al previsualizar.')
    }
  }

  const guardar = async () => {
    setErrorBackend(null)
    const errores = validar()
    setErroresForm(errores)
    if (Object.keys(errores).length > 0) return

    setGuardando(true)
    try {
      if (esEdicion && metricaId) {
        await actualizarMetrica(metricaId, construirPayload())
      } else {
        await crearMetrica(datasetId ?? '', construirPayload())
      }
      navigate(`/datasets/${datasetId}/metricas`)
    } catch (err) {
      setErrorBackend(err instanceof Error ? err.message : 'Error al guardar la métrica.')
      setGuardando(false)
    }
  }

  const ayudaTipoMetrica = data?.catalogo.tipoMetricas.find((t) => t.codigo === form.tipoMetrica)?.descripcion

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
                { label: esEdicion ? 'Editar métrica' : 'Nueva métrica' },
              ]}
            />
            <h1>{esEdicion ? 'Editar métrica' : 'Nueva métrica'}</h1>
            <p>
              Dataset: <strong>{data.metadata.dataset.nombre}</strong> ({data.metadata.dataset.codigo})
            </p>

            <ErrorBanner mensaje={errorBackend} />

            <Card title="Datos generales">
              <div className={styles.formGrid}>
                <FormField
                  label="Código interno de la métrica"
                  help="Identificador técnico único dentro del dataset. Ejemplos: total_ilq, tasa_ilq, estancia_media."
                  aviso={avisoCodigo(form.codigo)}
                  error={erroresForm.codigo}
                >
                  <input value={form.codigo} onChange={(e) => setForm({ ...form, codigo: e.target.value })} />
                </FormField>
                <FormField label="Nombre" error={erroresForm.nombre}>
                  <input value={form.nombre} onChange={(e) => setForm({ ...form, nombre: e.target.value })} />
                </FormField>
                <FormField label="Tipo de métrica" help={ayudaTipoMetrica ?? undefined}>
                  <select value={form.tipoMetrica} onChange={(e) => cambiarTipo(e.target.value as TipoMetrica)}>
                    {data.catalogo.tipoMetricas.map((t) => (
                      <option key={t.codigo} value={t.codigo}>
                        {t.nombre}
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField label="Unidad">
                  <input value={form.unidad} onChange={(e) => setForm({ ...form, unidad: e.target.value })} />
                </FormField>
                <FormField label="Decimales">
                  <input
                    type="number"
                    step={1}
                    min={0}
                    value={form.decimales}
                    onChange={(e) => setForm({ ...form, decimales: e.target.value })}
                  />
                </FormField>
                <FormField label="Orden">
                  <input
                    type="number"
                    step={1}
                    value={form.orden}
                    onChange={(e) => setForm({ ...form, orden: e.target.value })}
                  />
                </FormField>
                <div className={styles.descripcion}>
                  <FormField label="Descripción">
                    <textarea
                      rows={2}
                      value={form.descripcion}
                      onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                    />
                  </FormField>
                </div>
              </div>
            </Card>

            <Card title="Configuración">
              <MetricaConfigForm
                tipoMetrica={form.tipoMetrica}
                configuracion={form.configuracion}
                onChange={(configuracion) => setForm({ ...form, configuracion })}
                campos={data.metadata.campos}
                operadoresCatalogo={data.catalogo.operadoresFiltro}
                errores={erroresForm}
              />
            </Card>

            <p className={styles.ayudaPreview}>Previsualiza el resultado antes de guardar la métrica.</p>
            <div className={styles.fechasPreview}>
              <span>Rango para previsualizar (opcional):</span>
              <input type="date" value={fechaDesde} onChange={(e) => setFechaDesde(e.target.value)} />
              <span>—</span>
              <input type="date" value={fechaHasta} onChange={(e) => setFechaHasta(e.target.value)} />
            </div>

            <div className={styles.botones}>
              <button type="button" className="btn btnSecondary" onClick={previsualizar}>
                Previsualizar
              </button>
              <button type="button" className="btn btnPrimary" disabled={guardando} onClick={guardar}>
                {esEdicion ? 'Guardar cambios' : 'Crear métrica'}
              </button>
              <Link className="btn btnSecondary" to={`/datasets/${datasetId}/metricas`}>
                Cancelar
              </Link>
            </div>

            {resultadoPreview && (
              <Card title="Previsualización" subtitle={`${resultadoPreview.tipoMetrica}`}>
                <WidgetActual resultado={resultadoPreview} />
              </Card>
            )}
          </>
        )}
      </StateContainer>
    </div>
  )
}
