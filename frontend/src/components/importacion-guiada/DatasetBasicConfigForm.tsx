import { FormField } from '../FormField'
import { avisoCodigo } from '../../utils/validacion'
import styles from './ImportacionGuiada.module.css'

export interface ConfigDataset {
  nombre: string
  codigo: string
  descripcion: string
}

interface DatasetBasicConfigFormProps {
  valores: ConfigDataset
  onChange: (valores: ConfigDataset) => void
  errores: Record<string, string>
}

export function DatasetBasicConfigForm({ valores, onChange, errores }: DatasetBasicConfigFormProps) {
  const set = (cambios: Partial<ConfigDataset>) => onChange({ ...valores, ...cambios })

  return (
    <div className={styles.form}>
      <FormField label="Nombre del dashboard" help="Nombre visible. Ejemplo: Cardiología 2026." error={errores.nombre}>
        <input value={valores.nombre} onChange={(e) => set({ nombre: e.target.value })} />
      </FormField>
      <FormField
        label="Identificador"
        help="Identificador técnico único. Se sugiere a partir del nombre; puedes editarlo."
        aviso={avisoCodigo(valores.codigo)}
        error={errores.codigo}
      >
        <input value={valores.codigo} onChange={(e) => set({ codigo: e.target.value })} />
      </FormField>
      <FormField label="Descripción (opcional)" help="Explica qué datos contiene este dashboard.">
        <textarea rows={2} value={valores.descripcion} onChange={(e) => set({ descripcion: e.target.value })} />
      </FormField>
    </div>
  )
}
