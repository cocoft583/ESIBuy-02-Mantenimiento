---
name: hallazgo-mantenimiento
description: Registra un fallo o mala práctica nueva del proyecto heredado de ESIBuy como hallazgo (C-, A-, M- o B-) con su severidad, cruzándolo con lo ya documentado, y propone la HU que lo corrige. Úsalo cuando aparezca un error en pruebas manuales, en una revisión o en un PR.
---

Convierte un problema encontrado en el proyecto heredado en un hallazgo documentado y, si hace falta, en una HU. Es solo lectura hasta que el usuario confirma.

Argumentos recibidos (opcional): $ARGUMENTS. La descripción del problema, una captura o los pasos para reproducirlo.

## Dónde está la documentación

En el vault de Obsidian del equipo (la ruta la da el `CLAUDE.local.md` de cada uno; si no está, pregúntala):

- `Info/2026-11-10 Análisis crítico del proyecto heredado.md`: hallazgos por severidad (secciones 3 a 6) y la trazabilidad hallazgo → HU (sección 10).
- `Info/2026-11-11 Sprint 3 - historias de usuario (evolutivo y correctivo).md`: las HU del alcance acordado con el PO, con sus tasks.
- `Info/Pendientes (mantenimiento).md`: lo que queda por hacer.

## Severidad

| ID | Severidad | Criterio |
|---|---|---|
| `C-nn` | 🔴 Crítica | permite tomar cuentas, filtra secretos o rompe una funcionalidad principal |
| `A-nn` | 🟠 Alta | fallo de seguridad o de negocio explotable o visible para el usuario |
| `M-nn` | 🟡 Media | mala práctica con riesgo real, pero acotado |
| `B-nn` | 🟢 Baja | deuda técnica o de mantenibilidad |

Clasifica también con el OWASP Top 10 2025 si es de seguridad (skill `owasp-top-10`).

## Pasos

1. **Reproduce o localiza la causa** en el código (skill `investigate-first`): archivo:línea y por qué pasa. Si no se puede reproducir, dilo.
2. **Cruza con lo que ya hay**: busca en el análisis y en las HU si el problema ya está (mismo síntoma o misma causa). Si ya está, di cuál es y no lo dupliques; como mucho, propón ampliar su descripción.
3. **Propón el hallazgo**: siguiente número libre de su severidad, título corto, Dónde (archivo:línea), Riesgo y Propuesta, con el mismo formato que los demás del análisis.
4. **Traza al backlog heredado** si se puede: qué PBI del otro equipo debió cubrirlo (la nota `2026-11-11 Fallos en PBI y tasks de Azure (heredado)`). Si no lo sabes, «PBI por confirmar».
5. **Propón la HU** si no hay una que lo cubra: título, rol («Como usuario/vendedor/administrador quiero...»; no hay más roles), criterios de aceptación funcionales, tasks `Txx.y - Capa · Funcionalidad · qué` y horas.
6. **Espera el OK** antes de escribir en el vault o en Azure Boards (skill `azure-boards-esibuy`). Las HU nuevas solo entran en el sprint si el PO las acepta: márcalo como pendiente de decidir.

## Reglas

- No inventes líneas de código, PBI ni IDs de Azure.
- El análisis describe el proyecto **heredado**; lo que meta el equipo de mantenimiento con un fallo se anota igual, diciendo el commit que lo introdujo.
