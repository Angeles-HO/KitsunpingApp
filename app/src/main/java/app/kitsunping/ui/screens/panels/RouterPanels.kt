package app.kitsunping.ui.screens.panels

import android.R as AndroidR
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.kitsunping.ui.components.CardHeaderWithInfo

@Composable
fun RouterStatusPanel(
    paired: Boolean,
    hideIp: Boolean,
    routerIp: String,
    routerId: String,
    hasToken: Boolean,
    policyEventName: String,
    onToggleHideIp: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeaderWithInfo(
                title = "Router status",
                infoText = "Pairing, IP, token, and base bridge data with router."
            )
            Text(text = if (paired) "Paired" else "Unpaired", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hideIp) "IP: Hidden" else "IP: ${routerIp.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleHideIp) {
                    Icon(
                        imageVector = if (hideIp) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = if (hideIp) "Show sensitive data" else "Hide sensitive data"
                    )
                }
            }
            Text(
                text = if (hideIp) "Router ID: Hidden" else "Router ID: ${routerId.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(text = "Token: ${if (hasToken) "OK" else "No"}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Daemon event: $policyEventName", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun RouterPairingPanel(
    ipInput: String,
    onIpInputChange: (String) -> Unit,
    showIpInput: Boolean,
    onToggleShowIpInput: () -> Unit,
    suggestedRouterIp: String,
    onAutoDetect: () -> Unit,
    codeInput: String,
    onCodeInputChange: (String) -> Unit,
    showCodeInput: Boolean,
    onToggleShowCodeInput: () -> Unit,
    onRequestScanQr: () -> Unit,
    onRequestUnpairRouter: () -> Unit,
    onRequestPairRouter: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeaderWithInfo(
                title = "Pairing",
                infoText = "Enter IP and pair_code/URI to pair the router."
            )
            OutlinedTextField(
                value = ipInput,
                onValueChange = onIpInputChange,
                label = { Text("Router IP") },
                visualTransformation = if (showIpInput) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleShowIpInput) {
                        Icon(
                            imageVector = if (showIpInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showIpInput) "Hide IP" else "Show IP"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onAutoDetect, enabled = suggestedRouterIp.isNotBlank()) {
                    Text("Auto-detect")
                }
            }
            OutlinedTextField(
                value = codeInput,
                onValueChange = onCodeInputChange,
                label = { Text("QR URI o pair_code") },
                visualTransformation = if (showCodeInput) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleShowCodeInput) {
                            Icon(
                                imageVector = if (showCodeInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showCodeInput) "Hide pair code" else "Show pair code"
                            )
                        }
                        IconButton(onClick = onRequestScanQr) {
                            Icon(
                                painter = painterResource(id = AndroidR.drawable.ic_menu_camera),
                                contentDescription = "Escanear QR"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRequestUnpairRouter,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Unpair")
                }
                Button(onClick = onRequestPairRouter, modifier = Modifier.weight(1f)) {
                    Text("Pair")
                }
            }
        }
    }
}

@Composable
fun RouterNetworkSignaturePanel(
    hideIp: Boolean,
    bssid: String,
    band: String,
    channel: String,
    width: String,
    widthIsInferred: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardHeaderWithInfo(
                title = "Network signature",
                infoText = "Readable summary of the network signature detected by the daemon."
            )
            Text(
                "ID (BSSID): ${if (hideIp) "Hidden" else bssid.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Band: ${band.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
            Text("Channel: ${channel.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Width: ${width.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                if (widthIsInferred) {
                    Icon(
                        painter = painterResource(id = AndroidR.drawable.ic_menu_view),
                        contentDescription = "Inferred width",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("inferred", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RouterChannelAnalysisPanel(
    paired: Boolean,
    onRequestChannelScan: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardHeaderWithInfo(
                title = "Channel analysis",
                infoText = "Request recommendation of the best available channel from the router."
            )
            Button(
                onClick = onRequestChannelScan,
                modifier = Modifier.fillMaxWidth(),
                enabled = paired
            ) {
                Icon(Icons.Outlined.Wifi, contentDescription = null)
                Text("  Analyze Channels")
            }
            if (!paired) {
                Text(
                    "Pair the router to enable channel analysis",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
