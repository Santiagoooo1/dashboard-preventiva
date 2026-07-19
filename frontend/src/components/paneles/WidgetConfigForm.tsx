import { useState } from 'react'
import type {
  ConfiguracionWidgetDto,
  Granularidad,
  OpcionCatalogoDto,
  PanelMetricaConfiguracionWidgetRequestDto,
  TipoResultado,
  WidgetMetadataDto,
} from '../../api/types'
import { FormField } from '../FormField'
import styles from './WidgetForm.module.css'

const AUTOMATICO = '__AUTOMATICO__'

interface WidgetConfigFormProps {
  widget: WidgetMetadataDto
  camposFechaPermitidos: string[]
  camposAgrupacionPermitidos: string[]
  granularidades: OpcionCatalogoDto[]
  onSubmit: (payload: PanelMetricaConfiguracionWidgetRequestDto) => void
  onCancelar: () => void
  guardando: boolean
}

export function WidgetConfigForm({
  widget,
  camposFechaPermitidos,
  camposAgrupacionPermitidos,
  granularidades,
  onSubmit,
  onCancelar,
  guardando,
}: WidgetConfigFormProps) {
  const config = widget.configuracionWidgetActual
  const [tipoResultado, setTipoResultado] = useState(widget.tipoResultadoWidgetConfigurado ?? AUTOMATICO)
  const [granularidad, setGranularidad] = useState(config?.granularidad ?? '')
  const [campoFecha, setCampoFecha] = useState(config?.campoFecha ?? '')
  const [campoSegmentacion, setCampoSegmentacion] = useState(config?.campoSegmentacion ?? '')
  const [campoAgrupacion, setCampoAgrupacion] = useState(config?.campoAgrupacion ?? '')
  const [errores, setErrores] = useState<Record<string, string>>({})

  const esAutomatico = tipoResultado === AUTOMATICO
  const mostrarSerie = tipoResultado === 'SERIE_TEMPORAL'
  const mostrarComparativa = tipoResultado === 'COMPARATIVA'

  const enviar = () => {
    const nuevosErrores: Record<string, string> = {}
    if (mostrarSerie && !granularidad) {
      nuevosErrores.granularidad = 'SERIE_TEMPORAL requiere una granularidad.'
    }
    if (mostrarComparativa && !campoAgrupacion) {
      nuevosErrores.campoAgrupacion = 'COMPARATIVA requiere un campo de agrupación.'
    }
    setErrores(nuevosErrores)
    if (Object.keys(nuevosErrores).length > 0) return

    const configuracionWidget: ConfiguracionWidgetDto = {
      granularidad: (granularidad || null) as Granularidad | null,
      campoFecha: campoFecha || null,
      campoSegmentacion: campoSegmentacion || null,
      campoAgrupacion: campoAgrupacion || null,
    }

    onSubmit({
      tipoResultado: esAutomatico ? null : (tipoResultado as TipoResultado),
      configuracionWidget,
    })
  }

  return (
    <div className={styles.form}>
      <p className={styles.ayuda}>
        Resultado actual del widget: <strong>{widget.tipoResultadoActual}</strong>
        {widget.tipoResultadoWidgetConfigurado === null && ' (resuelto automáticamente)'}
      </p>
      <div className={styles.grid}>
        <FormField label="Tipo de resultado">
          <select value={tipoResultado} onChange={(e) => setTipoResultado(e.target.value)}>
            <option value={AUTOMATICO}>— Automático (según visualización) —</option>
            {widget.tipoResultadosPermitidos.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </FormField>

        {mostrarSerie && (
          <>
            <FormField label="Granularidad" error={errores.granularidad}>
              <select value={granularidad} onChange={(e) => setGranularidad(e.target.value)}>
                <option value="">— seleccionar granularidad —</option>
                {granularidades.map((g) => (
                  <option key={g.codigo} value={g.codigo}>
                    {g.nombre}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Campo de fecha (opcional)">
              <select value={campoFecha} onChange={(e) => setCampoFecha(e.target.value)}>
                <option value="">— por defecto —</option>
                {camposFechaPermitidos.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Campo de segmentación (opcional)">
              <select value={campoSegmentacion} onChange={(e) => setCampoSegmentacion(e.target.value)}>
                <option value="">— sin segmentar —</option>
                {camposAgrupacionPermitidos.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </FormField>
          </>
        )}

        {mostrarComparativa && (
          <FormField label="Campo de agrupación" error={errores.campoAgrupacion}>
            <select value={campoAgrupacion} onChange={(e) => setCampoAgrupacion(e.target.value)}>
              <option value="">— seleccionar campo —</option>
              {camposAgrupacionPermitidos.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </FormField>
        )}
      </div>
      <div className={styles.botones}>
        <button type="button" className={styles.primario} disabled={guardando} onClick={enviar}>
          Guardar configuración
        </button>
        <button type="button" className={styles.secundario} onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </div>
  )
}
