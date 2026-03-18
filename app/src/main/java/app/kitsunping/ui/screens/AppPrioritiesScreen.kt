package app.kitsunping.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.kitsunping.InstalledAppEntry
import app.kitsunping.TargetPolicyRule
import app.kitsunping.ui.animation.AppMotion
import app.kitsunping.ui.components.CardHeaderWithInfo
import java.util.Locale

private data class AppRuleUiModel(
    val packageName: String,
    val appLabel: String,
    val iconBitmap: Bitmap?,
    val profile: String,
    val priority: String,
    val enabled: Boolean,
    val state: RuleStatus,
    val routerConnected: Boolean
)

private enum class RuleStatus { RUNTIME_SYNCED, PENDING_SYNC, ROUTER_FAILED }

private enum class EditorMode { ADD, EDIT }

private data class TargetTransitionEntry(
    val tsSec: Long,
    val fromState: String,
    val toState: String,
    val reason: String
)

private val appIconFallbackCache = mutableMapOf<String, Bitmap?>()

@Composable
fun AppPrioritiesScreen(
    contentPadding: PaddingValues,
    installedApps: List<InstalledAppEntry>,
    appPolicies: Map<String, TargetPolicyRule>,
    runtimePolicies: Map<String, TargetPolicyRule>,
    syncSummary: List<String>,
    targetState: String,
    targetStateReason: String,
    targetStateHistory: List<String>,
    policyRequest: String,
    routerPaired: Boolean,
    reducedMotionEnabled: Boolean,
    applyInProgress: Boolean,
    onRequestSaveAppPolicy: (String, String, String, Boolean) -> Unit,
    onRequestDeleteAppPolicy: (String) -> Unit,
    onRequestApplyPolicies: () -> Unit
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editorMode by rememberSaveable { mutableStateOf(EditorMode.ADD) }
    var syncLogExpanded by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf("") }
    var selectedProfile by rememberSaveable { mutableStateOf("stable") }
    var selectedPriority by rememberSaveable { mutableStateOf("medium") }
    var selectedEnabled by rememberSaveable { mutableStateOf(true) }

    val normalizedSearch = search.trim().lowercase()
    val appEntryByPackage = remember(installedApps) {
        installedApps.associateBy { it.packageName.lowercase() }
    }
    val appLabelByPackage = remember(installedApps) {
        installedApps.associate { it.packageName.lowercase() to it.label }
    }
    val filteredApps = remember(installedApps, normalizedSearch) {
        installedApps
            .filter {
                if (normalizedSearch.isBlank()) return@filter true
                it.label.lowercase().contains(normalizedSearch) ||
                    it.packageName.lowercase().contains(normalizedSearch)
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    val appCards = appPolicies.entries.sortedWith(
        compareBy<Map.Entry<String, TargetPolicyRule>> {
            appLabelByPackage[it.key.lowercase()].orEmpty().ifBlank { it.key }
        }.thenBy { it.key }
    ).map { (pkg, rule) ->
        val normalizedPackage = pkg.lowercase()
        val appEntry = appEntryByPackage[normalizedPackage]
        val label = appEntry?.label.orEmpty().ifBlank { pkg }
        AppRuleUiModel(
            packageName = pkg,
            appLabel = label,
            iconBitmap = appEntry?.iconBitmap,
            profile = rule.profile,
            priority = rule.priority,
            enabled = rule.enabled,
            state = resolveRuleState(
                routerConnected = routerPaired,
                rule = rule,
                runtimeRule = runtimePolicies[pkg]
            ),
            routerConnected = routerPaired
        )
    }

    val pendingChanges = appCards.count { it.state == RuleStatus.PENDING_SYNC }
    val normalizedTargetState = normalizeTargetTransitionState(targetState)
    val normalizedTargetReason = targetStateReason.ifBlank { "none" }
    val terminalPolicyRequest = policyRequest.ifBlank { "none" }
    val terminalSyncStatus = syncSummary.firstOrNull().orEmpty().ifBlank { "waiting_sync" }
    val parsedTransitionHistory = remember(targetStateHistory) {
        targetStateHistory.mapNotNull { parseTargetTransitionEntry(it) }
    }
    val cardEntries = appCards.map { it }
    val cardRows = remember(cardEntries) {
        cardEntries.chunked(2)
    }
    val stickyVisible = applyInProgress || pendingChanges > 0

    fun openAddEditor() {
        editorMode = EditorMode.ADD
        search = ""
        selectedPackage = ""
        selectedProfile = "stable"
        selectedPriority = "medium"
        selectedEnabled = true
        editorVisible = true
    }

    fun openEditEditor(packageName: String, rule: TargetPolicyRule) {
        editorMode = EditorMode.EDIT
        search = ""
        val normalizedPackage = packageName.trim().lowercase()
        selectedPackage = normalizedPackage
        selectedProfile = rule.profile
        selectedPriority = rule.priority
        selectedEnabled = rule.enabled
        editorVisible = true
    }

    Box(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (stickyVisible) 84.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Profiles Per App",
                        infoText = "Runtime synchronization is global, not per individual app."
                    )
                    Text(
                        text = if (applyInProgress) "Runtime: Syncing..." else "Runtime: Synced",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (routerPaired) "Router: Connected" else "Router: Not Connected ⚠",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (routerPaired) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = "Saved Rules: ${appPolicies.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text("Runtime match", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                            Text("Pending", style = MaterialTheme.typography.labelSmall)
                        }

                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Target transition",
                        infoText = "Runtime target.prop states detected by the daemon."
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Last transitions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (parsedTransitionHistory.isEmpty()) {
                                Text(
                                    text = "No transitions captured yet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                parsedTransitionHistory.take(4).forEachIndexed { index, entry ->
                                    val isCurrent = index == 0
                                    val accentColor = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                        )
                                        Text(
                                            text = "${targetTransitionDisplayLabel(entry.fromState)} -> ${targetTransitionDisplayLabel(entry.toState)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "terminal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "> target.state=${normalizedTargetState}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> policy.request=${terminalPolicyRequest}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> sync=${terminalSyncStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> transitions=${parsedTransitionHistory.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "> reason=${normalizedTargetReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CardHeaderWithInfo(
                        title = "Cards",
                        infoText = "Visual status per rule and router infrastructure state."
                    )
                    if (appCards.isEmpty()) {
                        Text("No rules configured", style = MaterialTheme.typography.bodySmall)
                    }

                    cardRows.forEach { rowCards ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowCards.forEach { cardEntry ->
                                AppRuleCard(
                                    rule = cardEntry,
                                    onEdit = {
                                        openEditEditor(
                                            cardEntry.packageName,
                                            TargetPolicyRule(
                                                profile = cardEntry.profile,
                                                priority = cardEntry.priority,
                                                enabled = cardEntry.enabled
                                            )
                                        )
                                    },
                                    onDelete = {
                                        onRequestDeleteAppPolicy(cardEntry.packageName)
                                    },
                                    onToggleEnabled = { nowEnabled ->
                                        onRequestSaveAppPolicy(
                                            cardEntry.packageName,
                                            cardEntry.profile,
                                            cardEntry.priority,
                                            nowEnabled
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowCards.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (syncSummary.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .animateContentSize(AppMotion.contentSizeSpec(reducedMotionEnabled)),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CardHeaderWithInfo(
                            title = "Last sync",
                            infoText = "Evidence of runtime/router synchronization and application."
                        )
                        Button(
                            onClick = { syncLogExpanded = !syncLogExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (syncLogExpanded) "Hide details" else "View details")
                        }
                        if (syncLogExpanded) {
                            syncSummary.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (editorVisible) {
            AlertDialog(
                onDismissRequest = { editorVisible = false },
                modifier = Modifier.fillMaxWidth(0.96f),
                properties = DialogProperties(usePlatformDefaultWidth = false),
                title = {
                    Text(if (editorMode == EditorMode.ADD) "Add profile" else "Edit profile")
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (editorMode == EditorMode.ADD) {
                                "Select app + profile + priority and save."
                            } else {
                                "Edit profile, priority, and state for this package."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (editorMode == EditorMode.ADD) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = search,
                                        onValueChange = { search = it },
                                        label = { Text("Search app") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (filteredApps.isEmpty()) {
                                        Text(
                                            text = "No apps were found for this search.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier.heightIn(max = 240.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            filteredApps.take(6).forEach { entry ->
                                                val pkg = entry.packageName.lowercase()
                                                val isSelected = selectedPackage.equals(pkg, ignoreCase = true)
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val current = appPolicies[pkg]
                                                            selectedPackage = pkg
                                                            selectedProfile = current?.profile ?: selectedProfile
                                                            selectedPriority = current?.priority ?: selectedPriority
                                                            selectedEnabled = current?.enabled ?: true
                                                        },
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                                        } else {
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                        }
                                                    ),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                        } else {
                                                            MaterialTheme.colorScheme.surface
                                                        }
                                                    ),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Card(
                                                            shape = CircleShape,
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            ),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                                            )
                                                        ) {
                                                            if (entry.iconBitmap != null) {
                                                                Image(
                                                                    bitmap = entry.iconBitmap.asImageBitmap(),
                                                                    contentDescription = null,
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .clip(CircleShape),
                                                                    contentScale = ContentScale.Crop
                                                                )
                                                            } else {
                                                                Text(
                                                                    text = entry.label.take(2).uppercase(),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                                )
                                                            }
                                                        }

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = entry.label,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = entry.packageName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        if (isSelected) {
                                                            Icon(
                                                                Icons.Outlined.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedPackage.isNotBlank()) {
                                Text(
                                    text = "Paquete seleccionado: $selectedPackage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = selectedPackage,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Package") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        SelectBoxField(
                            label = "Profile",
                            selectedValue = selectedProfile,
                            options = listOf("stable", "speed", "gaming", "benchmark_gaming", "benchmark_speed"),
                            optionDescriptions = mapOf(
                                "stable" to "Balanced for daily use with consistent priority.",
                                "speed" to "Favorece mayor velocidad y throughput en descargas.",
                                "gaming" to "Prioritizes lower latency for real-time gaming.",
                                "benchmark_gaming" to "Extreme benchmark for the lowest possible ping, with higher risk and instability.",
                                "benchmark_speed" to "Extreme benchmark for maximum throughput, even if ping and power usage increase."
                            ),
                            onSelect = { selectedProfile = it }
                        )

                        SelectBoxField(
                            label = "Prioridad",
                            selectedValue = selectedPriority,
                            options = listOf("low", "medium", "high"),
                            optionDescriptions = mapOf(
                                "low" to "Lower priority to avoid displacing critical traffic.",
                                "medium" to "Medium priority recommended for most users.",
                                "high" to "Maximum priority for responsiveness and sensitivity."
                            ),
                            onSelect = { selectedPriority = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = selectedEnabled, onCheckedChange = { selectedEnabled = it })
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRequestSaveAppPolicy(selectedPackage, selectedProfile, selectedPriority, selectedEnabled)
                            editorVisible = false
                        },
                        enabled = selectedPackage.isNotBlank()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { editorVisible = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = if (stickyVisible) 94.dp else 20.dp)
                .size(56.dp)
                .clickable(onClick = { openAddEditor() }),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (stickyVisible) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (applyInProgress) "Syncing runtime..." else "$pendingChanges Changes Pending",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onRequestApplyPolicies,
                        enabled = pendingChanges > 0 && !applyInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (applyInProgress) "Applying..." else "Apply Now")
                    }
                }
            }
        }
    }
}

@Composable
fun SelectBoxField(
    label: String,
    selectedValue: String,
    options: List<String>,
    optionDescriptions: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (optionDescriptions.isNotEmpty()) {
                Text(
                    text = if (infoVisible) "(i) hide" else "(i)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { infoVisible = !infoVisible }
                )
            }
        }

        if (infoVisible && optionDescriptions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { option ->
                        val description = optionDescriptions[option].orEmpty()
                        Text(
                            text = "${profileDisplayName(option)}: $description",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = profileDisplayName(selectedValue),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("▼", style = MaterialTheme.typography.labelSmall)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(profileDisplayName(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun profileDisplayName(value: String): String = when (value.lowercase()) {
    "benchmark", "benchmark_gaming" -> "Bench Ping"
    "benchmark_speed" -> "Bench Speed"
    else -> value
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

@Composable
private fun AppRuleCard(
    rule: AppRuleUiModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconBitmap = remember(rule.packageName, rule.iconBitmap) {
        resolveRuleIconBitmap(context, rule.packageName, rule.iconBitmap)
    }

    val profileContainer = when (rule.profile.lowercase()) {
        "gaming" -> MaterialTheme.colorScheme.tertiaryContainer
        "benchmark", "benchmark_gaming" -> MaterialTheme.colorScheme.secondaryContainer
        "benchmark_speed" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        "speed" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val profileContent = when (rule.profile.lowercase()) {
        "gaming" -> MaterialTheme.colorScheme.onTertiaryContainer
        "benchmark", "benchmark_gaming" -> MaterialTheme.colorScheme.onSecondaryContainer
        "benchmark_speed" -> MaterialTheme.colorScheme.onPrimaryContainer
        "speed" -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val priorityBorderColor = when (rule.priority.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val priorityTextColor = when (rule.priority.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chipStatus = when {
        !rule.routerConnected -> RuleStatus.ROUTER_FAILED
        else -> rule.state
    }

    Card(
        modifier = modifier.heightIn(min = 224.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RuleStatusDot(status = chipStatus)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete rule",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit profile",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Card(
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap.asImageBitmap(),
                            contentDescription = "App icon",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = rule.appLabel.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                        )
                    }
                }

                Text(
                    text = rule.appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (rule.enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.heightIn(max = 28.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = profileContainer)
                ) {
                    Text(
                        text = profileDisplayName(rule.profile),
                        style = MaterialTheme.typography.labelMedium,
                        color = profileContent,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                Card(
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, priorityBorderColor),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = rule.priority.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = priorityTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }

        }
    }
}

@Composable
private fun RuleStatusDot(
    status: RuleStatus,
    modifier: Modifier = Modifier
) {
    val dotColor = when (status) {
        RuleStatus.RUNTIME_SYNCED -> MaterialTheme.colorScheme.primary
        RuleStatus.PENDING_SYNC -> MaterialTheme.colorScheme.tertiary
        RuleStatus.ROUTER_FAILED -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(dotColor)
    )
}

private fun resolveRuleState(
    routerConnected: Boolean,
    rule: TargetPolicyRule,
    runtimeRule: TargetPolicyRule?
): RuleStatus {
    if (!routerConnected) return RuleStatus.ROUTER_FAILED

    val applied = if (rule.enabled) {
        runtimeRule != null && runtimeRule.profile == rule.profile && runtimeRule.priority == rule.priority
    } else {
        runtimeRule == null
    }
    return if (applied) RuleStatus.RUNTIME_SYNCED else RuleStatus.PENDING_SYNC
}

private fun normalizeTargetTransitionState(state: String): String {
    return when (state.trim().uppercase(Locale.ROOT)) {
        "IDLE", "APP_OVERRIDE", "NETWORK_DECISION", "POLICY_APPLIED" -> state.trim().uppercase(Locale.ROOT)
        else -> "IDLE"
    }
}

private fun targetTransitionDisplayLabel(state: String): String {
    return when (normalizeTargetTransitionState(state)) {
        "IDLE" -> "IDLE"
        "APP_OVERRIDE" -> "APP_OVERRIDE"
        "NETWORK_DECISION" -> "NETWORK_DECISION"
        "POLICY_APPLIED" -> "POLICY_APPLIED"
        else -> "IDLE"
    }
}

private fun parseTargetTransitionEntry(raw: String): TargetTransitionEntry? {
    val parts = raw.split('|')
    if (parts.size < 4) return null
    val ts = parts[0].trim().toLongOrNull() ?: 0L
    val from = normalizeTargetTransitionState(parts[1])
    val to = normalizeTargetTransitionState(parts[2])
    val reason = parts.drop(3).joinToString("|").trim().ifBlank { "none" }
    return TargetTransitionEntry(tsSec = ts, fromState = from, toState = to, reason = reason)
}

private fun resolveRuleIconBitmap(
    context: android.content.Context,
    packageName: String,
    preloaded: Bitmap?
): Bitmap? {
    if (preloaded != null) return preloaded
    val cacheKey = packageName.lowercase(Locale.ROOT)
    if (appIconFallbackCache.containsKey(cacheKey)) {
        return appIconFallbackCache[cacheKey]
    }

    val loaded = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        convertDrawableToBitmap(drawable, width = 96, height = 96)
    }.getOrNull()

    appIconFallbackCache[cacheKey] = loaded
    return loaded
}

private fun convertDrawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val bitmap = drawable.bitmap
        return if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
