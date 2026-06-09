package com.newoether.agora.ui.settings

import com.newoether.agora.util.DebugLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.ApiKeyEntry
import com.newoether.agora.data.LocalChatModelConfig
import com.newoether.agora.util.noOpBringIntoView
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsProviderDetailPage(
    providerName: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val apiKeys by viewModel.apiKeys.collectAsState()
    val activeApiKeyIds by viewModel.activeApiKeyIds.collectAsState()
    val providerBaseUrls by viewModel.providerBaseUrls.collectAsState()
    val localChatModels by viewModel.localChatModels.collectAsState()
    val activeLocalId by viewModel.activeLocalChatModelId.collectAsState()
    val customProviders by viewModel.customProviders.collectAsState()

    val isCustom = customProviders.any { it.name == providerName }
    val isLocal = providerName == "Local"
    val isOllama = providerName == "Ollama"

    var showKeyDialog by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var showDeleteKeyConfirm by remember { mutableStateOf<ApiKeyEntry?>(null) }

    // Local model management state
    var importingModel by remember { mutableStateOf(false) }
    var copiedFilePath by remember { mutableStateOf<String?>(null) }
    var showAddModelDialog by remember { mutableStateOf(false) }
    var showEditModelDialog by remember { mutableStateOf<LocalChatModelConfig?>(null) }
    var showDeleteModelConfirm by remember { mutableStateOf<LocalChatModelConfig?>(null) }
    var showGgufError by remember { mutableStateOf(false) }
    // mmproj picker
    var mmprojPickedUri by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importingModel = true
            scope.launch(Dispatchers.IO) {
                try {
                    val destFile = java.io.File(context.filesDir, "chat_model_${java.util.UUID.randomUUID()}.gguf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val magic = ByteArray(4)
                    destFile.inputStream().use { it.read(magic) }
                    if (magic[0] != 'G'.code.toByte() || magic[1] != 'G'.code.toByte()
                        || magic[2] != 'U'.code.toByte() || magic[3] != 'F'.code.toByte()) {
                        destFile.delete()
                        showGgufError = true
                    } else {
                        copiedFilePath = destFile.absolutePath
                        showAddModelDialog = true
                    }
                } catch (e: Exception) {
                    DebugLog.e("ProviderDetail", "GGUF import failed", e)
                } finally {
                    importingModel = false
                }
            }
        }
    }

    val mmprojLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) mmprojPickedUri = uri.toString()
    }

    val providerInstance = viewModel.getProviderInstance(providerName)
    val savedUrl = providerBaseUrls[providerName]
    val needBaseUrl = isOllama || isCustom

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(providerName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Base URL (Ollama + custom providers)
            if (needBaseUrl) {
                val baseUrlState = remember(providerName, savedUrl) {
                    val initial = if (savedUrl.isNullOrBlank()) providerInstance.defaultBaseUrl else savedUrl
                    TextFieldState(initial)
                }
                LaunchedEffect(baseUrlState.text) {
                    delay(500)
                    viewModel.setProviderBaseUrl(providerName, baseUrlState.text.toString())
                }
                SettingsGroup(
                    title = stringResource(R.string.provider_connection),
                    items = buildList {
                        add {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(painter = painterResource(R.drawable.link_24), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.provider_base_url), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(modifier = Modifier.noOpBringIntoView()) {
                                            OutlinedTextField(state = baseUrlState, placeholder = { Text(providerInstance.defaultBaseUrl, style = MaterialTheme.typography.bodyMedium) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Local model management
            if (isLocal) {
                SettingsGroup(
                    title = stringResource(R.string.local_chat_models),
                    items = buildList {
                        localChatModels.forEach { model ->
                            var showMenu by remember { mutableStateOf(false) }
                            add {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.setActiveLocalChatModel(model.id) }.padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = if (model.id == activeLocalId) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = model.id == activeLocalId, onClick = { viewModel.setActiveLocalChatModel(model.id) }, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(model.alias, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                                            Text("${model.modelId} · ctx=${model.nCtx} · temp=${model.temperature}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Box {
                                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.options), modifier = Modifier.size(16.dp)) }
                                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 16.dp, shape = RoundedCornerShape(12.dp)) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenu = false; showEditModelDialog = model })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; showDeleteModelConfirm = model })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        add {
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = !importingModel) { filePickerLauncher.launch(arrayOf("*/*")) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                if (importingModel) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.importing_model), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.import_model_chat), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                        }
                    }
                )
            }

            // API Keys (non-Local providers)
            if (!isLocal) {
                val providerKeys = apiKeys.filter { it.provider == providerName }
                SettingsGroup(
                    title = stringResource(R.string.provider_api_keys),
                    items = buildList {
                        if (providerKeys.isEmpty()) {
                            add {
                                Text(stringResource(R.string.provider_no_keys, providerName), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        providerKeys.forEach { entry ->
                            var showMenu by remember { mutableStateOf(false) }
                            add {
                                Surface(modifier = Modifier.fillMaxWidth().clickable { viewModel.setActiveApiKey(providerName, entry.id) }.padding(horizontal = 16.dp, vertical = 4.dp), color = if (entry.id == activeApiKeyIds[providerName]) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = entry.id == activeApiKeyIds[providerName], onClick = { viewModel.setActiveApiKey(providerName, entry.id) }, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                                            Text(entry.key.take(4) + "••••••••" + entry.key.takeLast(4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box {
                                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.options), modifier = Modifier.size(16.dp)) }
                                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 16.dp, shape = RoundedCornerShape(12.dp)) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.provider_edit)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenu = false; showKeyDialog = entry })
                                                DropdownMenuItem(text = { Text(stringResource(R.string.provider_delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; showDeleteKeyConfirm = entry })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        add {
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { showKeyDialog = ApiKeyEntry(name = "", key = "", provider = providerName) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.provider_add_key), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                            }
                        }
                    }
                )
            }
        }
    }

    // --- Dialogs ---

    // GGUF error
    if (showGgufError) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showGgufError = false },
            title = { Text(stringResource(R.string.import_invalid_gguf_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.import_invalid_gguf_desc)) },
            confirmButton = { TextButton(onClick = { showGgufError = false }) { Text(stringResource(R.string.ok)) } }
        )
    }

    // Add local model dialog
    if (showAddModelDialog && copiedFilePath != null) {
        var modelId by remember { mutableStateOf("") }
        var modelAlias by remember { mutableStateOf("") }
        var nCtx by remember { mutableStateOf("2048") }
        var temperature by remember { mutableStateOf("0.7") }
        var topP by remember { mutableStateOf("0.9") }
        var maxTokens by remember { mutableStateOf("4096") }
        var idError by remember { mutableStateOf<String?>(null) }
        var formError by remember { mutableStateOf<String?>(null) }
        val idRegex = remember { Regex("^[a-z0-9._-]+\$") }
        val fm = LocalFocusManager.current

        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = {
                scope.launch(Dispatchers.IO) { copiedFilePath?.let { java.io.File(it).delete() } }
                showAddModelDialog = false; copiedFilePath = null
            },
            title = { Text(stringResource(R.string.add_local_chat_model), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { fm.clearFocus() }
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(value = modelId, onValueChange = { modelId = it; idError = null }, label = { Text(stringResource(R.string.model_id_label)) }, supportingText = if (idError != null) {{ Text(idError!!, color = MaterialTheme.colorScheme.error) }} else null, isError = idError != null, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = modelAlias, onValueChange = { modelAlias = it }, label = { Text(stringResource(R.string.model_alias_label)) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = nCtx, onValueChange = { nCtx = it }, label = { Text(stringResource(R.string.local_ctx_size)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = temperature, onValueChange = { temperature = it }, label = { Text(stringResource(R.string.local_temperature)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = topP, onValueChange = { topP = it }, label = { Text(stringResource(R.string.local_top_p)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = maxTokens, onValueChange = { maxTokens = it }, label = { Text(stringResource(R.string.local_max_tokens)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    formError?.let { err -> Spacer(modifier = Modifier.height(8.dp)); Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = modelId.trim()
                    idError = null; formError = null
                    if (id.isBlank()) { idError = "ID is required"; return@TextButton }
                    if (!idRegex.matches(id)) { idError = "Only lowercase a-z, 0-9, . _ - allowed"; return@TextButton }
                    if (viewModel.isLocalModelIdTaken(id)) { idError = "This ID is already in use"; return@TextButton }
                    val nCtxVal = nCtx.toIntOrNull() ?: run { formError = "Context size must be a positive integer"; return@TextButton }
                    if (nCtxVal <= 0) { formError = "Context size must be positive"; return@TextButton }
                    val tempVal = temperature.toFloatOrNull() ?: run { formError = "Temperature must be a number"; return@TextButton }
                    if (tempVal < 0f || tempVal > 2f) { formError = "Temperature must be 0–2"; return@TextButton }
                    val topPVal = topP.toFloatOrNull() ?: run { formError = "Top P must be a number"; return@TextButton }
                    if (topPVal < 0f || topPVal > 1f) { formError = "Top P must be 0–1"; return@TextButton }
                    val maxTokVal = maxTokens.toIntOrNull() ?: run { formError = "Max tokens must be an integer"; return@TextButton }
                    if (maxTokVal <= 0) { formError = "Max tokens must be positive"; return@TextButton }
                    val path = copiedFilePath ?: return@TextButton
                    viewModel.addLocalChatModel(LocalChatModelConfig(
                        modelId = id, alias = modelAlias.ifBlank { id },
                        localFilePath = path, nCtx = nCtxVal,
                        temperature = tempVal, topP = topPVal, maxTokens = maxTokVal
                    ))
                    showAddModelDialog = false; copiedFilePath = null
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { copiedFilePath?.let { java.io.File(it).delete() } }
                    showAddModelDialog = false; copiedFilePath = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Edit local model dialog
    showEditModelDialog?.let { model ->
        var editModelId by remember { mutableStateOf(model.modelId) }
        var editAlias by remember { mutableStateOf(model.alias) }
        var editMmprojPath by remember { mutableStateOf(model.mmprojPath) }
        var editNCtx by remember { mutableStateOf(model.nCtx.toString()) }
        var editTemp by remember { mutableStateOf(model.temperature.toString()) }
        var editTopP by remember { mutableStateOf(model.topP.toString()) }
        var editMaxTokens by remember { mutableStateOf(model.maxTokens.toString()) }
        var editIdError by remember { mutableStateOf<String?>(null) }
        var editFormError by remember { mutableStateOf<String?>(null) }
        val fm = LocalFocusManager.current
        val idRegex = remember { Regex("^[a-z0-9._-]+\$") }

        LaunchedEffect(mmprojPickedUri) {
            if (mmprojPickedUri != null) { editMmprojPath = mmprojPickedUri!!; mmprojPickedUri = null }
        }

        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showEditModelDialog = null },
            title = { Text(stringResource(R.string.edit), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { fm.clearFocus() }
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(value = editModelId, onValueChange = { editModelId = it; editIdError = null }, label = { Text(stringResource(R.string.model_id_label)) }, supportingText = if (editIdError != null) {{ Text(editIdError!!, color = MaterialTheme.colorScheme.error) }} else null, isError = editIdError != null, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editAlias, onValueChange = { editAlias = it }, label = { Text(stringResource(R.string.model_alias_label)) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editNCtx, onValueChange = { editNCtx = it }, label = { Text(stringResource(R.string.local_ctx_size)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    // mmproj: button with file picker
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hasMmproj = editMmprojPath.isNotBlank()
                        val label = if (hasMmproj) editMmprojPath.split("/").lastOrNull() ?: stringResource(R.string.local_mmproj_path) else stringResource(R.string.local_mmproj_path)
                        OutlinedButton(
                            onClick = { mmprojLauncher.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (hasMmproj) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (editMmprojPath.isNotBlank()) {
                        TextButton(onClick = { editMmprojPath = "" }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editTemp, onValueChange = { editTemp = it }, label = { Text(stringResource(R.string.local_temperature)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editTopP, onValueChange = { editTopP = it }, label = { Text(stringResource(R.string.local_top_p)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editMaxTokens, onValueChange = { editMaxTokens = it }, label = { Text(stringResource(R.string.local_max_tokens)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    editFormError?.let { err -> Spacer(modifier = Modifier.height(8.dp)); Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = editModelId.trim()
                    editIdError = null; editFormError = null
                    if (id.isBlank()) { editIdError = "ID is required"; return@TextButton }
                    if (!idRegex.matches(id)) { editIdError = "Only lowercase a-z, 0-9, . _ - allowed"; return@TextButton }
                    if (viewModel.isLocalModelIdTaken(id, excludeId = model.id)) { editIdError = "This ID is already in use"; return@TextButton }
                    val nCtxVal = editNCtx.toIntOrNull() ?: run { editFormError = "Context size must be an integer"; return@TextButton }
                    if (nCtxVal <= 0) { editFormError = "Context size must be positive"; return@TextButton }
                    val tempVal = editTemp.toFloatOrNull() ?: run { editFormError = "Temperature must be a number"; return@TextButton }
                    if (tempVal < 0f || tempVal > 2f) { editFormError = "Temperature must be 0–2"; return@TextButton }
                    val topPVal = editTopP.toFloatOrNull() ?: run { editFormError = "Top P must be a number"; return@TextButton }
                    if (topPVal < 0f || topPVal > 1f) { editFormError = "Top P must be 0–1"; return@TextButton }
                    val maxTokVal = editMaxTokens.toIntOrNull() ?: run { editFormError = "Max tokens must be an integer"; return@TextButton }
                    if (maxTokVal <= 0) { editFormError = "Max tokens must be positive"; return@TextButton }
                    viewModel.updateLocalChatModel(model.id, id, editAlias.ifBlank { id }, nCtxVal, tempVal, topPVal, maxTokVal, mmprojPath = editMmprojPath.trim())
                    showEditModelDialog = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showEditModelDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Delete model confirmation
    showDeleteModelConfirm?.let { model ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeleteModelConfirm = null },
            title = { Text(stringResource(R.string.local_chat_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.local_chat_delete_text, model.alias)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLocalChatModel(model.id)
                    showDeleteModelConfirm = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteModelConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // API Key dialog
    showKeyDialog?.let { entry ->
        var name by remember { mutableStateOf(entry.name) }
        var key by remember { mutableStateOf(entry.key) }
        val isEdit = apiKeys.any { it.id == entry.id }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showKeyDialog = null },
            title = { Text(if (isEdit) stringResource(R.string.provider_edit_key) else stringResource(R.string.provider_add_key_title), fontWeight = FontWeight.Bold) },
            text = {
                val fm = LocalFocusManager.current
                Column(Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { fm.clearFocus() }) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.provider_key_name_hint)) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().noOpBringIntoView())
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.noOpBringIntoView()) {
                        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("${providerName} API Key") }, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && key.isNotBlank()) {
                        if (isEdit) viewModel.updateApiKey(entry.id, name, key) else viewModel.addApiKey(name, key, providerName)
                        showKeyDialog = null
                    }
                }) { Text(if (isEdit) stringResource(R.string.provider_save) else stringResource(R.string.provider_add)) }
            },
            dismissButton = { TextButton(onClick = { showKeyDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Delete key confirmation
    showDeleteKeyConfirm?.let { entry ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showDeleteKeyConfirm = null },
            title = { Text(stringResource(R.string.provider_delete_key_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.provider_delete_key_text, entry.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteApiKey(entry.id)
                    showDeleteKeyConfirm = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.provider_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteKeyConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
