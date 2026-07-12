package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.VoiceOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsPlayerBar(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val playingBook = viewModel.ttsPlayingBook
    val playingChapter = viewModel.ttsPlayingChapter

    if (playingBook == null || playingChapter == null) return

    var showSettingsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("tts_player_bar"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elegant Book icon / visualizer badge with custom primary gradient
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Playing Audio",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Book & Chapter info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = playingBook.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playingChapter.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Voice / Settings panel trigger
                IconButton(onClick = {
                    viewModel.initTts()
                    showSettingsDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Voice Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Play / Pause Toggle
                IconButton(
                    onClick = {
                        if (viewModel.ttsIsPlaying) {
                            viewModel.pauseTts()
                        } else {
                            viewModel.resumeTts()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (viewModel.ttsIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (viewModel.ttsIsPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Stop / Close Button
                IconButton(onClick = { viewModel.stopTts() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // --- Voice Customization Dialog ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Voice Reader Options", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 0. Sleep Timer Option
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Sleep Timer: " + if (viewModel.sleepTimerMinutes == 0) "Never" else {
                                val mins = (viewModel.sleepTimerRemainingSeconds ?: 0) / 60
                                val secs = (viewModel.sleepTimerRemainingSeconds ?: 0) % 60
                                "${viewModel.sleepTimerMinutes}m (${String.format("%02d:%02d", mins, secs)} remaining)"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val timerOptions = listOf(0 to "Never", 10 to "10m", 30 to "30m", 60 to "1h", 120 to "2h")
                            timerOptions.forEach { (mins, label) ->
                                val isSelected = viewModel.sleepTimerMinutes == mins
                                OutlinedButton(
                                    onClick = { viewModel.startSleepTimer(mins) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 1. Speech Speed Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Reading Speed: ${String.format("%.1f", viewModel.ttsSpeed)}x",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = viewModel.ttsSpeed,
                            onValueChange = { speed ->
                                viewModel.updateTtsSettings(viewModel.ttsPitch, speed)
                            },
                            valueRange = 0.5f..2.5f,
                            steps = 19
                        )
                    }

                    // 2. Pitch Control
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Pitch / Tone: ${String.format("%.1f", viewModel.ttsPitch)}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = viewModel.ttsPitch,
                            onValueChange = { pitch ->
                                viewModel.updateTtsSettings(pitch, viewModel.ttsSpeed)
                            },
                            valueRange = 0.5f..1.5f,
                            steps = 9
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 3. Voice choices
                    Text(
                        text = "Voice Choice (${viewModel.ttsVoices.size} options)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (viewModel.ttsVoices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Initializing voices list...", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(6.dp)
                        ) {
                            items(viewModel.ttsVoices) { voice ->
                                val isSelected = voice.id == viewModel.selectedVoiceId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.setTtsVoice(voice)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = voice.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
