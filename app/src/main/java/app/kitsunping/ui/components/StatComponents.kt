package app.kitsunping.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class StatItem(val label: String, val value: String, val valueColor: Color? = null)

@Composable
fun CardHeaderWithInfo(title: String, infoText: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(imageVector = Icons.Outlined.Info, contentDescription = "Info")
            }
        }
        if (expanded) {
            Text(text = infoText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatCard(
    title: String,
    items: List<StatItem>,
    infoText: String,
    enabled: Boolean = true,
    highlight: Boolean = false
) {
    val cardModifier = if (enabled) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().alpha(0.45f)
    val baseColor = MaterialTheme.colorScheme.surface
    val pulseColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
    val animatedColor by animateColorAsState(
        targetValue = if (highlight) pulseColor else baseColor,
        label = "cardPulse"
    )
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = animatedColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardHeaderWithInfo(title = title, infoText = infoText)
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        StatTile(item, Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColor = MaterialTheme.colorScheme.surface
    val pulseColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
    val animatedColor by animateColorAsState(
        targetValue = if (highlight) pulseColor else baseColor,
        label = "highlightCard"
    )
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = animatedColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        content()
    }
}

@Composable
fun StatTile(item: StatItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = item.label, style = MaterialTheme.typography.labelSmall)
            val valueColor = item.valueColor ?: MaterialTheme.colorScheme.onSurface
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor
            )
        }
    }
}

@Composable
fun InactiveCard(label: String) {
    Card(
        modifier = Modifier.fillMaxWidth().alpha(0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CardHeaderWithInfo(
                title = label,
                infoText = "The interface is not active right now. Verify the connection or module status."
            )
            Text(text = "Interfaz inactiva", style = MaterialTheme.typography.bodySmall)
        }
    }
}
