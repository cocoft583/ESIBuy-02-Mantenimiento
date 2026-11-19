# Skills de Claude Code del mantenimiento de ESIBuy

Claude Code carga las skills de esta carpeta al abrir el repo. Las de `.agents/skills/` y `.github/skills/` son las del equipo de origen para Codex y Copilot (convenciones de Angular, Spring Boot con MongoDB, seguridad, Sonar, TDD y diseño); las skills de aquí las citan cuando hace falta en lugar de copiarlas.

## Flujo del mantenimiento

| Skill | Para qué | Origen |
|---|---|---|
| `tdd-hu` | hacer una HU ciclo a ciclo con TDD, con el código y los comandos listos para copiar | nueva |
| `plan-hu` | dejar por escrito el plan de una HU en `docs/planes/` sin ejecutar nada | adaptada de `plan-task` (ESIBuy del equipo) |
| `commit-esibuy` | commits con la convención del equipo y su `AB#` | adaptada de `commit-esibuy` |
| `pr-esibuy` | título y descripción del PR hacia `develop` | adaptada de `pr-esibuy` |
| `azure-boards-esibuy` | leer y actualizar HU y tasks en Azure Boards (RicardoMoragon0873 / EsiBuy-02) | nueva |
| `trazabilidad-mantenimiento` | hallazgo → HU → tasks → commits, tests y PR en la nota de HU y en Azure | adaptada de `matriz-trazabilidad` |
| `hallazgo-mantenimiento` | registrar un fallo nuevo como hallazgo C/A/M/B y proponer su HU | nueva |
| `revision-secretos` | buscar credenciales, claves por defecto y datos sensibles en logs y errores | nueva |
| `notas-obsidian` | documentar decisiones y explicaciones en el vault de Obsidian de cada uno (vault y carpetas en su `CLAUDE.local.md`) | adaptada de `notas-obsidian` |

## Seguridad, calidad y código en general

| Skill | Para qué | Origen |
|---|---|---|
| `owasp-top-10` | clasificar y corregir riesgos con el OWASP Top 10 2025 | `microsoft/hve-core` (ver `skills-lock.json`) |
| `semgrep` | análisis estático con Semgrep | `trailofbits/skills` (ver `skills-lock.json`) |
| `code-review-and-quality` | revisión de código en cinco ejes | `addyosmani/agent-skills` (ver `skills-lock.json`) |
| `java-springboot` | buenas prácticas de Spring Boot | `github/awesome-copilot` (ver `skills-lock.json`) |
| `investigate-first` | diagnosticar un fallo antes de tocar código | `JuliusBrussee/caveman` |
| `surgical-patch` | arreglos pequeños en la capa responsable, con prueba de regresión | `JuliusBrussee/caveman` |
| `safe-refactor` | refactorizar sin cambiar el comportamiento | `JuliusBrussee/caveman` |
| `verify-and-stop` | comprobar que algo cumple sin ampliar el alcance | `JuliusBrussee/caveman` |

## Configuración personal

Lo que depende de cada uno va en `CLAUDE.local.md`, que no se sube: la ruta del vault de Obsidian y si se proponen notas de forma automática.
