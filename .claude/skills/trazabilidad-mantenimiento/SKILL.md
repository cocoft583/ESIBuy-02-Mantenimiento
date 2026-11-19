---
name: trazabilidad-mantenimiento
description: Mantiene al día la trazabilidad del mantenimiento de ESIBuy (hallazgo → HU → tasks → commits, tests y PR) en la nota de HU de Obsidian y en Azure Boards. Úsalo al terminar un ciclo importante, al abrir o mergear el PR de una HU y cuando cambie el alcance de una task.
---

Refleja en la documentación lo que de verdad hay en el código y en Azure. Es la adaptación al mantenimiento de la matriz de trazabilidad del proyecto del equipo: aquí no hay `docs/calidad/`, la traza vive en la nota de HU del vault.

Argumentos recibidos (opcional): $ARGUMENTS. La HU, la rama o el número de PR.

## Qué se traza

| De | A | Dónde |
|---|---|---|
| Hallazgo (`C-`, `A-`, `M-`, `B-`) | HU | sección 10 del análisis crítico y «Trazabilidad» de cada HU |
| HU | tasks (`Txx.y`, AB#) | nota de HU y Azure Boards |
| Task | commits y tests | mensajes de commit (`AB#`) y la línea **Estado** de la HU en la nota |
| HU | PR | línea **Estado** y descripción del PR |

## Pasos

1. **Reúne los datos (solo lectura)**:
   - commits de la rama y sus AB#: `git log --format="%h %s | %b" origin/develop..HEAD`;
   - tests añadidos o cambiados: `git diff --name-status origin/develop...HEAD -- backend/src/test frontend/src`;
   - estado del PR: `gh pr list --repo cocoft583/ESIBuy-02-Mantenimiento --head <rama> --state all`;
   - estado de las tasks en Azure (skill `azure-boards-esibuy`).
2. **Propón la línea Estado** de la HU en la nota, debajo de su tabla de tasks. Por ejemplo:
   `**Estado:** hecha en la rama feature/HU01-...-AB424 (7 commits con TDD; pasan los 575 tests). PR #2 mergeado en develop el 18/11.`
3. **Propón los cambios en Azure**: tasks a `Done` cuando el PR esté mergeado; las que no se hagan, a `Removed` con el motivo.
4. Si cambió el alcance (una task quitada, otra nueva), propón también:
   - la fila en la tabla de tasks de la nota (sin renumerar las demás, para que cuadren con Azure);
   - el apartado «Qué cambia en la vX.Y» de la nota, con la fecha;
   - el Word de HU y tasks de `Entregables/`, si el usuario quiere regenerarlo.
5. **Enseña el diff** y espera el OK antes de escribir en el vault o en Azure.

## Reglas

- Manda el código: si la nota y el código no coinciden, se corrige la nota y se pregunta por la diferencia.
- No inventes resultados de tests: el número sale de un `clean verify` real.
- Las notas son del vault del equipo: no cambies el tono ni el formato de lo que ya hay.
