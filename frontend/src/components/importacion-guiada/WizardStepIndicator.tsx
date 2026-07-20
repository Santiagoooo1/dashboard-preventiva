import styles from './ImportacionGuiada.module.css'

export const PASOS_WIZARD = [
  { clave: 'subir', etiqueta: 'Subir archivo' },
  { clave: 'columnas', etiqueta: 'Columnas' },
  { clave: 'configuracion', etiqueta: 'Nombre' },
  { clave: 'creando', etiqueta: 'Importar' },
  { clave: 'resultado', etiqueta: 'Resultado' },
] as const

export type ClaveWizard = (typeof PASOS_WIZARD)[number]['clave']

interface WizardStepIndicatorProps {
  actual: ClaveWizard
}

export function WizardStepIndicator({ actual }: WizardStepIndicatorProps) {
  const indiceActual = PASOS_WIZARD.findIndex((p) => p.clave === actual)

  return (
    <ol className={styles.pasos}>
      {PASOS_WIZARD.map((paso, i) => {
        const completado = i < indiceActual
        const activo = i === indiceActual
        return (
          <li
            key={paso.clave}
            className={`${styles.paso} ${activo ? styles.pasoActivo : ''} ${completado ? styles.pasoCompletado : ''}`}
            aria-current={activo ? 'step' : undefined}
          >
            <span className={styles.pasoNumero}>{completado ? '✓' : i + 1}</span>
            {paso.etiqueta}
          </li>
        )
      })}
    </ol>
  )
}
