import type { CampoMetricaMetadataDto, FiltroMetricaDto, OperadorFiltroCatalogoDto } from '../../api/types'
import styles from './FiltroBuilder.module.css'

interface FiltroBuilderProps {
  filtros: FiltroMetricaDto[]
  onChange: (filtros: FiltroMetricaDto[]) => void
  campos: CampoMetricaMetadataDto[]
  operadoresCatalogo: OperadorFiltroCatalogoDto[]
}

function inputValor(
  filtro: FiltroMetricaDto,
  campo: CampoMetricaMetadataDto | undefined,
  operador: OperadorFiltroCatalogoDto | undefined,
  onValor: (valor: unknown) => void,
) {
  if (!operador || (!operador.requiereValor && !operador.requiereLista)) {
    return null
  }

  if (operador.requiereLista) {
    return (
      <input
        type="text"
        placeholder="valores separados por comas"
        value={typeof filtro.valor === 'string' ? filtro.valor : ''}
        onChange={(e) => onValor(e.target.value)}
      />
    )
  }

  switch (campo?.tipoDato) {
    case 'BOOLEANO':
      return (
        <select
          value={filtro.valor === true ? 'true' : filtro.valor === false ? 'false' : ''}
          onChange={(e) => onValor(e.target.value === '' ? null : e.target.value === 'true')}
        >
          <option value="">— valor —</option>
          <option value="true">Verdadero</option>
          <option value="false">Falso</option>
        </select>
      )
    case 'FECHA':
      return (
        <input
          type="date"
          value={typeof filtro.valor === 'string' ? filtro.valor : ''}
          onChange={(e) => onValor(e.target.value || null)}
        />
      )
    case 'ENTERO':
      return (
        <input
          type="number"
          step={1}
          value={typeof filtro.valor === 'number' ? filtro.valor : ''}
          onChange={(e) => onValor(e.target.value === '' ? null : Number(e.target.value))}
        />
      )
    case 'DECIMAL':
      return (
        <input
          type="number"
          step="any"
          value={typeof filtro.valor === 'number' ? filtro.valor : ''}
          onChange={(e) => onValor(e.target.value === '' ? null : Number(e.target.value))}
        />
      )
    default:
      return (
        <input
          type="text"
          value={typeof filtro.valor === 'string' ? filtro.valor : ''}
          onChange={(e) => onValor(e.target.value)}
        />
      )
  }
}

export function FiltroBuilder({ filtros, onChange, campos, operadoresCatalogo }: FiltroBuilderProps) {
  const actualizar = (index: number, cambios: Partial<FiltroMetricaDto>) => {
    onChange(filtros.map((f, i) => (i === index ? { ...f, ...cambios } : f)))
  }

  const quitar = (index: number) => {
    onChange(filtros.filter((_, i) => i !== index))
  }

  const anadir = () => {
    onChange([...filtros, { campo: '', operador: '', valor: null }])
  }

  return (
    <div className={styles.builder}>
      {filtros.length === 0 && <p className={styles.vacio}>Sin filtros.</p>}
      {filtros.map((filtro, index) => {
        const campo = campos.find((c) => c.codigo === filtro.campo)
        const operadoresPermitidos = campo
          ? operadoresCatalogo.filter((o) => campo.operadoresCompatibles.includes(o.codigo))
          : []
        const operador = operadoresCatalogo.find((o) => o.codigo === filtro.operador)

        return (
          <div key={index} className={styles.row}>
            <select
              value={filtro.campo}
              onChange={(e) => actualizar(index, { campo: e.target.value, operador: '', valor: null })}
            >
              <option value="">— campo —</option>
              {campos.map((c) => (
                <option key={c.codigo} value={c.codigo}>
                  {c.etiqueta} ({c.tipoDato})
                </option>
              ))}
            </select>
            <select
              value={filtro.operador}
              disabled={!campo}
              onChange={(e) => actualizar(index, { operador: e.target.value, valor: null })}
            >
              <option value="">— operador —</option>
              {operadoresPermitidos.map((o) => (
                <option key={o.codigo} value={o.codigo}>
                  {o.nombre}
                </option>
              ))}
            </select>
            {inputValor(filtro, campo, operador, (valor) => actualizar(index, { valor }))}
            <button type="button" className={styles.quitar} onClick={() => quitar(index)}>
              Quitar
            </button>
          </div>
        )
      })}
      <button type="button" className={styles.anadir} onClick={anadir}>
        + Añadir filtro
      </button>
    </div>
  )
}
