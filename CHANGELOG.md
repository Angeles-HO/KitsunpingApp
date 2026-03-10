# Changelog

## 2026-02-20

### Añadido
- Envío de estado del módulo al router (`MODULE_STATUS`) usando endpoint autenticado del router-agent.
- Payload enriquecido hacia router con perfil activo/target, estado de transporte, puntajes y último evento.
- Control de frecuencia y deduplicación del push al router para reducir ruido.
- Compatibilidad de lectura avanzada de router con fallback de `router.last` a `router.dni`.

### Mejorado
- Pairing con fallback de endpoint:
	- primero `/cgi-bin/router-pair-validate`
	- fallback a `/router_agent/pair_validate` para despliegues legacy.
- Fallback ampliado ante errores 5xx y fallos de red durante pairing.
- Validación estricta del token recibido (`hex lowercase`, 32 caracteres) antes de guardar.
- Mejor mensaje de error cuando Android bloquea tráfico HTTP cleartext.

### Fix
- Activado `android:usesCleartextTraffic="true"` para compatibilidad con routers locales por HTTP.
- Ajustes de robustez para mantener compatibilidad en distintos stacks web del router.

## 2026-02-13

### Añadido
- Dashboard principal con estado del módulo: evento, timestamp, interfaz y transporte.
- Tarjetas de métricas de red para Wi-Fi, Mobile y Composite.
- Gestión de perfiles de red desde la app (`speed`, `stable`, `gaming`).
- Acciones rápidas:
	- Calibrar ahora.
	- Iniciar daemon.
	- Reiniciar daemon.
	- Verificar PID del daemon.
- Herramientas avanzadas:
	- Lectura de `daemon.state` y `router.last`.
	- Ejecución de prueba de parsing Wi-Fi.
	- Apertura de archivos `router_*.info`.
- Pantalla de Ajustes separada con icono de tuerca en `TopAppBar`.
- Selector de tema visual:
	- Sistema
	- Claro
	- Oscuro
	- AMOLED
- Persistencia del tema seleccionado en almacenamiento local.
- Diálogo avanzado para mostrar resultados de comandos root y lectura de archivos.

### Mejorado
- Detección de estado activo de tarjetas Wi-Fi/Mobile basada en señales reales (`link`, `ip`, `iface`), con fallback por `transport`.
- Flujo de verificación del daemon mostrando `pid` y `cmdline` del proceso para diagnóstico.
- Inicio de daemon con rutas robustas (ruta nueva + fallback legacy).

### Refactor
- Modularización de la UI en capas:
	- `ui/screens`
	- `ui/components`
	- `ui/model`
	- `ui/utils`
- Extracción de responsabilidades de datos/dominio:
	- `data/root/RootCommandExecutor`
	- `data/files/ModuleFileGateway`
	- `data/settings/UiSettingsStore`
	- `domain/events/PolicyEventDispatcher`
- Reducción de responsabilidades de `MainActivity` para enfocarla en ciclo de vida y orquestación.

### Validación
- Compilación Kotlin verificada correctamente con `:app:compileDebugKotlin`.
