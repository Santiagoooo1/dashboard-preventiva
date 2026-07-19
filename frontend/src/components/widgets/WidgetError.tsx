import type { DashboardWidgetDto } from '../../api/types'

interface WidgetErrorProps {
  widget: DashboardWidgetDto
}

export function WidgetError({ widget }: WidgetErrorProps) {
  return <p role="alert">{widget.error?.mensaje ?? 'Error desconocido al calcular este widget.'}</p>
}
