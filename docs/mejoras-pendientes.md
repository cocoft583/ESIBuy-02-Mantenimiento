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

## Un cobro que no se puede devolver deja el pedido a medias

Detectado en: HU11 (AB#463), al hacer T11.1.

Qué pasa: si el pedido no se puede guardar como pagado, `OrderService.savePaidOrder()` llama a `paymentGateway.refund()` antes de borrar el pedido y liberar el stock. Si `refund()` lanza una excepción (una pasarela real caída justo en ese momento), la excepción sale tal cual: el pedido se queda guardado con `estadoPago = PENDIENTE`, el stock sigue reservado y el cliente sí está cobrado. Además el backend responde con un 500 genérico, y el checkout (T11.2) traduce cualquier 5xx distinto de 503 como «no se ha realizado ningún cargo», que en este caso no es verdad.

Mitigación actual: con `SimulatedPaymentGateway`, `refund()` no hace nada y no puede fallar. El pedido pendiente queda en la base de datos, así que hay constancia del cobro.

Arreglo propuesto (backend y frontend, con sus tests):

- En `savePaidOrder()`, capturar el fallo de `refund()`, no borrar el pedido, registrar el error con el `correlationId` y lanzar una excepción propia (por ejemplo `DevolucionPendienteException`) con un mensaje que no diga que no se ha cobrado.
- Responder a esa excepción con un código distinto de los demás y que el checkout enseñe que el cargo se está revisando y que se devolverá.
- Dejar estos pedidos para la conciliación del apartado siguiente.

## Un corte entre el cobro y el segundo guardado deja un pedido con el pago pendiente

Detectado en: HU11 (AB#463), al hacer T11.1.

Qué pasa: `createOrder()` guarda el pedido con el pago `PENDIENTE`, cobra y lo vuelve a guardar como `PAGADO`. Si el servidor se cae (o se reinicia) entre el cobro y ese segundo guardado, el cliente está cobrado y el pedido se queda con `estadoPago = PENDIENTE` y el stock reservado. Ese pedido sale en el historial del cliente, pero no se puede cancelar (`cancelCustomerOrder()` exige el pago `PAGADO`) y no cuenta en las estadísticas, que filtran por pagados. Nadie lo corrige solo.

Mitigación actual: el cliente no recibe respuesta, y el checkout (T11.2) le pide revisar Mis pedidos antes de volver a pagar, así que verá el pedido pendiente y no pagará dos veces.

Arreglo propuesto (backend, con sus tests):

- Que `PaymentGateway.charge()` devuelva una referencia del cobro y guardarla en el pedido, para poder consultar después qué pasó con él.
- Una tarea programada (`@Scheduled`) que busque los pedidos con el pago `PENDIENTE` de hace más de unos minutos y, según lo que diga la pasarela, los marque como pagados o devuelva el cobro, borre el pedido y libere el stock.
