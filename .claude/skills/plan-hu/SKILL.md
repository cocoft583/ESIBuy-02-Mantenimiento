---
name: plan-hu
description: Genera el plan de trabajo de una HU del mantenimiento de ESIBuy (rama, ciclos de TDD, archivos, commits y verificación) en docs/planes/hu-<nn>.md, sin ejecutar nada. Úsalo cuando se pida un plan para una HU o una task antes de empezarla.
disable-model-invocation: true
---

Genera el plan de una HU y **déjalo solo como documento**. Esta skill no crea ramas, no escribe código ni tests, no ejecuta nada que modifique el repo y no commitea. Para hacer la HU paso a paso está la skill `tdd-hu`.

Argumentos recibidos (opcional): $ARGUMENTS. El número de HU o de task, material de apoyo e indicaciones libres (p. ej. «solo el ciclo de configuración»).

## Pasos

1. **Reunir la información (solo lectura).**
   - La HU en Azure Boards (skill `azure-boards-esibuy`): historia, trazabilidad, Acceptance Criteria, Effort y tasks.
   - Su entrada en la nota de HU del vault (ruta en el `CLAUDE.local.md`) y el hallazgo que corrige en el análisis crítico.
   - El código y los tests que nombran las tasks, con las líneas reales. El backend está organizado **por dominio** (`auth`, `cart`, `orders`, `products`, `users`...), cada uno con `controller`, `dto`, `model`, `repository` y `service`; no metas capas nuevas (lo dice `.agents/skills/esibuy-workflow`).
   - Ramas y PR relacionados (`git branch -a`, `gh pr list --repo cocoft583/ESIBuy-02-Mantenimiento`) para saber qué está hecho.
   - Si hay un PDF de apoyo, extrae el texto con `pdftotext` o `pypdf`.

2. **Preguntar antes de decidir.** Una sola tanda de preguntas, con opciones y una recomendada, sobre lo ambiguo: nombres que fijará el test (clases, métodos, excepciones), qué casos entran, si una task choca con otra funcionalidad. Lo que se decide con el equipo o el PO no se resuelve: va como `PENDIENTE DE DECIDIR`.

3. **Escribir el plan** en `docs/planes/hu-<nn>.md` (carpeta ignorada por git). Si existe, pregunta antes de sobrescribir. Contenido:
   - **Contexto**: HU, AB#, hallazgo, tasks con su AB# y qué cubre esta fase.
   - **Paso 0 · Rama**, solo como texto: `feature/HUxx-<descripcion>-AB<pbi>` desde `origin/develop`.
   - **Decisiones pendientes**: tabla con la decisión y dónde queda reflejada.
   - **Ciclos**: tabla con orden, test (nombre en inglés), task, tipo (normal, frontera o comportamiento ya existente) y si se espera rojo real. Por ciclo: archivo y ubicación exactos, qué pide el test para el verde y en qué clase y paquete va, y los commits (`ciclo rojo - ...` / `ciclo verde - ...` con `AB#<task>`).
   - **Verificación**: el comando por ciclo (`.\mvnw.cmd -q test "-Dtest=<Clase>"`) con el resultado esperado, y el final como la CI: `$env:SESSION_COOKIE_SECURE = "true"; .\mvnw.cmd clean verify`. Los commits de rojo dejan la suite en rojo a propósito; el PR se abre con todo en verde.
   - **Riesgos y efectos secundarios**: qué más toca el cambio (p. ej. variables de entorno que pasan a ser obligatorias en local o en el despliegue).

4. **Parar.** Di la ruta del plan, resume las decisiones pendientes y espera instrucciones.
