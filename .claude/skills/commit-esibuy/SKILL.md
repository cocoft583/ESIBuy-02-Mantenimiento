---
name: commit-esibuy
description: Prepara un commit del mantenimiento de ESIBuy con la convención del equipo (ciclos de TDD o tipo(area), siempre con el AB# de la task de Azure Boards). Úsalo en lugar de un git commit genérico en este repo.
disable-model-invocation: true
---

Prepara un commit para el repositorio del mantenimiento de ESIBuy (`cocoft583/ESIBuy-02-Mantenimiento`).

Argumentos recibidos (opcional): $ARGUMENTS. Pueden traer la fase del ciclo, la descripción o el ID de la task en cualquier orden.

## Convención

Hay dos formatos y los dos llevan **el AB# de la task en el cuerpo** (segundo `-m`), para que Azure Boards enlace el commit:

| Cuándo | Asunto | Cuerpo |
|---|---|---|
| Ciclo de TDD | `ciclo rojo - <qué prueba el test>` o `ciclo verde - <qué hace el código>` | `AB#<task>` |
| Refactor de un ciclo | `refactor - <qué mejora>` | `AB#<task>` |
| Test sin rojo real (caso frontera o test de comportamiento que ya existía) | `test - <qué comprueba>` | `AB#<task>` |
| Resto (configuración, documentación, dependencias...) | `tipo(area): descripción`, con `tipo` = `feat`, `fix`, `chore`, `docs`, `refactor` o `test` y `area` = `back`, `front` o `all` | `AB#<id>` |

- Si el commit toca varias tasks, van todos los AB# en el cuerpo: `AB#425 AB#428`.
- La descripción va en español, en minúscula salvo nombres propios, corta y sin punto final.
- Nada de mensajes genéricos («cambios», «arreglos», «wip»).

## Pasos

1. Ejecuta `git status`, `git branch --show-current` y `git diff --staged` (y `git diff` si no hay nada preparado).
2. Comprueba que la rama no es `develop` ni `main`. Si lo es, avisa y para.
3. Saca el AB#:
   - de $ARGUMENTS si lo trae;
   - si no, de los commits anteriores de la rama (`git log origin/develop..HEAD --format=%b`) o del nombre de la rama (`feature/HUxx-...-AB<pbi>` lleva el de la HU, no el de la task);
   - si sigue sin estar claro, pregunta. Nunca inventes un número ni dejes `AB#ID`.
4. Elige el formato según la tabla y redacta el asunto.
5. Prepara solo los archivos de ese cambio, con rutas explícitas (`git add <ruta> <ruta>`). Nunca `git add -A` ni `git add .`: en la carpeta suele haber archivos sin seguimiento de otras tareas.
6. Enseña al usuario el comando completo en PowerShell, desde la raíz del repo:
   ```powershell
   cd C:\ruta\al\repo
   git add <rutas>
   git commit -m "<asunto>" -m "AB#<task>"
   ```
7. Ejecuta el commit solo si el usuario lo pide. Si prefiere lanzarlo él, deja los comandos y para.

## Cosas que han pasado

- `git add backend/...` ejecutado desde dentro de `backend` falla porque la ruta queda duplicada. Por eso los comandos empiezan con un `cd` absoluto a la raíz.
- Para cambiar mensajes de commits que **no** se han subido, `git rebase -i` no está disponible; usa `git filter-branch --msg-filter` sobre `origin/develop..HEAD` con el árbol limpio (guarda antes lo pendiente con `git stash`). Si los commits ya están en GitHub, no se reescriben sin hablarlo con el equipo.
