# Checklist de defensa

Complementa (no sustituye) a `docs/demo/checklist-demo.md`, con foco en la parte de defensa/
presentación además de la técnica.

## Antes de la defensa

- [ ] Docker Desktop arrancado.
- [ ] Backend arrancado y `http://localhost:8080/api/health` responde `200`.
- [ ] Frontend arrancado y abierto en `http://localhost:5173`.
- [ ] Dataset demo (`DEMO_MVP_ILQ_part2`) visible y activo en `/datasets` — es la red de seguridad
      de toda la demo (ver `demo-en-7-minutos.md`).
- [ ] `docs/demo/ilq_demo_mvp.csv` localizado y accesible desde el selector de archivos (ten una
      copia también en el escritorio por si la ruta del repo no está a mano).
- [ ] Navegador limpio: sin pestañas ni barras de marcadores que distraigan, sin extensiones
      visibles innecesarias.
- [ ] Zoom del navegador adecuado para proyector (probar legibilidad desde el fondo de la sala si
      es posible).
- [ ] Pestañas preparadas: Inicio, `/crear-dashboard`, `/datasets` (con `DEMO_MVP_ILQ_part2` ya
      localizado) — no perder tiempo navegando en directo.
- [ ] Terminales minimizadas o cerradas salvo la necesaria para reiniciar backend/frontend si algo
      falla.
- [ ] Backup del guion: `guion-defensa.md`, `demo-en-7-minutos.md` y
      `preguntas-y-respuestas.md` impresos o en un segundo dispositivo, por si el proyector falla.
- [ ] Plan B decidido de antemano: si la demo en vivo falla, saltar directamente a
      `DEMO_MVP_ILQ_part2` y seguir la defensa desde ahí (ver detalle en `demo-en-7-minutos.md`).

## Durante la defensa

- [ ] No improvisar rutas: seguir `demo-en-7-minutos.md` bloque a bloque, no explorar pantallas que
      no estén en el guion.
- [ ] No pulsar "Archivar" sobre ningún dataset durante la demo.
- [ ] No borrar ni descartar ningún dataset durante la demo (ni el demo ni, sobre todo, ninguno de
      los datasets reales preexistentes).
- [ ] No abrir pantallas de administración (Campos, Plantillas, Métricas, Paneles en modo edición)
      salvo que se explique explícitamente por qué se entra ahí.
- [ ] Enseñar el dashboard final pronto (minuto 5-6 del guion cronometrado): es la parte que más
      impacto visual da, no dejarla para el último momento por si el tiempo aprieta.
- [ ] Reconocer las limitaciones cuando surjan o se pregunten, sin restarles importancia ni
      exagerarlas — ver `preguntas-y-respuestas.md`.

## Después

- [ ] Enseñar la documentación (`README.md`, `docs/estado-mvp.md`) si preguntan cómo está
      documentado el proyecto.
- [ ] Enseñar los tests de backend (`backend/src/test/java/...`, 17 tests) si preguntan cómo se
      verifica que funciona.
- [ ] Enseñar `docs/arquitectura.md` (y el diagrama `docs/Diagrama.png`) si preguntan por el diseño
      técnico.
- [ ] Responder preguntas apoyándose en `preguntas-y-respuestas.md` — no hace falta memorizarlo,
      basta con tenerlo a mano y conocer dónde está cada respuesta.
