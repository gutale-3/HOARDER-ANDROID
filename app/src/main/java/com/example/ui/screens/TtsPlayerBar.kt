package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.VoiceOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsPlayerBar(
    viewModel: MainViewModel,
    onNavigateToReader: (String, String, Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val playingBook = viewModel.ttsPlayingBook
    val playingChapter = viewModel.ttsPlayingChapter

    if (playingBook == null || playingChapter == null) {
        if (viewModel.hasResumableSession) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("tts_resume_bar"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.resumeLastSession() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Continue listening",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "${viewModel.resumeBookName} • ${viewModel.resumeChapterTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearTtsProgress(); viewModel.loadResumableTtsSession() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        return
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    if (viewModel.isTtsPlayerBarMinimized) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable {
                    onNavigateToReader(playingBook.id, playingChapter.id, viewModel.ttsActiveParagraphIndex ?: 0)
                }
                .testTag("tts_player_bar_minimized"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            ),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.isTtsPlayerBarMinimized = false },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Expand Player",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "${playingBook.title} - ${playingChapter.title}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (viewModel.ttsIsPlaying) {
                            viewModel.pauseTts()
                        } else {
                            viewModel.resumeTts()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (viewModel.ttsIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.stopTts() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        return
    }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First Row: Book/Chapter Info, Settings, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Book icon / visualizer badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Info text
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onNavigateToReader(playingBook.id, playingChapter.id, viewModel.ttsActiveParagraphIndex ?: 0)
                        },
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

                // Minimize and stop buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = { viewModel.isTtsPlayerBarMinimized = true }) {
                        Icon(
                            imageVector = Icons.Default.UnfoldLess,
                            contentDescription = "Minimize Player",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.stopTts() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Second Row: Progress Slider
            val activePara = viewModel.ttsActiveParagraphIndex ?: -1
            val totalParas = viewModel.ttsTotalParagraphs
            val sliderValue = if (activePara < 0) 0f else activePara.toFloat()
            val maxSliderValue = if (totalParas <= 1) 1f else (totalParas - 1).toFloat()

            var draggingValue by remember { mutableStateOf<Float?>(null) }
            val currentSliderValue = draggingValue ?: sliderValue

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activePara < 0) "Reading Chapter Title" else "Paragraph ${currentSliderValue.toInt() + 1} of $totalParas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${((currentSliderValue / maxSliderValue) * 100).toInt().coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = currentSliderValue.coerceIn(0f, maxSliderValue),
                    onValueChange = { draggingValue = it },
                    onValueChangeFinished = {
                        draggingValue?.let {
                            viewModel.seekToParagraph(it.toInt())
                        }
                        draggingValue = null
                    },
                    valueRange = 0f..maxSliderValue,
                    modifier = Modifier.fillMaxWidth().height(24.dp).testTag("tts_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
            }

            // Third Row: Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-Scroll Toggle Button
                IconButton(
                    onClick = { viewModel.toggleTtsAutoScroll() },
                    modifier = Modifier.size(40.dp).testTag("tts_quick_autoscroll_btn")
                ) {
                    Icon(
                        imageVector = if (viewModel.ttsAutoScrollEnabled) Icons.Default.MenuBook else Icons.Default.Book,
                        contentDescription = "Toggle Auto-Scroll",
                        tint = if (viewModel.ttsAutoScrollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Prev Chapter
                IconButton(
                    onClick = { viewModel.playPreviousChapterTts() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Chapter",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Skip back 1 paragraph
                IconButton(
                    onClick = { viewModel.skipParagraph(-1) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateBefore,
                        contentDescription = "Previous Paragraph",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Play / Pause Toggle Button
                FilledIconButton(
                    onClick = {
                        if (viewModel.ttsIsPlaying) {
                            viewModel.pauseTts()
                        } else {
                            viewModel.resumeTts()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (viewModel.ttsIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (viewModel.ttsIsPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Skip forward 1 paragraph
                IconButton(
                    onClick = { viewModel.skipParagraph(1) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateNext,
                        contentDescription = "Next Paragraph",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Next Chapter
                IconButton(
                    onClick = { viewModel.playNextChapterTts() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Chapter",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Voice Settings Button
                IconButton(
                    onClick = {
                        viewModel.initTts()
                        showSettingsDialog = true
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Voice Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }    // --- Voice Customization Dialog ---
    if (showSettingsDialog) {
        Dialog(
            onDismissRequest = { showSettingsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "Voice Reader Options",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Selector
                    var selectedTab by remember { mutableStateOf(0) } // 0 = Tuning / Controls, 1 = Voices
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = { Divider(color = MaterialTheme.colorScheme.surfaceVariant) }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Voice Tuning", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                            icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Select Voice", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                            icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedTab == 0) {
                        // --- TUNING TAB ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // 1. Sleep Timer
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Sleep Timer",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (viewModel.sleepTimerMinutes == 0) "Inactive" else {
                                        val mins = (viewModel.sleepTimerRemainingSeconds ?: 0) / 60
                                        val secs = (viewModel.sleepTimerRemainingSeconds ?: 0) % 60
                                        "Active: ${viewModel.sleepTimerMinutes}m (${String.format("%02d:%02d", mins, secs)} remaining)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val timerOptions = listOf(0 to "Never", 10 to "10m", 30 to "30m", 60 to "1h", 120 to "2h")
                                    timerOptions.forEach { (mins, label) ->
                                        val isSelected = viewModel.sleepTimerMinutes == mins
                                        OutlinedButton(
                                            onClick = { viewModel.startSleepTimer(mins) },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            border = BorderStroke(
                                                width = 1.5.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 2. Reading Speed
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Reading Speed",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${String.format("%.1f", viewModel.ttsSpeed)}x",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                Slider(
                                    value = viewModel.ttsSpeed,
                                    onValueChange = { speed ->
                                        viewModel.updateTtsSettings(viewModel.ttsPitch, speed)
                                    },
                                    valueRange = 0.5f..2.5f,
                                    steps = 19
                                )
                                // Quick presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f).forEach { speedVal ->
                                        val isSelected = Math.abs(viewModel.ttsSpeed - speedVal) < 0.05f
                                        val label = if (speedVal == 1.0f) "Normal (1.0x)" else "${speedVal}x"
                                        SuggestionChip(
                                            onClick = { viewModel.updateTtsSettings(viewModel.ttsPitch, speedVal) },
                                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 3. Pitch Controls
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Pitch / Tone",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${String.format("%.1f", viewModel.ttsPitch)}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                Slider(
                                    value = viewModel.ttsPitch,
                                    onValueChange = { pitch ->
                                        viewModel.updateTtsSettings(pitch, viewModel.ttsSpeed)
                                    },
                                    valueRange = 0.5f..1.5f,
                                    steps = 9
                                )
                                // Quick presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple(0.7f, "Deep", "Deep Tone"),
                                        Triple(1.0f, "Normal", "Normal Pitch"),
                                        Triple(1.3f, "High", "High Pitch")
                                    ).forEach { (pitchVal, label, desc) ->
                                        val isSelected = Math.abs(viewModel.ttsPitch - pitchVal) < 0.05f
                                        SuggestionChip(
                                            onClick = { viewModel.updateTtsSettings(pitchVal, viewModel.ttsSpeed) },
                                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 4. Auto-Scroll Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Follow Spoken Text",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Keep reader in sync automatically",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = viewModel.ttsAutoScrollEnabled,
                                    onCheckedChange = { viewModel.toggleTtsAutoScroll() }
                                )
                            }
                        }
                    } else {
                        // --- VOICES TAB ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Select Reader Voice",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            var voiceFilter by remember { mutableStateOf("All") }

                            // Filter Chips Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("All", "Kokoro", "Piper", "System").forEach { filter ->
                                    val count = when (filter) {
                                        "All" -> viewModel.ttsVoices.size
                                        "Kokoro" -> viewModel.ttsVoices.count { it.id.startsWith("kokoro-") }
                                        "Piper" -> viewModel.ttsVoices.count { it.id.startsWith("vits-piper-") }
                                        "System" -> viewModel.ttsVoices.count { !it.id.startsWith("vits-piper-") && !it.id.startsWith("kokoro-") }
                                        else -> 0
                                    }
                                    
                                    if (count > 0) {
                                        val isSelected = voiceFilter == filter
                                        Surface(
                                            onClick = { voiceFilter = filter },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            )
                                        ) {
                                            Text(
                                                text = "$filter ($count)",
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            val filteredVoices = viewModel.ttsVoices.filter { voice ->
                                when (voiceFilter) {
                                    "All" -> true
                                    "Kokoro" -> voice.id.startsWith("kokoro-")
                                    "Piper" -> voice.id.startsWith("vits-piper-")
                                    "System" -> !voice.id.startsWith("vits-piper-") && !voice.id.startsWith("kokoro-")
                                    else -> true
                                }
                            }

                            if (filteredVoices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Initializing voices list...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    items(filteredVoices) { voice ->
                                        val isSelected = voice.id == viewModel.selectedVoiceId
                                        if (voice.id.startsWith("vits-piper-") || voice.id.startsWith("kokoro-")) {
                                            val piperVoice = com.example.data.ai.PiperVoiceCatalog.getVoiceById(voice.id)
                                            val isDownloaded = viewModel.isVoiceDownloaded(piperVoice)
                                            val isThisVoiceDownloading = viewModel.premiumVoiceDownloading && viewModel.sherpaOnnxTtsEngine.selectedVoiceId == voice.id
                                            
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        if (isDownloaded) {
                                                            viewModel.setTtsVoice(voice)
                                                        } else if (!isThisVoiceDownloading) {
                                                            viewModel.downloadPremiumVoice(piperVoice)
                                                        }
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surface
                                                    }
                                                ),
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.Top,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        // Avatar Badge
                                                        Box(
                                                            modifier = Modifier
                                                                .size(44.dp)
                                                                .background(
                                                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isSelected) Icons.Default.VolumeUp else Icons.Default.RecordVoiceOver,
                                                                contentDescription = null,
                                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }

                                                        // Voice Title Info
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.wrapContentWidth()
                                                            ) {
                                                                Text(
                                                                    text = piperVoice.name,
                                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                                        else MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                )
                                                                
                                                                val engineBadgeBg = if (piperVoice.isKokoro) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                                                val engineBadgeColor = if (piperVoice.isKokoro) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                                val engineBadgeText = if (piperVoice.isKokoro) "KOKORO" else "PIPER"

                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(engineBadgeBg, RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = engineBadgeText,
                                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                                            fontSize = 8.sp,
                                                                            fontWeight = FontWeight.Black,
                                                                            color = engineBadgeColor
                                                                        )
                                                                    )
                                                                }

                                                                if (isDownloaded) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "Offline",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.primary,
                                                                            fontSize = 8.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                } else {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "${piperVoice.sizeMb}MB",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            fontSize = 8.sp
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "${piperVoice.accent} • ${piperVoice.gender}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )

                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = piperVoice.description,
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    lineHeight = 15.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                                ),
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        // Play Sample & Delete Controls
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            val isPlayingPreview = viewModel.previewingVoiceId == voice.id
                                                            IconButton(
                                                                onClick = {
                                                                    if (isPlayingPreview) {
                                                                        viewModel.stopVoicePreview()
                                                                    } else {
                                                                        viewModel.playVoicePreview(voice)
                                                                    }
                                                                },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayCircle,
                                                                    contentDescription = if (isPlayingPreview) "Stop Preview" else "Preview Sample",
                                                                    tint = if (isPlayingPreview) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }

                                                            if (isDownloaded) {
                                                                IconButton(
                                                                    onClick = { viewModel.deletePremiumVoice(piperVoice) },
                                                                    modifier = Modifier.size(36.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Delete,
                                                                        contentDescription = "Delete",
                                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Speaker ID input if multi-speaker
                                                    if (isSelected && voice.id == "vits-piper-en_US-libritts_r-medium") {
                                                        var speakerIdInput by remember { mutableStateOf(viewModel.getSpeakerId(voice.id).toString()) }
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "Speaker ID (0-900+):",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            TextField(
                                                                value = speakerIdInput,
                                                                onValueChange = { newVal ->
                                                                    if (newVal.all { it.isDigit() }) {
                                                                        speakerIdInput = newVal
                                                                        val sId = newVal.toIntOrNull() ?: 0
                                                                        viewModel.saveSpeakerId(voice.id, sId)
                                                                    }
                                                                },
                                                                modifier = Modifier.width(70.dp).height(32.dp),
                                                                textStyle = MaterialTheme.typography.labelSmall,
                                                                singleLine = true,
                                                                colors = TextFieldDefaults.colors(
                                                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                                    focusedIndicatorColor = Color.Transparent,
                                                                    unfocusedIndicatorColor = Color.Transparent
                                                                )
                                                            )
                                                        }
                                                    }

                                                    // Download progress bar
                                                    if (isThisVoiceDownloading) {
                                                        Column(
                                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = "Downloading voice... ${viewModel.premiumVoiceDownloadProgress}%",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(10.dp),
                                                                    strokeWidth = 1.5.dp,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            LinearProgressIndicator(
                                                                progress = viewModel.premiumVoiceDownloadProgress / 100f,
                                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                                color = MaterialTheme.colorScheme.primary,
                                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        viewModel.setTtsVoice(voice)
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surface
                                                    }
                                                ),
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    // Avatar Badge
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .background(
                                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                                shape = RoundedCornerShape(12.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isSelected) Icons.Default.VolumeUp else Icons.Default.Hearing,
                                                            contentDescription = null,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }

                                                    // Voice Details
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = voice.name.replace("System: ", "").substringAfterLast(" - ").uppercase(),
                                                                style = MaterialTheme.typography.titleMedium.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                                ),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "SYSTEM",
                                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                                        fontSize = 8.sp,
                                                                        fontWeight = FontWeight.Black,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "System Default Text-to-Speech Voice",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    // Play Sample button
                                                    val isPlayingPreview = viewModel.previewingVoiceId == voice.id
                                                    IconButton(
                                                        onClick = {
                                                            if (isPlayingPreview) {
                                                                viewModel.stopVoicePreview()
                                                            } else {
                                                                viewModel.playVoicePreview(voice)
                                                            }
                                                        },
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayCircle,
                                                            contentDescription = if (isPlayingPreview) "Stop Preview" else "Preview Sample",
                                                            tint = if (isPlayingPreview) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Dismiss Button
                    Button(
                        onClick = { showSettingsDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
