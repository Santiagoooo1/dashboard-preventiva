import { useMemo, useState } from 'react'
import type { PerfilCampoDto } from '../../api/types'
import { ETIQUETA_CARDINALIDAD, ETIQUETA_ROL, ETIQUETA_TIPO_DATO } from './etiquetasRol'
import styles from './SelectorCampoPerfil.module.css'

interface SelectorCampoPerfilProps {
  campos: PerfilCampoDto[]
  seleccionado: string
  onSeleccionar: (codigo: string) => void
  error?: string
}

/**
 * Elige una columna mostrando lo que de verdad hace falta para decidir: cuántos
 * datos tiene, cuántos faltan y cuántos valores distintos (Fase 6.9I.2).
 *
 * <p>Sin esos tres números, elegir columna es adivinar: una con el 4 % de
 * completitud no sirve para un indicador aunque su nombre sea el correcto.
 *
 * <p>La búsqueda es local sobre una lista ya acotada (las columnas del dataset,
 * decenas como mucho). Los VALORES de las columnas nunca se cargan aquí: de
 * esos, el perfil solo trae unos pocos ejemplos.
 */
export function SelectorCampoPerfil({ campos, seleccionado, onSeleccionar, error }: SelectorCampoPerfilProps) {
  const [busqueda, setBusqueda] = useState('')

  const filtrados = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    if (!texto) return campos
    return campos.filter(
      (c) => c.etiqueta.toLowerCase().includes(texto) || c.codigo.toLowerCase().includes(texto),
    )
  }, [campos, busqueda])

  return (
    <div className={styles.selector}>
      <label className={styles.busqueda}>
        <span className={styles.busquedaEtiqueta}>Buscar columna</span>
        <input
          type="search"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder={`Filtrar entre ${campos.length} columnas…`}
        />
      </label>

      {error && <p className={styles.error}>{error}</p>}

      {filtrados.length === 0 ? (
        <p className={styles.vacio}>Ninguna columna coincide con «{busqueda}».</p>
      ) : (
        <ul className={styles.lista} role="radiogroup" aria-label="Columnas del dataset">
          {filtrados.map((campo) => {
            const activo = campo.codigo === seleccionado
            const sinDatos = campo.valoresInformados === 0

            return (
              <li key={campo.codigo}>
                <label className={`${styles.campo} ${activo ? styles.campoActivo : ''}`}>
                  <input
                    type="radio"
                    name="campoPerfil"
                    checked={activo}
                    onChange={() => onSeleccionar(campo.codigo)}
                  />
                  <span className={styles.contenido}>
                    <span className={styles.cabecera}>
                      <span className={styles.etiqueta}>{campo.etiqueta}</span>
                      <span className={styles.rol}>{ETIQUETA_ROL[campo.rolSugerido] ?? campo.rolSugerido}</span>
                    </span>
                    <span className={styles.codigo}>
                      {campo.codigo} · {ETIQUETA_TIPO_DATO[campo.tipoDato] ?? campo.tipoDato}
                    </span>
                    <span className={styles.cifras}>
                      <span>
                        <strong>{campo.valoresInformados}</strong> con dato
                      </span>
                      <span className={campo.valoresSinDato > 0 ? styles.cifraAviso : undefined}>
                        <strong>{campo.valoresSinDato}</strong> sin dato
                      </span>
                      <span>
                        <strong>{campo.valoresDistintos}</strong> distintos
                      </span>
                      <span className={styles.cardinalidad}>
                        {ETIQUETA_CARDINALIDAD[campo.cardinalidad] ?? campo.cardinalidad}
                      </span>
                      {campo.completitud !== null && <span>{campo.completitud} % completo</span>}
                    </span>
                    {campo.valorMinimo && campo.valorMaximo && (
                      <span className={styles.rango}>
                        De {campo.valorMinimo} a {campo.valorMaximo}
                      </span>
                    )}
                    {/* Ejemplos: el perfil solo trae unos pocos, y nunca de
                        identificadores ni de texto libre (son datos de paciente). */}
                    {campo.valoresEjemplo.length > 0 && (
                      <span className={styles.ejemplos}>
                        {campo.valoresEjemplo.map((valor) => (
                          <span key={valor} className={styles.ejemplo}>
                            {valor}
                          </span>
                        ))}
                      </span>
                    )}
                    {sinDatos && (
                      <span className={styles.avisoSinDatos}>
                        Esta columna no tiene ningún valor informado: solo podrás medir su completitud.
                      </span>
                    )}
                  </span>
                </label>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
