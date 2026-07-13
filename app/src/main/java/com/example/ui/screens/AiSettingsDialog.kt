package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.DownloadStatus
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val downloadStatus by viewModel.modelManager.downloadStatus.collectAsState()
    var gemmaExists by remember { mutableStateOf(viewModel.modelManager.checkModelExists()) }

    // File picker launcher for importing Gemma task files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                viewModel.modelManager.importModel(uri)
                gemmaExists = viewModel.modelManager.checkModelExists()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Control Center",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Provider selection dropdown / cards
                Column {
                    Text(
                        text = "Preferred AI Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The app will attempt to use this engine first, and fall back to others if needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                viewModel.aiRegistry.providers.forEach { provider ->
                    val isSelected = viewModel.activeAiProviderId == provider.id
                    val isAvailable = remember(provider.id, gemmaExists) {
                        if (provider.id == "mediapipe_local") gemmaExists else true
                    }

                    Card(
                        onClick = { viewModel.updateActiveAiProvider(provider.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_provider_card_${provider.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                        ),
                        border = if (isSelected) {
                            CardDefaults.outlinedCardBorder().copy(
                                width = 2.dp,
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateActiveAiProvider(provider.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isProviderAvailable = run {
                                        if (provider.id == "gemini_cloud") {
                                            val key = viewModel.userGeminiApiKey.ifBlank { com.example.BuildConfig.GEMINI_API_KEY }
                                            key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
                                        } else if (provider.id == "mediapipe_local") {
                                            gemmaExists
                                        } else {
                                            true
                                        }
                                    }

                                    Icon(
                                        imageVector = if (isProviderAvailable) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = "Status",
                                        tint = if (isProviderAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (provider.id) {
                                            "gemini_cloud" -> {
                                                if (isProviderAvailable) "Key Configured & Active" 
                                                else "Key Missing (Enter below or set in Secrets)"
                                            }
                                            "mediapipe_local" -> {
                                                if (gemmaExists) "Gemma Model Loaded" 
                                                else "Missing model (gemma.task)"
                                            }
                                            else -> "Ready (Gated by device)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isProviderAvailable) MaterialTheme.colorScheme.onSurfaceVariant 
                                                else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // Gemini API Key Input Field
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Cloud Gemini API Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Enter your custom GEMINI_API_KEY. If left blank, the app will fall back to the API key configured in the system environment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    var apiKeyText by remember { mutableStateOf(viewModel.userGeminiApiKey) }
                    
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            viewModel.updateGeminiApiKey(it)
                        },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input"),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            if (apiKeyText.isNotEmpty()) {
                                IconButton(onClick = {
                                    apiKeyText = ""
                                    viewModel.updateGeminiApiKey("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Key")
                                }
                            }
                        }
                    )
                }

                // Local Gemma Model Management (MediaPipe)
                Divider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Local Gemma Model (MediaPipe)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "To run LLM inference 100% offline without sending text to the cloud, download the optimized CPU/GPU task model or import your own .task file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (gemmaExists) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "gemma.task is loaded (Active)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.modelManager.deleteModel()
                                        gemmaExists = viewModel.modelManager.checkModelExists()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    } else {
                        when (val status = downloadStatus) {
                            is DownloadStatus.Idle -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                viewModel.modelManager.downloadModel()
                                                gemmaExists = viewModel.modelManager.checkModelExists()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Download")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download Model")
                                    }
                                    OutlinedButton(
                                        onClick = { filePickerLauncher.launch("*/*") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = "Import")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Import .task")
                                    }
                                }
                            }
                            is DownloadStatus.Progress -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Importing/Downloading: ${status.percentage}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = status.percentage / 100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            is DownloadStatus.Success -> {
                                Text(
                                    text = "Model loaded successfully!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.modelManager.deleteModel()
                                            gemmaExists = viewModel.modelManager.checkModelExists()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Reset")
                                }
                            }
                            is DownloadStatus.Error -> {
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.modelManager.deleteModel()
                                            gemmaExists = viewModel.modelManager.checkModelExists()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                // Premium Offline Voice (Piper TTS)
                Divider()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Premium Offline Voice (Piper TTS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Highly realistic, deeply natural offline narration is fully supported! You can select, download, and manage multiple Piper voices (including Ryan, Amy, Alan, and LibriTTS) directly from the central Audio Player settings on the book reader screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
