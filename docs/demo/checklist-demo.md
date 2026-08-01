# Checklist de demo — Dashboard Preventiva

## Antes de la demo

- [ ] Docker Desktop en marcha.
- [ ] PostgreSQL levantado (`docker compose up -d` desde la raíz del repo).
- [ ] Backend arrancado y `http://localhost:8080/api/health` responde `200`.
- [ ] Frontend arrancado (`npm run dev` en `frontend/`) y accesible en `http://localhost:5173`.
- [ ] `ilq_demo_mvp.csv` localizado (`docs/demo/ilq_demo_mvp.csv`).
- [ ] Base de datos razonablemente limpia (pocos datasets de prueba visibles en `/datasets`; no
      hace falta estar vacía, pero conviene no tener datasets a medias con nombres confusos).
- [ ] Navegador preparado: pestaña en `http://localhost:5173`, zoom cómodo para proyectar.

## Durante la demo

- [ ] Subir `ilq_demo_mvp.csv` desde "Crear dashboard".
- [ ] Mostrar la detección automática de columnas (tipo y rol clínico).
- [ ] Mostrar los errores detectados (fecha inválida, edad no numérica, HC vacío).
- [ ] Corregir una celda (fecha de cirugía inválida).
- [ ] Rellenar columna (MICROOR. vacío en varias filas).
- [ ] Normalizar columna (formatos de fecha mixtos).
- [ ] Excluir una fila (registro casi vacío).
- [ ] Señalar el caso clínico especial (FECHA ALTA: SIGUE INGRESADO).
- [ ] Importar los datos corregidos.
- [ ] Crear y mostrar el dashboard inicial.
- [ ] Ver la trazabilidad de la importación.
- [ ] Cerrar con el valor del producto: de exportación cruda a dashboard auditable en minutos, sin
      programar.

## Después de la demo

- [ ] Si se creó un dataset de prueba que no se quiere conservar, descartarlo desde "Cancelar
      creación" o desde "Pruebas y borradores" en `/datasets` — nunca borrar directamente en base
      de datos sin confirmarlo antes.
- [ ] Revisar los logs del backend por si algo falló silenciosamente durante la demo.
- [ ] Comprobar que la aplicación sigue respondiendo con normalidad (`/api/health`, `/datasets`)
      después de la demo.
