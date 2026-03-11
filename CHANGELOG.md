# Changelog

## 2.0 - Release

### Added
- Stable release for the current Kitsunping app line, published independently from the module versioning.

### Improved
- Unified `priority_target` resolution across runtime policies and app policies before sending `MODULE_STATUS`.
- Correct adaptive launcher icon pipeline with the final transparent asset, stable sizing, and visual centering adjustments.

### Fixed
- Hardened router pairing for legacy deployments and router-side parsing edge cases.
- Included `priority_target` in `MODULE_STATUS` so the router applies the actual user-selected priority.
- Restored channel-switch CTA visibility when the recommended channel differs from the current one.
- Split splash and launcher resources correctly to avoid editing the wrong visual asset.

## 1.1 - Release

### Added
- Module status push to the router through the authenticated router-agent `MODULE_STATUS` endpoint.
- Richer router payload with active profile and target, transport state, scores, and last event details.
- Push rate limiting and deduplication to reduce router-side noise.
- Advanced router state reading with fallback from `router.last` to `router.dni`.

### Improved
- Pairing endpoint fallback:
- First `/cgi-bin/router-pair-validate`.
- Fallback to `/router_agent/pair_validate` for legacy deployments.
- Extended fallback handling for 5xx errors and network failures during pairing.
- Strict validation for the received token with 32-character lowercase hexadecimal format before persisting it.
- Clearer error reporting when Android blocks cleartext HTTP traffic.

### Fixed
- Enabled `android:usesCleartextTraffic="true"` for local router HTTP compatibility.
- Improved compatibility across different router web stack deployments.

## 1.0 - Release

### Added
- Main dashboard with module status details including event, timestamp, interface, and transport.
- Network metric cards for Wi-Fi, Mobile, and Composite views.
- Network profile management from the app for `speed`, `stable`, and `gaming`.
- Quick actions for calibrating now, starting the daemon, restarting the daemon, and checking the daemon PID.
- Advanced tools for reading `daemon.state` and `router.last`, running the Wi-Fi parsing test, and opening `router_*.info` files.
- Dedicated Settings screen with a gear icon in the `TopAppBar`.
- Visual theme selector for System, Light, Dark, and AMOLED modes.
- Local persistence for the selected theme.
- Advanced dialog support for root command output and file reads.

### Improved
- Wi-Fi and Mobile card activity detection based on real `link`, `ip`, and `iface` signals with `transport` fallback.
- Daemon verification flow with `pid` and `cmdline` visibility for troubleshooting.
- Daemon start flow with robust path detection and legacy fallback support.

### Refactor
- Modularized the UI into `ui/screens`, `ui/components`, `ui/model`, and `ui/utils`.
- Extracted data and domain responsibilities into `data/root/RootCommandExecutor`, `data/files/ModuleFileGateway`, `data/settings/UiSettingsStore`, and `domain/events/PolicyEventDispatcher`.
- Reduced `MainActivity` responsibilities so it focuses on lifecycle and orchestration.

### Validation
- Verified Kotlin compilation successfully with `:app:compileDebugKotlin`.
