---
name: tdd-hu
description: Guía una HU del mantenimiento de ESIBuy con TDD, ciclo a ciclo, dando el código completo, dónde va cada cosa y los comandos para tests y commits (en el sistema del usuario: Windows, macOS o Linux), sin tocar el código. Úsalo cuando se pida «vamos a hacer la HUxx», «pásame el código por ciclos» o similar.
---

Guía una historia de usuario del mantenimiento con TDD. **El código lo escribe el usuario**: tú lees, diseñas los ciclos y das el código y los comandos listos para copiar. No edites archivos del proyecto salvo que el usuario lo pida expresamente para un paso concreto (p. ej. «el refactor hazlo tú»).

Argumentos recibidos (opcional): $ARGUMENTS. Normalmente, el número de HU.

## 1. Leer la HU (solo lectura)

1. Busca la HU en Azure Boards con la skill `azure-boards-esibuy`: título `HUxx - Mantenimiento - ...`, descripción (historia y trazabilidad), **Acceptance Criteria**, Effort y tasks hijas (`[System.Parent] = <id>`).
2. Lee el código que nombran las tasks y sus tests. Comprueba las líneas reales: las de la nota o la task pueden haberse movido.
3. Mira `.agents/skills/` para las convenciones del stack (`spring-mongodb-backend`, `testing-tdd`, `angular-frontend`, `secure-development`).
4. Si algo no cuadra (la task dice una cosa y el código otra, o un cambio rompe otra funcionalidad), dilo antes de proponer ciclos.

## 2. Rama

Desde `develop` actualizado, con el nombre `feature/HUxx-<descripcion-corta>-AB<id de la HU>`:

```
cd "$(git rev-parse --show-toplevel)"
git switch develop
git pull --ff-only
git switch -c feature/HUxx-descripcion-ABnnn
```

Si hay archivos sin seguimiento de otra HU, avisa: viajan con el cambio de rama y no deben entrar en los commits de esta.

## 3. Diseñar los ciclos

- Un ciclo por comportamiento, en el orden de las tasks. Cada ciclo: 🔴 test que falla → 🟢 código mínimo → refactor si hace falta.
- Si un test pasa a la primera porque el código ya lo hacía bien (el fallo estaba en la configuración o es un caso frontera), dilo claramente: es un test de comportamiento, se commitea como `test - ...`. Añade un test «gemelo» que demuestre que la comprobación funciona (p. ej. log activado → sí aparece).
- Nombres de tests en **inglés** y camelCase (`rejectsMissingKey`), como el resto del repo. Mensajes de error y comentarios, en español, como el código existente.
- Tests de configuración: cargar `application.properties` con `PropertiesLoaderUtils` y comprobar el valor literal (p. ej. `${AES_KEY}` sin valor por defecto).
- Tests de arranque: `ApplicationContextRunner` con `ConfigDataApplicationContextInitializer` y `withPropertyValues`.
- Tests de logs: `OutputCaptureExtension` y `CapturedOutput`.
- Nada de `ReflectionTestUtils` para meter dependencias si se puede pasar por constructor.

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

## 4. Formato de la respuesta

Por cada ciclo, en este orden y sin saltos:

1. **🔴 Rojo**: ruta exacta del archivo (`backend/src/test/java/...`), si es nuevo o existente, y el código. Para archivos existentes, di exactamente dónde va: línea, «antes de la última llave», «sustituye todo el contenido», imports que añadir.
2. Comando del test, siempre con `cd` a una ruta fija, con el wrapper del sistema del usuario (ver «Comandos en Windows, macOS y Linux»):
   ```
   cd "$(git rev-parse --show-toplevel)/backend"
   ./mvnw -q test "-Dtest=NombreTest"        # macOS / Linux
   .\mvnw.cmd -q test "-Dtest=NombreTest"    # Windows (PowerShell)
   ```
   y qué tiene que salir (cuántos fallan o pasan).
3. Commit del rojo (skill `commit-esibuy`): `git add` con rutas explícitas desde la raíz y `git commit -m "ciclo rojo - ..." -m "AB#<task>"`.
4. **🟢 Verde**: mismo nivel de detalle (archivo, líneas antes y después), comando y resultado esperado, y commit `ciclo verde - ...`.

Al final:

- La suite completa como la CI: `SESSION_COOKIE_SECURE=true ./mvnw clean verify` en macOS / Linux, o `$env:SESSION_COOKIE_SECURE = "true"; .\mvnw.cmd clean verify` en PowerShell (el `.env` local pone la cookie insegura y si no falla `CategoryControllerTest`).
- `git push -u origin <rama>` y la PR con la skill `pr-esibuy`.
- Qué tasks quedan cerradas y cuáles no.

## 5. Durante la HU

- Si el usuario pega un error o dice «mira la terminal», léela (`read_terminal`) antes de responder y explica la causa concreta.
- Si pregunta «¿ya está?», compruébalo de verdad: `git log origin/develop..HEAD`, `git status`, rama remota, y si hace falta lanza los tests tú.
- Si ves algo grave fuera de la HU (secretos en el repo, una regresión), avísalo al final con ⚠️, sin mezclarlo en los ciclos.
