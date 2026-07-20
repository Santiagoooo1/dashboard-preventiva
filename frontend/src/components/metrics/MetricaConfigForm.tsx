import type {
  CampoMetricaMetadataDto,
  ConfiguracionMetricaDto,
  FiltroMetricaDto,
  OperadorFiltroCatalogoDto,
  TipoMetrica,
} from '../../api/types'
import { FormField } from '../FormField'
import { FiltroBuilder } from './FiltroBuilder'

interface MetricaConfigFormProps {
  tipoMetrica: TipoMetrica
  configuracion: ConfiguracionMetricaDto
  onChange: (configuracion: ConfiguracionMetricaDto) => void
  campos: CampoMetricaMetadataDto[]
  operadoresCatalogo: OperadorFiltroCatalogoDto[]
  errores: Record<string, string>
}

export function MetricaConfigForm({
  tipoMetrica,
  configuracion,
  onChange,
  campos,
  operadoresCatalogo,
  errores,
}: MetricaConfigFormProps) {
  const setFiltros = (filtros: FiltroMetricaDto[]) => onChange({ ...configuracion, filtros })

  const filtrosGenerales = (
    <FormField
      label="Filtros"
      help="Los filtros limitan qué registros entran en el cálculo."
      error={errores['configuracion.filtros']}
    >
      <FiltroBuilder
        filtros={configuracion.filtros ?? []}
        onChange={setFiltros}
        campos={campos}
        operadoresCatalogo={operadoresCatalogo}
      />
    </FormField>
  )

  switch (tipoMetrica) {
    case 'CONTEO':
      return filtrosGenerales

    case 'PORCENTAJE':
      return (
        <>
          <FormField label="Filtros del numerador" error={errores['configuracion.numerador']}>
            <FiltroBuilder
              filtros={configuracion.numerador?.filtros ?? []}
              onChange={(filtros) => onChange({ ...configuracion, numerador: { filtros } })}
              campos={campos}
              operadoresCatalogo={operadoresCatalogo}
            />
          </FormField>
          <FormField label="Filtros del denominador" error={errores['configuracion.denominador']}>
            <FiltroBuilder
              filtros={configuracion.denominador?.filtros ?? []}
              onChange={(filtros) => onChange({ ...configuracion, denominador: { filtros } })}
              campos={campos}
              operadoresCatalogo={operadoresCatalogo}
            />
          </FormField>
        </>
      )

    case 'PROMEDIO':
    case 'SUMA':
      return (
        <>
          <FormField label="Campo de valor (numérico)" error={errores['configuracion.campoValor']}>
            <select
              value={configuracion.campoValor ?? ''}
              onChange={(e) => onChange({ ...configuracion, campoValor: e.target.value || null })}
            >
              <option value="">— seleccionar campo —</option>
              {campos
                .filter((c) => c.utilizableComoCampoValor)
                .map((c) => (
                  <option key={c.codigo} value={c.codigo}>
                    {c.etiqueta} ({c.tipoDato})
                  </option>
                ))}
            </select>
          </FormField>
          {filtrosGenerales}
        </>
      )

    case 'DISTRIBUCION':
      return (
        <>
          <FormField label="Campo de agrupación" error={errores['configuracion.campoAgrupacion']}>
            <select
              value={configuracion.campoAgrupacion ?? ''}
              onChange={(e) => onChange({ ...configuracion, campoAgrupacion: e.target.value || null })}
            >
              <option value="">— seleccionar campo —</option>
              {campos
                .filter((c) => c.utilizableComoCampoAgrupacion)
                .map((c) => (
                  <option key={c.codigo} value={c.codigo}>
                    {c.etiqueta} ({c.tipoDato})
                  </option>
                ))}
            </select>
          </FormField>
          {filtrosGenerales}
        </>
      )
  }
}
