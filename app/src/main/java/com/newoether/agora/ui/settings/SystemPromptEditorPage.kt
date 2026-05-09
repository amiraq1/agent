package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.PredefinedVariables
import com.newoether.agora.data.PromptItemType
import com.newoether.agora.data.PromptTemplateItem
import com.newoether.agora.data.SystemPromptEntry

private fun variableDisplayName(key: String): String = when (key) {
    PredefinedVariables.TIME -> "Time"
    PredefinedVariables.DATE -> "Date"
    PredefinedVariables.ACTIVE_MEMORY -> "Active Memory"
    PredefinedVariables.MODEL_ID -> "Model ID"
    else -> key
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptEditorPage(
    entry: SystemPromptEntry?,
    onSave: (
        title: String,
        systemItems: List<PromptTemplateItem>,
        userPrependItems: List<PromptTemplateItem>,
        userPostpendItems: List<PromptTemplateItem>
    ) -> Unit,
    onBack: () -> Unit
) {
    val isEdit = entry != null
    var title by remember { mutableStateOf(entry?.title ?: "") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val systemItems = remember {
        mutableStateListOf<PromptTemplateItem>().also { list ->
            entry?.resolvedSystemItems?.let { list.addAll(it) }
        }
    }
    val userPrependItems = remember {
        mutableStateListOf<PromptTemplateItem>().also { list ->
            entry?.userPrependItems?.let { list.addAll(it) }
        }
    }
    val userPostpendItems = remember {
        mutableStateListOf<PromptTemplateItem>().also { list ->
            entry?.userPostpendItems?.let { list.addAll(it) }
        }
    }

    var showVariablePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    val currentItems: MutableList<PromptTemplateItem> = when (selectedTab) {
        0 -> systemItems
        1 -> userPrependItems
        else -> userPostpendItems
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) stringResource(R.string.template_edit_title) else stringResource(R.string.template_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (title.isBlank()) {
                            titleError = true
                            return@TextButton
                        }
                        onSave(title, systemItems.toList(), userPrependItems.toList(), userPostpendItems.toList())
                    }) {
                        Text(stringResource(R.string.provider_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                label = { Text(stringResource(R.string.prompts_title_hint)) },
                isError = titleError,
                supportingText = if (titleError) {{ Text(stringResource(R.string.template_title_required)) }} else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.template_tab_system), maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.template_tab_prepend), maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.template_tab_postpend), maxLines = 1) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Items
            if (currentItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.template_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            for (i in currentItems.indices) {
                val item = currentItems[i]
                TemplateItemRow(
                    item = item,
                    onChange = { updated -> currentItems[i] = updated },
                    onDelete = { currentItems.removeAt(i) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = {
                    currentItems.add(PromptTemplateItem(type = PromptItemType.CUSTOM, value = ""))
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.template_add_text))
                }
                FilledTonalButton(onClick = { showVariablePicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.template_add_variable))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Preview
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.template_preview),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                val previewText = PredefinedVariables.compile(
                    items = currentItems.toList(),
                    runtimeValues = PredefinedVariables.EXAMPLE_VALUES
                )
                Text(
                    text = previewText.ifEmpty { stringResource(R.string.template_preview_empty) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Variable picker dialog
    if (showVariablePicker) {
        AlertDialog(
            onDismissRequest = { showVariablePicker = false },
            title = { Text(stringResource(R.string.template_variable_picker_title)) },
            text = {
                Column {
                    for (key in PredefinedVariables.ALL) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentItems.add(PromptTemplateItem(type = PromptItemType.PREDEFINED, value = key))
                                    showVariablePicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = {
                                    currentItems.add(PromptTemplateItem(type = PromptItemType.PREDEFINED, value = key))
                                    showVariablePicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = variableDisplayName(key),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "{${key}}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVariablePicker = false }) {
                    Text(stringResource(R.string.provider_cancel))
                }
            }
        )
    }
}

@Composable
private fun TemplateItemRow(
    item: PromptTemplateItem,
    onChange: (PromptTemplateItem) -> Unit,
    onDelete: () -> Unit
) {
    when (item.type) {
        PromptItemType.CUSTOM -> {
            var text by remember(item.id) { mutableStateOf(item.value) }
            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                    onChange(item.copy(value = newValue))
                },
                label = { Text(stringResource(R.string.template_custom_text_label)) },
                trailingIcon = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.provider_delete))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        PromptItemType.PREDEFINED -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = variableDisplayName(item.value),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "{${item.value}}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.provider_delete),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
