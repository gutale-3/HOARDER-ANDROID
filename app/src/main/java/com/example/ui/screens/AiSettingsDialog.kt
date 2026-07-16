package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Dialog(
        onDismissRequest = onDismiss,
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "AI Control Center",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Engine Preference
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Preferred AI Engine",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "The app will attempt to use this engine first, and fall back to others if needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        viewModel.aiRegistry.providers.forEach { provider ->
                            val isSelected = viewModel.activeAiProviderId == provider.id
                            val isAvailable = remember(provider.id, gemmaExists) {
                                if (provider.id == "mediapipe_local") gemmaExists else true
                            }

                            Card(
                                onClick = { viewModel.settings.updateActiveAiProvider(provider.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_provider_card_${provider.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.settings.updateActiveAiProvider(provider.id) }
                                    )
                                    Column(modifier = Modifier.weight(1.0f)) {
                                        Text(
                                            text = provider.displayName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
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
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = when (provider.id) {
                                                    "gemini_cloud" -> {
                                                        if (isProviderAvailable) "Key Configured & Active" 
                                                        else "Key Missing (Enter below)"
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
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Gemini API Key Input Field with Validation
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Cloud Gemini API Key",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Enter your custom GEMINI_API_KEY. If left blank, the app will fall back to the API key configured in the system environment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        var apiKeyText by remember { mutableStateOf(viewModel.userGeminiApiKey) }
                        var validationResult by remember { mutableStateOf<String?>(null) }
                        var isCurrentlyValidating by remember { mutableStateOf(false) }
                        var wasValidationSuccessful by remember { mutableStateOf<Boolean?>(null) }
                        var keyVisible by remember { mutableStateOf(false) }
                        
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = {
                                apiKeyText = it
                                viewModel.settings.updateGeminiApiKey(it)
                                validationResult = null
                                wasValidationSuccessful = null
                            },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input"),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (apiKeyText.isNotEmpty()) {
                                        IconButton(onClick = { keyVisible = !keyVisible }) {
                                            Icon(
                                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (keyVisible) "Hide Key" else "Show Key"
                                            )
                                        }
                                        IconButton(onClick = {
                                            apiKeyText = ""
                                            viewModel.settings.updateGeminiApiKey("")
                                            validationResult = null
                                            wasValidationSuccessful = null
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear Key")
                                        }
                                    }
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    isCurrentlyValidating = true
                                    viewModel.aiFeatures.validateApiKey(apiKeyText) { success, msg ->
                                        isCurrentlyValidating = false
                                        wasValidationSuccessful = success
                                        validationResult = msg
                                    }
                                },
                                enabled = apiKeyText.isNotEmpty() && !isCurrentlyValidating,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isCurrentlyValidating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Validating...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.OfflineBolt, contentDescription = "Validate", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test Connection", fontSize = 12.sp)
                                }
                            }
                            
                            if (validationResult != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (wasValidationSuccessful == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = "Status",
                                        tint = if (wasValidationSuccessful == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = validationResult!!,
                                        color = if (wasValidationSuccessful == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Customizable AI Prompts Section
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Customizable AI Prompt Guides",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Fine-tune the custom system instructions that guide the Gemini engine for advanced text parsing, summary building, and names translation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        var showPromptSettings by remember { mutableStateOf(false) }
                        
                        OutlinedButton(
                            onClick = { showPromptSettings = !showPromptSettings },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (showPromptSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Prompts"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showPromptSettings) "Hide Custom Prompts" else "Show & Edit Custom Prompts")
                        }
                        
                        if (showPromptSettings) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Glossary Prompt
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Glossary Generator Prompt", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    OutlinedTextField(
                                        value = viewModel.glossaryPrompt,
                                        onValueChange = { viewModel.settings.updateGlossaryPrompt(it) },
                                        minLines = 3,
                                        maxLines = 5,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                                
                                // Polish Prompt
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Prose Polish Prompt", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    OutlinedTextField(
                                        value = viewModel.polishPrompt,
                                        onValueChange = { viewModel.settings.updatePolishPrompt(it) },
                                        minLines = 3,
                                        maxLines = 5,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                                
                                // Recap Prompt
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Chapter Summary Prompt", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    OutlinedTextField(
                                        value = viewModel.recapPrompt,
                                        onValueChange = { viewModel.settings.updateRecapPrompt(it) },
                                        minLines = 3,
                                        maxLines = 5,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Local Gemma Model Management (MediaPipe)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Local Gemma Model (MediaPipe)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "gemma.task is loaded (Active)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Using local CPU/GPU acceleration",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
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
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 12.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download Model", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { filePickerLauncher.launch("*/*") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.UploadFile, contentDescription = "Import", modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Import .task", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                                is DownloadStatus.Progress -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Importing/Downloading: ${status.percentage}%",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        }
                                        LinearProgressIndicator(
                                            progress = status.percentage / 100f,
                                            modifier = Modifier.fillMaxWidth(),
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                is DownloadStatus.Success -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Model loaded successfully!",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.modelManager.deleteModel()
                                                    gemmaExists = viewModel.modelManager.checkModelExists()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Reset")
                                        }
                                    }
                                }
                                is DownloadStatus.Error -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = status.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.modelManager.deleteModel()
                                                    gemmaExists = viewModel.modelManager.checkModelExists()
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Premium Offline Voice Info
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Premium Offline Voice (Piper TTS)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Highly realistic, deeply natural offline narration is fully supported! You can select, download, and manage multiple Piper voices (including Ryan, Amy, Alan, and LibriTTS) directly from the central Audio Player settings on the book reader screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Done Button
                Button(
                    onClick = onDismiss,
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
