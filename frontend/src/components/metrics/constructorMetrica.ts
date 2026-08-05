import type {
  CampoMetricaMetadataDto,
  ConfiguracionMetricaDto,
  FiltroMetricaDto,
  OperacionDisponibleDto,
  OperadorFiltroCatalogoDto,
  PerfilCampoDto,
  TipoMetrica,
} from '../../api/types'

/**
 * Lógica compartida por los tres modos de creación de métricas (Fase 6.9I.2).
 *
 * Aquí NO se calcula nada clínico: solo se traduce lo que el usuario ha elegido
 * en el formulario a la `ConfiguracionMetricaDto` que entiende el motor. El
 * cálculo vive entero en el backend, y estas funciones existen para que los tres
 * modos produzcan exactamente la misma estructura.
 */

/**
 * En el formulario, el valor de IN/NOT_IN se edita como texto separado por
 * comas; el backend espera una lista. Esta conversión es la misma que usaba
 * `MetricaFormPage` en local y ahora comparten los tres modos.
 */
export function filtrosParaPayload(
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

/** El inverso: una métrica guardada vuelve al formulario con las listas como texto. */
export function filtrosParaFormulario(filtros: FiltroMetricaDto[] | null | undefined): FiltroMetricaDto[] {
  return (filtros ?? []).map((f) => ({
    ...f,
    valor: Array.isArray(f.valor) ? f.valor.join(', ') : f.valor,
  }))
}

/**
 * `infeccionLocalizacionQuirurgica` → `infeccion_localizacion_quirurgica`.
 *
 * El código de una métrica es su identificador estable: se usa en enlaces y en
 * los scripts de verificación, así que conviene que sea legible y previsible en
 * vez de un hash o un contador.
 */
export function aSnakeCase(codigoCampo: string): string {
  return codigoCampo
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
    .toLowerCase()
}

/**
 * Código sugerido, único dentro del dataset.
 *
 * Si `edad_promedio` ya existe, propone `edad_promedio_2`. Nunca devuelve un
 * código repetido en silencio: crear dos métricas con el mismo código dejaría
 * al usuario sin saber cuál está mirando (y el backend lo rechazaría al
 * guardar).
 */
export function sugerirCodigo(
  codigoCampo: string,
  sufijo: string,
  codigosExistentes: string[],
): string {
  const base = `${aSnakeCase(codigoCampo)}${sufijo}`
  const usados = new Set(codigosExistentes.map((c) => c.toLowerCase()))

  if (!usados.has(base)) return base

  let n = 2
  while (usados.has(`${base}_${n}`)) n += 1
  return `${base}_${n}`
}

/** Nombre legible por defecto. El usuario puede cambiarlo. */
export function sugerirNombre(campo: PerfilCampoDto, operacion: OperacionDisponibleDto): string {
  // Algunos nombres de operación ya se leen como frase completa sobre el campo
  // ("Pacientes distintos", "Número de registros"): repetir la etiqueta detrás
  // produciría "Pacientes distintos de Código de paciente".
  if (operacion.codigo === 'CONTEO') return 'Número de registros'
  if (operacion.codigo === 'CONTEO_DISTINTO' && campo.esIdentificadorIndividuo) return 'Pacientes distintos'

  return `${operacion.nombre} de ${campo.etiqueta}`
}

/** Cómo se calculará, en una frase, para la previsualización previa al guardado. */
export function describirCalculo(
  campo: PerfilCampoDto,
  operacion: OperacionDisponibleDto,
  numeroFiltros: number,
): string {
  const partes = [operacion.explicacion]

  if (numeroFiltros > 0) {
    partes.push(
      `Limitado a los registros que cumplen ${numeroFiltros} ${numeroFiltros === 1 ? 'filtro' : 'filtros'}.`,
    )
  }

  // Avisar de la cobertura real: una métrica sobre una columna con la mitad de
  // los valores sin informar no es falsa, pero se lee muy distinto sabiéndolo.
  if (campo.valoresSinDato > 0) {
    partes.push(
      `${campo.etiqueta} está sin informar en ${campo.valoresSinDato} de ${campo.totalRegistros} registros.`,
    )
  }

  return partes.join(' ')
}

/**
 * Traduce la elección del asistente a la configuración del motor.
 *
 * Cada operación necesita su campo en el sitio correcto: las que agregan un
 * valor usan `campoValor`; las que reparten usan `campoAgrupacion`. Enviar el
 * campo en el atributo equivocado haría que el backend lo rechazara por
 * "requiere campoValor", así que la decisión se toma aquí una sola vez.
 */
export function construirConfiguracionDesdeColumna(opciones: {
  operacion: TipoMetrica
  codigoCampo: string
  filtros: FiltroMetricaDto[]
  incluirSinDato: boolean
  topN: number | null
}): ConfiguracionMetricaDto {
  const { operacion, codigoCampo, filtros, incluirSinDato, topN } = opciones

  const config: ConfiguracionMetricaDto = { filtros }

  if (operacion === 'DISTRIBUCION' || operacion === 'CATEGORIA_PRINCIPAL') {
    config.campoAgrupacion = codigoCampo
    // Explícito en ambos sentidos: en clínica "sin documentar" no es "no", y
    // que aparezca o no como categoría propia debe quedar registrado en la
    // métrica, no depender de un valor por defecto que pueda cambiar.
    config.tratamientoNulos = incluirSinDato ? 'INCLUIR_COMO_CATEGORIA' : 'EXCLUIR'
    if (operacion === 'DISTRIBUCION' && topN != null && topN > 0) {
      config.maxCategorias = topN
    }
    return config
  }

  if (operacion === 'CONTEO') {
    // CONTEO no opera sobre un campo: cuenta registros. El campo elegido solo
    // ha servido para llegar hasta aquí.
    return config
  }

  if (operacion === 'PORCENTAJE') {
    // Porcentaje sobre un booleano: "Sí" arriba, "con dato" abajo. Los valores
    // que viajan son técnicos (true / NOT_NULL), nunca las etiquetas "Sí"/"No".
    config.numerador = { filtros: [{ campo: codigoCampo, operador: 'EQ', valor: true }] }
    config.denominador = { filtros: [{ campo: codigoCampo, operador: 'NOT_NULL', valor: null }] }
    return config
  }

  config.campoValor = codigoCampo
  return config
}
