import { useEffect, useMemo, useState } from 'react'
import type {
  CampoMetricaMetadataDto,
  ComparacionInteranualRequestDto,
  DatasetClinicoResponseDto,
  Granularidad,
  TipoComparacionInteranual,
} from '../../api/types'
import { listarDatasets } from '../../api/datasetApi'
import { obtenerMetadataMetricas } from '../../api/metricasApi'
import { Card } from '../Card'
import { FormField } from '../FormField'
import { SelectorDatasets } from '../comparacion/SelectorDatasets'
import {
  ETIQUETA_COMPARACION,
  comparacionesPara,
  conceptosComunes,
} from '../comparacion/conceptosComparables'
import styles from './Informe.module.css'

interface FormularioComparacionProps {
  onAceptar: (config: ComparacionInteranualRequestDto) => void
  onCancelar: () => void
}

/**
 * Configura una comparación entre años para insertarla en un informe
 * (Fase 6.9Q.1).
 *
 * <p>Reutiliza el selector de datasets y las reglas de conceptos comparables de
 * `/comparar-anios`: son las mismas decisiones clínicas, y tenerlas duplicadas
 * acabaría ofreciendo opciones distintas en cada pantalla.
 *
 * <p>Solo guarda la configuración. El resultado lo calcula la vista previa con
 * el mismo motor de siempre.
 */
export function FormularioComparacion({ onAceptar, onCancelar }: FormularioComparacionProps) {
  const [datasets, setDatasets] = useState<DatasetClinicoResponseDto[]>([])
  const [seleccionados, setSeleccionados] = useState<number[]>([])
  const [camposPorDataset, setCamposPorDataset] = useState<Map<number, CampoMetricaMetadataDto[]>>(new Map())
  const [codigo, setCodigo] = useState('')
  const [tipo, setTipo] = useState<TipoComparacionInteranual>('TASA')
  const [granularidad, setGranularidad] = useState<Granularidad>('MES')

  useEffect(() => {
    listarDatasets().then(setDatasets).catch(() => setDatasets([]))
  }, [])

  useEffect(() => {
    const pendientes = seleccionados.filter((id) => !camposPorDataset.has(id))
    if (pendientes.length === 0) return
    Promise.all(pendientes.map((id) => obtenerMetadataMetricas(id).then((m) => [id, m.campos] as const)))
      .then((cargados) => {
        setCamposPorDataset((actual) => {
          const siguiente = new Map(actual)
          cargados.forEach(([id, campos]) => siguiente.set(id, campos))
          return siguiente
        })
      })
      .catch(() => undefined)
  }, [seleccionados, camposPorDataset])

  const comunes = useMemo(
    () => conceptosComunes(seleccionados, camposPorDataset),
    [seleccionados, camposPorDataset],
  )

  const campoElegido = comunes.find((c) => c.codigo === codigo)
  const opcionesTipo = useMemo(
    () => (campoElegido ? comparacionesPara(campoElegido.tipoDato) : []),
    [campoElegido],
  )

  useEffect(() => {
    if (opcionesTipo.length > 0 && !opcionesTipo.includes(tipo)) {
      setTipo(opcionesTipo[0])
    }
  }, [opcionesTipo, tipo])

  const puedeAceptar = seleccionados.length > 0 && codigo !== ''

  return (
    <Card title="Añadir comparación entre años">
      <FormField label="Datasets a comparar">
        <SelectorDatasets
          datasets={datasets}
          seleccionados={seleccionados}
          onAlternar={(id) =>
            setSeleccionados((a) => (a.includes(id) ? a.filter((x) => x !== id) : [...a, id]))
          }
          onLimpiar={() => setSeleccionados([])}
        />
      </FormField>

      <FormField
        label="Variable"
        help={
          seleccionados.length > 1
            ? 'Solo se ofrecen los conceptos que existen, con el mismo tipo, en todos los seleccionados.'
            : undefined
        }
      >
        <select value={codigo} onChange={(e) => setCodigo(e.target.value)} disabled={comunes.length === 0}>
          <option value="">
            {seleccionados.length === 0 ? 'Elige antes los datasets' : 'Selecciona una variable'}
          </option>
          {comunes
            .filter((c) => comparacionesPara(c.tipoDato).length > 0)
            .map((c) => (
              <option key={c.codigo} value={c.codigo}>
                {c.etiqueta}
              </option>
            ))}
        </select>
      </FormField>

      <FormField label="Qué comparar">
        <select
          value={tipo}
          onChange={(e) => setTipo(e.target.value as TipoComparacionInteranual)}
          disabled={opcionesTipo.length <= 1}
        >
          {opcionesTipo.map((t) => (
            <option key={t} value={t}>
              {ETIQUETA_COMPARACION[t]}
            </option>
          ))}
        </select>
      </FormField>

      <FormField label="Granularidad">
        <select value={granularidad} onChange={(e) => setGranularidad(e.target.value as Granularidad)}>
          <option value="MES">Mes</option>
          <option value="TRIMESTRE">Trimestre</option>
          <option value="ANIO">Año</option>
        </select>
      </FormField>

      <div className={styles.acciones}>
        <button
          type="button"
          className="btn btnPrimary"
          disabled={!puedeAceptar}
          onClick={() =>
            onAceptar({
              datasetIds: seleccionados,
              codigoCanonico: codigo,
              tipoComparacion: tipo,
              granularidad,
            })
          }
        >
          Añadir al informe
        </button>
        <button type="button" className="btn btnSecondary" onClick={onCancelar}>
          Cancelar
        </button>
      </div>
    </Card>
  )
}
