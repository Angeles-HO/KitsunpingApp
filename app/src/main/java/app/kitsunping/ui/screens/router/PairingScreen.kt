package app.kitsunping.ui.screens.router

import android.R as AndroidR
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kitsunping.ui.components.CardHeaderWithInfo

@Composable
fun PairingScreen(
    contentPadding: PaddingValues,
    paired: Boolean,
    policyEventName: String,
    suggestedRouterIp: String,
    routerIp: String,
    routerId: String,
    hasToken: Boolean,
    scannedPairPayload: String,
    hideIp: Boolean,
    onToggleHideIp: () -> Unit,
    onRequestPairRouter: (String, String) -> Unit,
    onRequestUnpairRouter: () -> Unit,
    onRequestScanQr: () -> Unit
) {
    var ipInput by rememberSaveable { mutableStateOf("") }
    var codeInput by rememberSaveable(scannedPairPayload) { mutableStateOf(scannedPairPayload) }
    var showIpInput by rememberSaveable { mutableStateOf(false) }
    var showCodeInput by rememberSaveable { mutableStateOf(false) }
    val daemonPairConfirmed = policyEventName == "ROUTER_PAIRED"
    val pulse = rememberInfiniteTransition(label = "pairPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pairScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardHeaderWithInfo(
                    title = "Current status",
                    infoText = "Dedicated pairing flow via IP + QR URI/pair_code."
                )
                if (daemonPairConfirmed && paired) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Pairing confirmed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Pairing confirmed by daemon",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(text = if (paired) "Paired" else "Unpaired", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hideIp) "Saved IP: Hidden" else "IP guardada: ${routerIp.ifBlank { "-" }}",
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeaderWithInfo(
                    title = "Pairing",
                    infoText = "Enter router IP and QR URI or pair_code to complete pairing."
                )
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Router IP") },
                    visualTransformation = if (showIpInput) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showIpInput = !showIpInput }) {
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
                    Button(
                        onClick = {
                            if (suggestedRouterIp.isNotBlank()) {
                                ipInput = suggestedRouterIp
                                showIpInput = false
                            }
                        }
                    ) {
                        Text("Auto-detect")
                    }
                }
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("QR URI o pair_code") },
                    visualTransformation = if (showCodeInput) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showCodeInput = !showCodeInput }) {
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
                    Button(onClick = { onRequestPairRouter(ipInput.ifBlank { routerIp }, codeInput) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Text(" Pair")
                    }
                }
            }
        }
    }
}
