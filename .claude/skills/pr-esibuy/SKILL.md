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
- **Tests**: si el PR toca `backend/`, lanza la suite completa como la CI. En local hay que forzar la cookie segura, porque el `.env` la desactiva para trabajar por http:
  ```powershell
  cd <repo>\backend
  $env:SESSION_COOKIE_SECURE = "true"; .\mvnw.cmd clean verify
  ```
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

## 4. Confirmar y abrir

1. Enseña el título y la descripción completos y espera el OK.
2. Subida de la rama. El push tiene que hacerse con la cuenta **RicardoMM-debug**; la otra cuenta del equipo (RicardoMoragon) solo tiene lectura y da `403`. Si falla:
   ```powershell
   git remote set-url origin https://RicardoMM-debug@github.com/cocoft583/ESIBuy-02-Mantenimiento.git
   git push -u origin <rama>
   ```
3. Abre el PR si el usuario lo pide (`gh pr create --repo cocoft583/ESIBuy-02-Mantenimiento --base develop --head <rama> --title "..." --body-file -`) o déjale el texto para pegarlo en GitHub.
4. Recuerda lo que queda fuera de GitHub: pasar las tasks a Done en Azure cuando se mergee y actualizar el estado de la HU en la nota de Obsidian (skill `trazabilidad-mantenimiento`).
