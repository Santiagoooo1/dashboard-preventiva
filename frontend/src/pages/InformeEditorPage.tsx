import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import type {
  BloqueInformeRequestDto,
  BloqueInformeResponseDto,
  ComparacionInteranualRequestDto,
  InformeClinicoResponseDto,
} from '../api/types'
import {
  actualizarBloque,
  actualizarInforme,
  anadirBloque,
  anadirPagina,
  duplicarPagina,
  eliminarBloque,
  eliminarPagina,
  moverBloque,
  obtenerInforme,
  obtenerInformeConResultados,
} from '../api/informesApi'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { FormField } from '../components/FormField'
import { BibliotecaElementos } from '../components/informes/BibliotecaElementos'
import { FormularioComparacion } from '../components/informes/FormularioComparacion'
import { HojaInforme } from '../components/informes/HojaInforme'
import styles from '../components/informes/Informe.module.css'

/**
 * Editor de informes (Fase 6.9Q).
 *
 * <p>Desde la 6.9Q.3 el lienzo es WYSIWYG: los bloques enseñan sus cifras
 * reales mientras se editan. Con un «se calculará después» no había forma de
 * saber si un KPI cabía en un cuarto de hoja o si una tabla de doce meses iba a
 * desbordar el A4, que es justo lo que se está decidiendo aquí.
 *
 * <p>Los resultados salen de {@code GET /informes/:id/resultados}, el mismo
 * agregado que usa la vista previa: una llamada para todo el documento, nunca
 * una por bloque, y ningún cálculo en el navegador. Reordenar o cambiar el
 * ancho no vuelve a pedir nada —el dato no cambia—, así que esas acciones
 * recargan solo la estructura y reaprovechan los resultados ya recibidos.
 *
 * <p>Los bloques se mueven con controles explícitos en vez de arrastrar. No es
 * por comodidad: un drag & drop propio y robusto es bastante código, y la
 * alternativa era una dependencia nueva. Reordenar con flechas funciona con
 * teclado, no se rompe en táctil y hace exactamente lo que dice.
 */
