# Demo MVP — Dashboard Preventiva

Material para enseñar el producto de principio a fin en 5-10 minutos.

## Contenido de esta carpeta

- `demo-mvp.md` — explicación del flujo principal (16 pasos, del alta al dashboard).
- `guion-demo-mvp.md` — guion paso a paso: qué hacer, qué decir, qué resultado esperar.
- `checklist-demo.md` — checklist antes / durante / después de la demo.
- `ilq_demo_mvp.csv` — archivo de datos de demo (15 registros, con errores intencionados).

## Qué archivo usar

`ilq_demo_mvp.csv`. Es un CSV pequeño y realista (columnas de vigilancia de infección de
localización quirúrgica) que **no** entra limpio a la primera a propósito: sirve para demostrar
detección de columnas, corrección de errores, exclusión de filas, relleno y normalización de
columnas, y el registro de trazabilidad.

## Errores incluidos a propósito

| Fila (HC)          | Problema                                              | Qué demuestra                          |
|---------------------|--------------------------------------------------------|-----------------------------------------|
| H002                | `FECHA CIRUGIA` con fecha imposible (`32/13/2026`)      | Corrección de celda                     |
| H003                | `EDAD` no numérica (`NOVENTA`)                          | Corrección de celda                     |
| (fila 5, HC vacío)  | `HC` vacío pero el resto del registro es aprovechable   | Corrección de celda (rellenar el HC)    |
| (fila 10, HC vacío) | `HC` vacío y casi todo lo demás también                 | Excluir fila (no merece corregirse)     |
| H005                | `FECHA ALTA` = `SIGUE INGRESADO`                        | Caso clínico especial, no es un error   |
| H006 / H007         | Fechas válidas pero en formatos distintos entre sí      | Normalizar columna                      |
| H010                | `DURACION MINUTOS` = `NO CONSTA`                        | Valor ausente clínico reconocido        |
| H013 / H014         | `MICROOR.` vacío en dos filas con cultivo positivo      | Rellenar columna (aplica a varias filas)|

El resto de columnas opcionales vacías (fecha de ILQ, localización de infección, cultivo cuando no
hubo infección) son advertencias esperables, no errores: sirven para mostrar que la aplicación
distingue "campo que no aplica" de "dato roto".

## Qué acciones demostrar

Ver el detalle paso a paso en `guion-demo-mvp.md`. En resumen: subir archivo → revisar columnas →
corregir una celda → rellenar una columna → normalizar una columna → excluir una fila → importar →
crear dashboard inicial → ver trazabilidad → ver el dataset creado.

## Qué resultado final se espera

Un dataset "ILQ Demo" con la mayoría de las 15 filas importadas (una excluida a propósito), un
dashboard inicial con indicadores ya generados (tasa de infección, adecuación de profilaxis,
cirugías por mes...), y un historial de trazabilidad con cada corrección registrada.

## Comandos para arrancar

Backend:

```
cd /d C:\Users\santi\dashboard-preventiva
docker compose up -d
cd backend
mvnw.cmd spring-boot:run
```

Frontend:

```
cd /d C:\Users\santi\dashboard-preventiva\frontend
npm run dev
```

Comprobar que el backend responde:

```
curl http://localhost:8080/api/health
```
