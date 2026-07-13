package app.kitsunping.ui.screens.panels

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kitsunping.ui.screens.SelectBoxField
import app.kitsunping.ui.animation.AppMotion
import app.kitsunping.ui.components.CardHeaderWithInfo
import app.kitsunping.ui.theme.AppThemeMode
import app.kitsunping.ui.utils.displayName

// ─────────────────────────────────────────────────────────────
// 1. Apariencia
// ─────────────────────────────────────────────────────────────

@Composable
fun SettingsThemePanel(
    currentThemeMode: AppThemeMode,
    reducedMotionEnabled: Boolean,
    onRequestThemeMode: (AppThemeMode) -> Unit,
    onRequestReducedMotion: (Boolean) -> Unit
) {
    var themeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val darkThemes = listOf(
        AppThemeMode.KITSUNPING,
        AppThemeMode.DARK,
        AppThemeMode.AMOLED,
        AppThemeMode.TERMINAL,
        AppThemeMode.CRIMSON,
        AppThemeMode.SOLARIZED,
        AppThemeMode.MODERN,
        AppThemeMode.FOREST,
        AppThemeMode.OCEAN,
        AppThemeMode.SUNSET,
        AppThemeMode.MONOCHROME,
        AppThemeMode.DRACULA,
        AppThemeMode.NORD
    )
    val lightThemes = listOf(
        AppThemeMode.LIGHT,
        AppThemeMode.ARCTIC,
        AppThemeMode.ROSE,
        AppThemeMode.MODERN_INVERTED,
        AppThemeMode.PASTEL
    )
    val experimentalThemes = listOf(
        AppThemeMode.SYSTEM,
        AppThemeMode.MONO_BLUEPRINT
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardHeaderWithInfo(
                title = "Apariencia",
                infoText = "Tema visual y preferencias de animación."
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { themeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = currentThemeMode.displayName())
                }
                DropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    listOf(
                        "Oscuros" to darkThemes,
                        "Claros" to lightThemes,
                        "Experimentales" to experimentalThemes
                    ).forEachIndexed { index, (label, themes) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        themes.forEach { themeMode ->
                            DropdownMenuItem(
                                text = { Text(themeMode.displayName()) },
                                onClick = {
                                    onRequestThemeMode(themeMode)
                                    themeMenuExpanded = false
                                }
                            )
                        }
                        if (index != 2) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            SettingsToggleRow(
                label = "Animaciones reducidas",
                checked = reducedMotionEnabled,
                onCheckedChange = onRequestReducedMotion
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 2. Módulo
// ─────────────────────────────────────────────────────────────

@Composable
fun SettingsModulePanel(
    bootCustomProfile: String,
    onnxEnabled: Boolean,
    ipv6CalibrationEnabled: Boolean,
    granularLatencyEnabled: Boolean,
    onRequestOnnxEnabled: (Boolean) -> Unit,
    onRequestBootCustomProfile: (String) -> Unit,
    onRequestIpv6Calibration: (Boolean) -> Unit,
    onRequestGranularLatency: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardHeaderWithInfo(
                title = "Módulo",
                infoText = "Configuración del módulo Kitsunping en el dispositivo."
            )

            SelectBoxField(
                label = "Perfil al iniciar",
                selectedValue = when (bootCustomProfile) {
                    "benchmark" -> "benchmark_gaming"
                    else -> bootCustomProfile
                },
                options = listOf("none", "stable", "speed", "gaming", "benchmark_gaming", "benchmark_speed"),
                optionDescriptions = mapOf(
                    "none" to "Sin perfil forzado al inicio",
                    "stable" to "Fuerza stable al inicio",
                    "speed" to "Fuerza speed al inicio",
                    "gaming" to "Fuerza gaming al inicio",
                    "benchmark_gaming" to "Fuerza benchmark gaming al inicio",
                    "benchmark_speed" to "Fuerza benchmark speed al inicio"
                ),
                onSelect = onRequestBootCustomProfile
            )

            SettingsToggleRow(
                label = "ONNX adaptativo",
                checked = onnxEnabled,
                onCheckedChange = onRequestOnnxEnabled
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            SettingsToggleRow(
                label = "Calibración IPv6",
                checked = ipv6CalibrationEnabled,
                onCheckedChange = onRequestIpv6Calibration
            )

            SettingsToggleRow(
                label = "Métrica granular (P90/P99)",
                checked = granularLatencyEnabled,
                onCheckedChange = onRequestGranularLatency
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 3. Desarrollador
// ─────────────────────────────────────────────────────────────

@Composable
fun SettingsDeveloperPanel(
    developerMode: Boolean,
    reducedMotionEnabled: Boolean,
    lowNetworkSimulationEnabled: Boolean,
    onnxLearningEnabled: Boolean,
    onnxUseDefaultModel: Boolean,
    lowNetworkTestOffset: Int,
    routerFiles: List<String>,
    onRequestDeveloperMode: (Boolean) -> Unit,
    onRequestLowNetworkSimulation: (Boolean) -> Unit,
    onRequestOnnxLearningEnabled: (Boolean) -> Unit,
    onRequestOnnxUseDefaultModel: (Boolean) -> Unit,
    onRequestLowNetworkTestOffset: (Int) -> Unit,
    onRequestCheckDaemonPid: () -> Unit,
    onRequestRefreshRouterFiles: () -> Unit,
    onRequestReadFile: (String, String) -> Unit,
    onRequestViewRouterLast: () -> Unit,
    onRequestOpenRouterLast: () -> Unit,
    onRequestRunCommand: (String, String) -> Unit,
    onRequestModuleIntegrityCheck: () -> Unit
) {
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }
    var lowNetOffsetInput by rememberSaveable { mutableStateOf(lowNetworkTestOffset.toString()) }

    LaunchedEffect(lowNetworkTestOffset) {
        lowNetOffsetInput = lowNetworkTestOffset.toString()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(AppMotion.contentSizeSpec(reducedMotionEnabled)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardHeaderWithInfo(
                title = "Desarrollador",
                infoText = "Modo desarrollador y herramientas de diagnóstico."
            )

            SettingsToggleRow(
                label = "Modo desarrollador",
                checked = developerMode,
                onCheckedChange = onRequestDeveloperMode
            )

            if (developerMode) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // ── Simulación de red ──
                Text(
                    text = "Simulación de red",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                SettingsToggleRow(
                    label = "Simular red baja (divisor=2)",
                    checked = lowNetworkSimulationEnabled,
                    onCheckedChange = onRequestLowNetworkSimulation
                )

                SettingsToggleRow(
                    label = "Aprendizaje ONNX",
                    checked = onnxLearningEnabled,
                    onCheckedChange = onRequestOnnxLearningEnabled
                )

                SettingsToggleRow(
                    label = "Usar modelo ONNX por defecto",
                    checked = onnxUseDefaultModel,
                    onCheckedChange = onRequestOnnxUseDefaultModel
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = lowNetOffsetInput,
                        onValueChange = { raw ->
                            lowNetOffsetInput = raw.filter { it.isDigit() }
                        },
                        label = { Text("LowNet offset") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val value = lowNetOffsetInput.toIntOrNull() ?: 0
                            onRequestLowNetworkTestOffset(value.coerceAtLeast(0))
                        }
                    ) {
                        Text("Aplicar")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // ── Diagnóstico ──
                Text(
                    text = "Diagnóstico",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = onRequestModuleIntegrityCheck,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verificar integridad del módulo")
                }

                OutlinedButton(
                    onClick = { toolsExpanded = !toolsExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (toolsExpanded) "Ocultar herramientas" else "Más herramientas")
                }

                if (toolsExpanded) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRequestCheckDaemonPid, modifier = Modifier.weight(1f)) { Text("PID") }
                        Button(onClick = onRequestRefreshRouterFiles, modifier = Modifier.weight(1f)) { Text("Refrescar") }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onRequestRunCommand(
                                    "Test Wi-Fi parsing",
                                    "sh /data/adb/modules/Kitsunping/tools/test_wifi_parsing.sh"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Test parsing") }
                        Button(
                            onClick = { onRequestReadFile("daemon.state", "/data/adb/modules/Kitsunping/cache/daemon.state") },
                            modifier = Modifier.weight(1f)
                        ) { Text("daemon.state") }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRequestViewRouterLast, modifier = Modifier.weight(1f)) { Text("router.last") }
                        Button(onClick = onRequestOpenRouterLast, modifier = Modifier.weight(1f)) { Text("target") }
                    }

                    if (routerFiles.isEmpty()) {
                        Text(text = "Sin archivos del router", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            routerFiles.forEach { path ->
                                val name = path.substringAfterLast('/')
                                OutlinedButton(
                                    onClick = {
                                        if (name == "router.last") onRequestOpenRouterLast() else onRequestReadFile(name, path)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 4. Permisos
// ─────────────────────────────────────────────────────────────

@Composable
fun SettingsPermissionsPanel(onRequestOpenAppSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardHeaderWithInfo(
                title = "Permisos",
                infoText = "Acceso directo a los permisos y configuración del sistema."
            )
            OutlinedButton(onClick = onRequestOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir ajustes de la app")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared helper
// ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
