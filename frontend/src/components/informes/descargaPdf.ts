import { descargarInformePdf } from '../../api/informesApi'

/**
 * Pide el PDF de un informe y se lo entrega al navegador (Fase 6.9R.2.1).
 *
 * <p>Vive aquí y no en cada página porque el editor y la vista previa hacen lo
 * mismo. Cuando estaba duplicado, un arreglo en una de las dos —el nombre de
 * archivo, el momento de liberar el blob— se olvidaba en la otra y la descarga
 * se comportaba distinto según desde dónde se pulsara.
 *
 * <p>Se descarga como blob en lugar de abrir la URL en una pestaña: si la
 * generación falla, una pestaña nueva enseñaría un JSON de error o una página
 * en blanco, y el usuario se quedaría sin saber qué ha pasado. Así el fallo
 * sube como excepción y lo cuenta la pantalla desde la que se pidió.
 */
export async function descargarPdfDeInforme(informeId: string | number): Promise<void> {
  const { blob, nombre } = await descargarInformePdf(informeId)
  entregarArchivo(blob, nombre ?? 'informe.pdf')
}

function entregarArchivo(blob: Blob, nombre: string): void {
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  // Liberar el objeto en el mismo tick cancela la descarga en algunos
  // navegadores; se deja un margen.
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