export function InformeEditorPage() {
  const { informeId } = useParams()
  const [informe, setInforme] = useState<InformeClinicoResponseDto | null>(null)
  const [paginaActiva, setPaginaActiva] = useState(0)
  const [titulo, setTitulo] = useState('')
  const [sinGuardar, setSinGuardar] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [comparacionAbierta, setComparacionAbierta] = useState(false)
  // Resultados por id de bloque: sobreviven a una recarga de solo estructura.
  const [resultados, setResultados] = useState<Map<number, BloqueInformeResponseDto>>(new Map())
  const [calculando, setCalculando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  /**
   * Recarga el informe.
   *
   * <p>`conResultados` decide si se piden también las cifras. Añadir o quitar
   * un bloque cambia lo que hay que calcular; moverlo o estrecharlo, no. Los
   * resultados que ya tenemos se guardan por id de bloque y se reaprovechan,
   * así que una recarga de estructura no deja la hoja en blanco.
   */
  const recargar = useCallback(
    async (conResultados: boolean) => {
      if (!informeId) return
      if (conResultados) setCalculando(true)
      try {
        const datos = conResultados
          ? await obtenerInformeConResultados(informeId)
          : await obtenerInforme(informeId)
        setInforme(datos)
        setTitulo(datos.tituloVisible)
        setPaginaActiva((actual) => Math.min(actual, Math.max(0, (datos.paginas?.length ?? 1) - 1)))
        if (conResultados) {
          const mapa = new Map<number, BloqueInformeResponseDto>()
          datos.paginas?.forEach((p) => p.bloques.forEach((b) => mapa.set(b.id, b)))
          setResultados(mapa)
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : 'No se pudo cargar el informe.')
      } finally {
        if (conResultados) setCalculando(false)
      }
    },
    [informeId],
  )

  useEffect(() => {
    recargar(true)
  }, [recargar])

  // Guardado explícito: el autosave sobre una estructura que el usuario está
  // moviendo invita a carreras entre peticiones, y perder un informe a medias
  // es peor que pulsar un botón.
  const guardar = async () => {
    if (!informeId) return
    setGuardando(true)
    setError(null)
    try {
      // Las dos columnas van con el mismo texto; el backend lo vuelve a
      // asegurar por si la petición llega de otro sitio.
      await actualizarInforme(informeId, { nombre: titulo, titulo })
      setSinGuardar(false)
      await recargar(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo guardar.')
    } finally {
      setGuardando(false)
    }
  }

  const paginas = informe?.paginas ?? []
  const pagina = paginas[paginaActiva]

  /**
   * La página que se pinta: la estructura que se está editando, con el
   * resultado ya calculado de cada bloque encima. Así el lienzo del editor y la
   * vista previa reciben exactamente la misma forma de datos y los pinta el
   * mismo renderizador.
   */
  const paginaConResultados = useMemo(() => {
    if (!pagina) return pagina
    return {
      ...pagina,
      bloques: pagina.bloques.map((b) => {
        const calculado = resultados.get(b.id)
        if (!calculado) return b
        return {
          ...b,
          widget: calculado.widget,
          comparacion: calculado.comparacion,
          disponible: calculado.disponible,
          motivoNoDisponible: calculado.motivoNoDisponible,
          datasetNombre: calculado.datasetNombre,
        }
      }),
    }
  }, [pagina, resultados])

  /**
   * `recalcular` distingue lo que cambia los datos de lo que solo cambia el
   * maquetado. Estrechar un bloque o subirlo una posición no altera ninguna
   * cifra: volver a pedirlas sería trabajo tirado en cada clic.
   */
  const conError = async (accion: () => Promise<unknown>, recalcular = true) => {
    setError(null)
    try {
      await accion()
      await recargar(recalcular)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo completar la acción.')
    }
  }

  const anadir = (bloque: BloqueInformeRequestDto) => {
    if (!informeId || !pagina) return
    conError(() => anadirBloque(informeId, pagina.id, bloque))
  }

  // En secuencia, no en paralelo: el orden de los bloques lo asigna el backend
  // al insertar, y lanzar varias peticiones a la vez dejaría el resultado a
  // merced de cuál conteste antes.
  const anadirVarios = (bloques: BloqueInformeRequestDto[]) => {
    if (!informeId || !pagina) return
    conError(async () => {
      for (const bloque of bloques) {
        await anadirBloque(informeId, pagina.id, bloque)
      }
    })
  }

  const anadirComparacion = (config: ComparacionInteranualRequestDto) => {
    setComparacionAbierta(false)
    // Ancho completo: una matriz de años por categorías no se lee en media hoja.
    anadir({ tipoBloque: 'COMPARACION_INTERANUAL', configuracionComparacion: config, ancho: 12 })
  }

  const cambiarAncho = (bloqueId: number, ancho: number) => {
    if (!informeId || !pagina) return
    const bloque = pagina.bloques.find((b) => b.id === bloqueId)
    if (!bloque) return
    conError(() =>
      actualizarBloque(informeId, pagina.id, bloqueId, {
        tipoBloque: bloque.tipoBloque,
        ancho,
        metricaId: bloque.metricaId,
        tipoVisualizacion: bloque.tipoVisualizacion,
        tipoResultadoWidget: bloque.tipoResultadoWidget,
        configuracionWidget: bloque.configuracionWidget,
        configuracionComparacion: bloque.configuracionComparacion,
        contenidoTexto: bloque.contenidoTexto,
        tituloPersonalizado: bloque.tituloPersonalizado,
      }),
      false,
    )
  }

  const editarTexto = (bloqueId: number) => {
    if (!informeId || !pagina) return
    const bloque = pagina.bloques.find((b) => b.id === bloqueId)
    if (!bloque) return
    const nuevo = window.prompt('Texto del bloque:', bloque.contenidoTexto ?? '')
    if (nuevo === null || nuevo.trim() === '') return
    conError(() =>
      actualizarBloque(informeId, pagina.id, bloqueId, {
        tipoBloque: bloque.tipoBloque,
        ancho: bloque.ancho,
        contenidoTexto: nuevo,
      }),
    )
  }

  if (!informe) {
    return (
      <Card title="Informe">
        <ErrorBanner mensaje={error} />
        {!error && <p className="stateLoading">Cargando…</p>}
      </Card>
    )
  }

  return (
    <div>
      <Card title="Informe">
        <ErrorBanner mensaje={error} />
        <FormField
          label="Título del informe"
          help="Identifica el informe en la lista y encabeza cada hoja."
        >
          <input
            value={titulo}
            onChange={(e) => {
              setTitulo(e.target.value)
              setSinGuardar(true)
            }}
          />
        </FormField>
        <div className={styles.acciones}>
          <button type="button" className="btn btnPrimary" disabled={guardando} onClick={guardar}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
          <span className={sinGuardar ? styles.sinGuardar : styles.estadoGuardado}>
            {sinGuardar ? 'Cambios sin guardar' : 'Guardado'}
          </span>
          <Link className="btn btnSecondary" to={`/informes/${informe.id}/vista-previa`}>
            Vista previa
          </Link>
          <Link className="btn btnSecondary" to="/informes">
            Volver
          </Link>
        </div>
      </Card>

      <div className={styles.editor}>
        <BibliotecaElementos
          onAnadir={anadir}
          onAnadirVarios={anadirVarios}
          onAbrirComparacion={() => setComparacionAbierta(true)}
        />

        <div className={styles.lienzo}>
          {comparacionAbierta && (
            <FormularioComparacion
              onAceptar={anadirComparacion}
              onCancelar={() => setComparacionAbierta(false)}
            />
          )}

          <div className={styles.cabeceraPagina}>
            {calculando && <span className={styles.calculando}>Calculando…</span>}
            <span className={styles.acciones}>
              <button
                type="button"
                className="btn btnSecondary"
                disabled={paginaActiva === 0}
                onClick={() => setPaginaActiva((p) => p - 1)}
              >
                ← Anterior
              </button>
              <button
                type="button"
                className="btn btnSecondary"
                disabled={paginaActiva >= paginas.length - 1}
                onClick={() => setPaginaActiva((p) => p + 1)}
              >
                Siguiente →
              </button>
              <button
                type="button"
                className="btn btnSecondary"
                onClick={() => informeId && conError(() => anadirPagina(informeId))}
              >
                + Añadir página
              </button>
              <button
                type="button"
                className="btn btnSecondary"
                onClick={() => informeId && pagina && conError(() => duplicarPagina(informeId, pagina.id))}
              >
                Duplicar
              </button>
              <button
                type="button"
                className="btn btnDanger"
                disabled={paginas.length <= 1}
                onClick={() => informeId && pagina && conError(() => eliminarPagina(informeId, pagina.id))}
              >
                Eliminar página
              </button>
            </span>
          </div>

          {pagina && (
            <HojaInforme
              informe={informe}
              pagina={paginaConResultados}
              numero={paginaActiva + 1}
              total={paginas.length}
              controlesBloque={(bloqueId, indice, total) => (
                <>
                  <button
                    type="button"
                    disabled={indice === 0}
                    title="Subir"
                    onClick={() =>
                      informeId &&
                      conError(() => moverBloque(informeId, pagina.id, bloqueId, indice - 1), false)
                    }
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    disabled={indice >= total - 1}
                    title="Bajar"
                    onClick={() =>
                      informeId &&
                      conError(() => moverBloque(informeId, pagina.id, bloqueId, indice + 1), false)
                    }
                  >
                    ↓
                  </button>
                  <button type="button" title="Un cuarto de ancho" onClick={() => cambiarAncho(bloqueId, 3)}>
                    ¼
                  </button>
                  <button type="button" title="Media página" onClick={() => cambiarAncho(bloqueId, 6)}>
                    ½
                  </button>
                  <button type="button" title="Ancho completo" onClick={() => cambiarAncho(bloqueId, 12)}>
                    1/1
                  </button>
                  {esTexto(pagina.bloques.find((b) => b.id === bloqueId)?.tipoBloque) && (
                    <button type="button" title="Editar texto" onClick={() => editarTexto(bloqueId)}>
                      Editar
                    </button>
                  )}
                  <button
                    type="button"
                    title="Eliminar bloque"
                    onClick={() =>
                      informeId && conError(() => eliminarBloque(informeId, pagina.id, bloqueId))
                    }
                  >
                    ✕
                  </button>
                </>
              )}
            />
          )}
        </div>
      </div>
    </div>
  )
}

function esTexto(tipo?: string): boolean {
  return tipo === 'TITULO' || tipo === 'SUBTITULO' || tipo === 'TEXTO'
}
