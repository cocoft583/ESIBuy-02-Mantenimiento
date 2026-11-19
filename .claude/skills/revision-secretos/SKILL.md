---
name: revision-secretos
description: Revisa el repo o un diff del mantenimiento de ESIBuy en busca de secretos y datos sensibles (credenciales, claves por defecto, tokens en logs, trazas en errores) y dice qué hacer con cada uno. Úsalo antes de commitear configuración, antes de abrir un PR y cuando se sospeche que se ha subido algo sensible.
---

Busca información sensible en el código y la configuración del backend y del frontend. Es solo lectura: informa y propone, no cambia nada sin OK.

Argumentos recibidos (opcional): $ARGUMENTS. Puede ser «diff» (solo lo que cambia frente a `origin/develop`), una ruta o «todo».

## Qué buscar

1. **Credenciales en la configuración**: valores por defecto con secretos en `${VARIABLE:valor}` dentro de `backend/src/main/resources/application*.properties`, sobre todo:
   - URIs de MongoDB con usuario y contraseña (`mongodb+srv://usuario:contraseña@...`);
   - `PASSWORD_PEPPER`, `AES_KEY`, contraseñas de correo (`EMAIL_PWD`), tokens de Sonar o Firebase.

   La regla del proyecto (HU01): los secretos no tienen valor por defecto y el arranque falla si faltan.
2. **Archivos que no deben estar en git**: `backend/.env` o cualquier `.env` (`git ls-files | grep -i env`), claves (`*.pem`, `*.key`, `*.jks`).
3. **Secretos en los logs**: `LOGGER.*` que escriben OTP, tokens de recuperación, contraseñas o el `totpSecret`. Solo pueden salir con un interruptor que esté a `false` por defecto y `true` solo en el perfil `dev` (HU02).
4. **Errores**: `server.error.include-stacktrace` e `include-message` tienen que estar a `never`, y los `@ExceptionHandler` no deben devolver `exception.getMessage()` de excepciones técnicas.
5. **Frontend**: `environment*.ts` con claves o URLs internas; nada de tokens en `localStorage`.
6. **Pipelines**: `azure-pipelines-*.yml` con tokens en claro o en la URL del `git push`.

Búsquedas de partida:

```bash
git grep -nE "mongodb(\+srv)?://[^$\{]*:[^@]*@" -- ':!*.md'
git grep -nE "\$\{[A-Z_]*(KEY|SECRET|PEPPER|PWD|PASSWORD|TOKEN)[A-Z_]*:[^}]+\}" -- backend/src/main/resources
git grep -nE "LOGGER\.(info|warn|debug|error)\(.*(token|otp|code|password|secret)" -i -- backend/src/main/java
git ls-files | grep -iE "(^|/)\.env|\.pem$|\.key$|\.jks$"
```

Para saber si algo ya está en el historial y quién lo subió: `git log -S "<trozo>" --format="%h %an %ad %s" --date=short`.

## Cómo informar

- Una tabla con archivo:línea, qué es, gravedad (crítica si da acceso a datos o cuentas) y qué hacer.
- **Nunca copies el secreto en la respuesta**: tápalo (`usuario:****@`).
- Si un secreto ya está en GitHub, lo primero es **cambiarlo** en el servicio (Atlas, Sonar, correo...). Quitarlo del código no basta, porque sigue en el historial; reescribir el historial de `develop` no se hace sin hablarlo con el equipo.
- Si hace falta código, propón una HU con la skill `hallazgo-mantenimiento` y guíala con `tdd-hu`.

## Situación conocida (18/11/2026)

El commit `9f6b4de` (PR #1) dejó en `develop` la URI de Atlas con usuario y contraseña como valor por defecto de `MONGODB_URI` y un valor por defecto para `PASSWORD_PEPPER`. Pendiente: cambiar la contraseña en Atlas y quitar esos valores por defecto.
