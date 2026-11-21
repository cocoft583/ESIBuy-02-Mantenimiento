# Mejoras pendientes

Problemas detectados durante el mantenimiento que no entran en ninguna HU del sprint. Se apuntan aquí para que no se pierdan; si alguno se aborda, se crea su HU en Azure Boards y se quita de esta lista.

## Cambiar la app de autenticación borra la clave antigua antes de confirmar la nueva

Detectado en: HU08 (AB#452), al hacer el frontend de T08.2.

Qué pasa: `TwoFactorAuthenticationService.setup()` guarda la clave nueva en `totpSecret` en cuanto el código actual es válido, antes de que el usuario confirme la app nueva con `enable()`. Si el usuario empieza el cambio y no lo termina (cierra la pestaña, no escanea el QR...), la app antigua deja de funcionar y la nueva no está configurada. Solo podría entrar con un código de respaldo.

Mitigación actual: la pantalla de «Cambiar app de autenticación» avisa de que la app actual deja de funcionar en cuanto se continúa.

Arreglo propuesto (backend, unos 3 o 4 archivos con sus tests):

- Un campo nuevo en `User`, por ejemplo `pendingTotpSecret`.
- `setup()`, con el 2FA ya activo, guarda la clave en `pendingTotpSecret` y deja `totpSecret` como está.
- `enable()` comprueba el código contra la clave pendiente y, si es válido, la pasa a `totpSecret` y vacía el campo pendiente.
- El login sigue usando `totpSecret`, así que la app antigua funciona hasta que se confirma la nueva.
