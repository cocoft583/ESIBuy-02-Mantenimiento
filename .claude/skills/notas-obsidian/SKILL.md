---
name: notas-obsidian
description: Documenta en el vault de Obsidian del equipo de mantenimiento de ESIBuy las decisiones, explicaciones y cambios de alcance, sin duplicar notas. Úsalo SOLO si el CLAUDE.local.md del usuario configura el vault, o si el usuario lo pide expresamente.
---

Escribe en el vault de Obsidian del usuario las decisiones importantes, las explicaciones que pida y los cambios en las notas del mantenimiento. No forma parte del repo: el vault es aparte.

Argumentos recibidos (opcional): $ARGUMENTS. El tema o el contenido.

## Configuración

Va en el `CLAUDE.local.md` de cada desarrollador (no versionado):

- **Ruta del vault** (p. ej. `C:\Users\<usuario>\Documents\Obsidian\ESIBuy (práctica integrada)`).
- **Carpetas**: `Info/` para notas de trabajo, `Entregables/` para los Word y PDF, `Copias/` para versiones anteriores.
- **Disparo automático**: si se proponen notas solas o solo cuando se piden.

Si no hay configuración y el usuario invoca la skill, pregunta la ruta. No inventes rutas.

## Cómo escribir

- Si el `obsidian` CLI está disponible y Obsidian abierto, úsalo (`search`, `create`, `append`; nunca `overwrite`). Si no, escribe el `.md` directamente en la carpeta del vault.
- **Busca antes** una nota del mismo tema para ampliarla en vez de duplicarla.
- Las notas del vault siguen este formato: frontmatter con `tags`, `fecha` y `estado` (p. ej. `borrador v6.1`), títulos y texto en español, enlaces `[[...]]` a notas que existen y callouts de Obsidian (`> [!note]`).
- Al cambiar una nota con versión, sube el número en `estado` y añade arriba un apartado «Qué cambia en la vX.Y (dd/mm)» con lo que cambia y por qué.
- Antes de un cambio grande, guarda una copia de la versión anterior en `Copias/`.
- Si el cambio afecta a un entregable de `Entregables/` (análisis, alcance, HU y tasks), dilo y pregunta si se regenera.

## Flujo

1. Enseña el borrador (nota nueva o ampliación, carpeta y contenido).
2. Espera un OK explícito.
3. Escribe y di qué se ha escrito y dónde.

## Notas que ya existen

| Nota | Qué tiene |
|---|---|
| `Info/2026-11-10 Análisis crítico del proyecto heredado` | los 41 hallazgos y la trazabilidad hallazgo → HU |
| `Info/2026-11-11 Sprint 3 - historias de usuario (evolutivo y correctivo)` | las 30 HU del alcance acordado con el PO, con tasks, horas y estado |
| `Info/2026-11-11 Fallos en PBI y tasks de Azure (heredado)` | los fallos del backlog del otro equipo |
| `Info/Pendientes (mantenimiento)` | lista viva de lo que queda |

Si un dato (rama, HU, fecha) no se puede obtener, se omite en vez de inventarlo.
