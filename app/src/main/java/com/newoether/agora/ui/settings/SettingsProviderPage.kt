package com.newoether.agora.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.CustomProviderConfig
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsProviderPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val apiKeys by viewModel.apiKeys.collectAsState()
    val activeApiKeyIds by viewModel.activeApiKeyIds.collectAsState()
    val providerBaseUrls by viewModel.providerBaseUrls.collectAsState()
    val customProviders by viewModel.customProviders.collectAsState()
    val localChatModels by viewModel.localChatModels.collectAsState()
    val activeLocalId by viewModel.activeLocalChatModelId.collectAsState()

    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    val showDocFab by viewModel.showDocumentationFab.collectAsState()

    if (selectedProvider != null) {
        SettingsProviderDetailPage(
            providerName = selectedProvider!!,
            viewModel = viewModel,
            onBack = { selectedProvider = null }
        )
        return
    }

    val builtInNames = listOf("Google", "OpenAI", "Anthropic", "DeepSeek", "Qwen", "Ollama", "Open Router")

    @Composable
    fun isConfigured(name: String): Boolean = when (name) {
        "Local" -> localChatModels.isNotEmpty()
        else -> {
            val isCustom = customProviders.any { it.name == name }
            if (isCustom || name == "Ollama") !providerBaseUrls[name].isNullOrBlank()
            else apiKeys.any { it.provider == name }
        }
    }

    @Composable
    fun summary(name: String): String = when (name) {
        "Local" -> if (localChatModels.isEmpty()) stringResource(R.string.not_configured)
        else stringResource(R.string.provider_local_models_summary, localChatModels.size)
        else -> {
            val keyCount = apiKeys.count { it.provider == name }
            when {
                name == "Ollama" -> providerBaseUrls[name]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_configured)
                customProviders.any { it.name == name } -> providerBaseUrls[name]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_configured)
                keyCount > 0 -> stringResource(R.string.provider_keys_summary, keyCount)
                else -> stringResource(R.string.not_configured)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_provider), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddCustomDialog = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.custom_provider_add))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        },
        floatingActionButton = { if (showDocFab) DocumentationFab("provider.md") },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        val fm = LocalFocusManager.current
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { fm.clearFocus() }
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                stringResource(R.string.provider_built_in),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            builtInNames.forEach { name ->
                ProviderRow(
                    name = name,
                    configured = isConfigured(name),
                    summary = summary(name),
                    onClick = { selectedProvider = name }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (customProviders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.custom_provider_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                customProviders.forEach { config ->
                    ProviderRow(
                        name = config.name,
                        configured = isConfigured(config.name),
                        summary = summary(config.name),
                        isCustom = true,
                        onClick = { selectedProvider = config.name }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            ProviderRow(
                name = stringResource(R.string.local_title),
                configured = isConfigured("Local"),
                summary = summary("Local"),
                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                onClick = { selectedProvider = "Local" }
            )

            if (showDocFab) Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Add Custom Provider Dialog
    if (showAddCustomDialog) {
        var customName by remember { mutableStateOf("") }
        var customBaseUrl by remember { mutableStateOf("") }
        var nameError by remember { mutableStateOf(false) }
        var urlError by remember { mutableStateOf(false) }
        val allNames = builtInNames + customProviders.map { it.name }

        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text(stringResource(R.string.custom_provider_add_title), fontWeight = FontWeight.Bold) },
            text = {
                val fm = LocalFocusManager.current
                Column(Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { fm.clearFocus() }) {
                    OutlinedTextField(
                        value = customName, onValueChange = { customName = it; nameError = false },
                        label = { Text(stringResource(R.string.custom_provider_name_label)) },
                        isError = nameError,
                        supportingText = if (nameError) {{ Text(stringResource(R.string.custom_provider_name_error)) }} else null,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customBaseUrl, onValueChange = { customBaseUrl = it; urlError = false },
                        label = { Text(stringResource(R.string.provider_base_url)) },
                        isError = urlError,
                        supportingText = if (urlError) {{ Text(stringResource(R.string.custom_provider_url_error)) }} else null,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmedName = customName.trim()
                    val trimmedUrl = customBaseUrl.trim()
                    nameError = trimmedName.isBlank() || trimmedName in allNames
                    urlError = trimmedUrl.isBlank()
                    if (!nameError && !urlError) {
                        viewModel.addCustomProvider(trimmedName, trimmedUrl)
                        showAddCustomDialog = false
                    }
                }) { Text(stringResource(R.string.custom_provider_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ProviderRow(
    name: String,
    configured: Boolean,
    summary: String,
    isCustom: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = if (configured) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (configured) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                stringResource(R.string.custom_provider_badge),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Configured indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .padding(start = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (configured) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                } else {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ) {}
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
