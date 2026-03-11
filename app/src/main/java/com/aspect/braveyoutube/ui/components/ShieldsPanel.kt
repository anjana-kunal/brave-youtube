package com.aspect.braveyoutube.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aspect.braveyoutube.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldsPanel(
    shieldsEnabled: Boolean,
    blockAds: Boolean,
    blockShorts: Boolean,
    backgroundPlayback: Boolean,
    adsBlocked: Int,
    onToggleShields: (Boolean) -> Unit,
    onToggleBlockAds: (Boolean) -> Unit,
    onToggleBlockShorts: (Boolean) -> Unit,
    onToggleBgPlayback: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (shieldsEnabled) R.drawable.ic_shield_check
                            else R.drawable.ic_shield
                        ),
                        contentDescription = "Shields",
                        tint = if (shieldsEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "Brave Shields",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = shieldsEnabled,
                    onCheckedChange = onToggleShields,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Ads\nBlocked", adsBlocked.toString())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Toggle items
            ShieldToggle(
                title = "Block Ads & Promoted Content",
                subtitle = "Filters promoted videos and ad content from results",
                checked = blockAds && shieldsEnabled,
                enabled = shieldsEnabled,
                onCheckedChange = onToggleBlockAds
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            ShieldToggle(
                title = "Block YouTube Shorts",
                subtitle = "Hide Shorts from the feed",
                checked = blockShorts && shieldsEnabled,
                enabled = shieldsEnabled,
                onCheckedChange = onToggleBlockShorts
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            ShieldToggle(
                title = "Background Playback",
                subtitle = "Continue playing audio when app is in background",
                checked = backgroundPlayback,
                enabled = true,
                onCheckedChange = onToggleBgPlayback
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ShieldToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
