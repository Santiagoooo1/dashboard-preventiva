/**
 * Cómo se nombran en pantalla los roles analíticos y la cardinalidad
 * (Fase 6.9I.2).
 *
 * El backend habla en enums (`TEXTO_LIBRE`, `ALTA`); el usuario es personal
 * clínico. Estas tablas son la única traducción, para que un mismo concepto no
 * acabe llamándose de dos maneras en dos pantallas.
 */

export const ETIQUETA_ROL: Record<string, string> = {
  IDENTIFICADOR: 'Identificador de paciente',
  BOOLEANO: 'Sí / No',
  CATEGORICO: 'Categoría',
  NUMERICO: 'Número',
  FECHA: 'Fecha',
  TEXTO_LIBRE: 'Texto libre',
}

/** Qué implica cada rol, para el desplegable de interpretación. */
export const AYUDA_ROL: Record<string, string> = {
  IDENTIFICADOR: 'Identifica a la persona. Se cuenta en distintos; nunca se promedia ni se reparte en un gráfico.',
  BOOLEANO: 'Solo puede valer Sí, No o quedar sin documentar.',
  CATEGORICO: 'Un conjunto acotado de valores que se repiten: admite repartos y comparativas.',
  NUMERICO: 'Una magnitud: admite media, mediana, suma, mínimo y máximo.',
  FECHA: 'Una fecha: admite primera, última y evolución en el tiempo.',
  TEXTO_LIBRE: 'Texto con demasiados valores distintos para tratarlo como categoría.',
}

export const ETIQUETA_CARDINALIDAD: Record<string, string> = {
  BAJA: 'Pocos valores',
  MEDIA: 'Valores moderados',
  ALTA: 'Muchos valores',
}

export const ETIQUETA_TIPO_DATO: Record<string, string> = {
  TEXTO: 'Texto',
  ENTERO: 'Número entero',
  DECIMAL: 'Número decimal',
  FECHA: 'Fecha',
  BOOLEANO: 'Sí / No',
}
