---
name: pr-esibuy
description: Prepara el título y la descripción de un PR del mantenimiento de ESIBuy hacia develop, a partir de los commits, la HU de Azure Boards y los tests reales. Úsalo siempre que se pida abrir, crear o preparar un PR en este repo.
---

Prepara un PR del repositorio `cocoft583/ESIBuy-02-Mantenimiento`. Va a `develop` salvo que el usuario diga otra cosa; nunca a `main` sin indicación expresa.

Argumentos recibidos (opcional): $ARGUMENTS. Pueden traer la HU, la rama o indicaciones libres.

## 1. Comprobaciones (solo lectura)

- **PR existente**: `gh pr list --repo cocoft583/ESIBuy-02-Mantenimiento --head <rama> --state all`. Si hay uno abierto, no abras otro. No edites la descripción de un PR ya mergeado para meter trabajo que no incluye.
- **Frente a develop**: `git fetch origin` y `git log --oneline HEAD..origin/develop`. Si develop ha avanzado, avísalo antes de seguir.
- **Qué entra**: `git log --oneline --no-merges origin/develop..HEAD` y `git diff --name-status origin/develop...HEAD`. Si se cuela algo que no es de la HU, avísalo.
- **Tests**: si el PR toca `backend/`, lanza la suite completa como la CI, desde `backend/`. En local hay que forzar la cookie segura, porque el `.env` la desactiva para trabajar por http (variantes por sistema en «Comandos en Windows, macOS y Linux»):
  - macOS / Linux: `SESSION_COOKIE_SECURE=true ./mvnw clean verify`
  - Windows (PowerShell): `$env:SESSION_COOKIE_SECURE = "true"; .\mvnw.cmd clean verify`
  Tiene que acabar en `BUILD SUCCESS` con «All coverage checks have been met». No abras el PR con tests en rojo.
- **Secretos**: pasa la skill `revision-secretos` sobre el diff antes de abrir el PR.

## 2. Información

- **HU y tasks**: el AB# de los commits y el de la rama (`feature/HUxx-...-AB<pbi>`). Lee la HU y sus tasks en Azure Boards con la skill `azure-boards-esibuy`.
- **Historial TDD**: qué ciclos hubo, cuáles pasaron sin rojo real y por qué.

## 3. Título y descripción

- **Título**: `HUxx - <título de la HU> (AB#<pbi>)`.
- **Descripción**, con estos apartados en markdown:
  - `## Qué cambia`: el problema que había (con el hallazgo del análisis, p. ej. C-03) y qué se ha cambiado, en prosa.
  - `## Tests`: cómo se ha hecho (TDD, un ciclo por commit) y una lista corta de las clases de test y qué cubren. Termina con el resultado real de `clean verify` (número de tests y cobertura), nunca uno supuesto.
  - `## Cómo probarlo en local` (si cambia algo para el equipo: variables nuevas, perfiles, pasos).
  - `## Work items`: todos los AB# (la HU y sus tasks) en una línea, para que Azure enlace el PR.
- Escríbela como la escribiría alguien del equipo: sin frases hechas, sin negritas decorativas y sin guiones largos. Si el usuario tiene instalada la skill `humanizer` en su Claude, pásala por ella en modo embebido.

## Comandos en Windows, macOS y Linux

Da los comandos de forma que valgan en todos los sistemas:

- `git`, `gh`, `npm` y `npx` se escriben igual en todos.
- Rutas con `/` (PowerShell también las acepta) y, para ir a la raíz del repo, `cd "$(git rev-parse --show-toplevel)"`, que funciona en bash, zsh y PowerShell.
- Solo cambian el wrapper de Maven y las variables de entorno. Si no sabes el sistema del usuario, da las dos variantes:

| | macOS / Linux (bash, zsh) | Windows (PowerShell) |
|---|---|---|
| Maven | `./mvnw ...` (si da «permiso denegado», `sh mvnw ...`) | `.\mvnw.cmd ...` |
| Variable para un comando | `SESSION_COOKIE_SECURE=true ./mvnw clean verify` | `$env:SESSION_COOKIE_SECURE = "true"; .\mvnw.cmd clean verify` |

- Pon entre comillas los argumentos `-D` (`"-Dtest=NombreTest"`): PowerShell los necesita y en bash no molestan.

## 4. Confirmar y abrir

1. Enseña el título y la descripción completos y espera el OK.
2. Subida de la rama con la cuenta de GitHub del propio usuario: `git push -u origin <rama>`. Todo el equipo es colaborador del repo con permiso de escritura.
   - Si da `403`, la cuenta con la que se ha autenticado git no tiene permiso: comprueba cuál es con `gh auth status`. Si es la correcta y sigue fallando, hay que pedir al propietario del repo que la añada como colaboradora.
   - No cambies la URL del remoto a la cuenta de otra persona ni subas con credenciales ajenas.
3. Abre el PR si el usuario lo pide (`gh pr create --repo cocoft583/ESIBuy-02-Mantenimiento --base develop --head <rama> --title "..." --body-file -`) o déjale el texto para pegarlo en GitHub.
4. Recuerda lo que queda fuera de GitHub: pasar las tasks a Done en Azure cuando se mergee y actualizar el estado de la HU en la nota de Obsidian (skill `trazabilidad-mantenimiento`).
