package com.newoether.agora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.MessageStatus
import com.newoether.agora.model.Participant
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MessageList(
    messages: List<ChatMessage>, 
    allMessages: List<ChatMessage> = emptyList(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    state: LazyListState = rememberLazyListState(),
    isLoading: Boolean = false,
    visualizeContextRollout: Boolean = false,
    maxContextWindow: Int = 20,
    bottomBarHeight: androidx.compose.ui.unit.Dp = 0.dp,
    viewportHeight: Int = 0,
    messageHeights: MutableMap<String, Int> = remember { mutableStateMapOf() },
    onEditMessage: (String, String) -> Unit = { _, _ -> },
    onSwitchBranch: (String?, Int) -> Unit = { _, _ -> }
) {
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Calculate which messages are in context
    val currentPath = messages.filter { it.participant != Participant.ERROR }
    val contextStartIndex = if (currentPath.size > maxContextWindow) currentPath.size - maxContextWindow else 0
    val inContextIds = currentPath.drop(contextStartIndex).map { it.id }.toSet()
    
    val lastUserMessageIndex = messages.indexOfLast { it.participant == Participant.USER }
    
    // Calculation of extra padding to allow scrolling last user message to 180dp from top.
    // We don't wrap this in remember to ensure it reacts to every change in messageHeights.
    val extraPadding = if (lastUserMessageIndex == -1 || viewportHeight == 0) {
        0.dp
    } else {
        with(density) {
            val vDp = viewportHeight.toDp()
            
            // Target position: top of the last user message at 180dp from top of screen
            val targetTopDp = 180.dp
            
            // Available space from target top to the top of the bottom bar
            // MessageList's contentPadding.bottom already includes bottomBarHeight + 8.dp
            val availableSpaceDp = vDp - targetTopDp - (bottomBarHeight + 8.dp)
            
            // Height of all messages from the last user message onwards (inclusive)
            // messageHeights[id] already includes the vertical padding of the message
            var contentHeightPx = 0
            for (i in lastUserMessageIndex until messages.size) {
                contentHeightPx += messageHeights[messages[i].id] ?: 0
            }
            val contentHeightDp = contentHeightPx.toDp()
            
            (availableSpaceDp - contentHeightDp).coerceAtLeast(0.dp)
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            reverseLayout = false,
            state = state
        ) {
            items(messages, key = { it.id }) { message ->
                val isLastMessage = messages.lastOrNull()?.id == message.id
                val isInContext = inContextIds.contains(message.id)
                
                // Calculate branches for this message's parent
                val siblings = allMessages.filter { it.parentId == message.parentId }
                    .sortedBy { it.timestamp }
                val branchIndex = siblings.indexOfFirst { it.id == message.id }
                val totalBranches = siblings.size

                MessageItem(
                    message = message, 
                    onEdit = { id, text -> 
                        onEditMessage(id, text)
                        editingMessageId = null
                    },
                    isStreaming = isLastMessage && isLoading && message.participant == Participant.MODEL,
                    isEditingAllowed = !isLoading && (editingMessageId == null || editingMessageId == message.id),
                    isEditing = editingMessageId == message.id,
                    isInContext = isInContext,
                    visualizeContextRollout = visualizeContextRollout,
                    onStartEdit = { editingMessageId = message.id },
                    onCancelEdit = { editingMessageId = null },
                    branchIndex = branchIndex,
                    totalBranches = totalBranches,
                    onSwitchBranch = { direction -> onSwitchBranch(message.parentId, direction) },
                    onHeightChanged = { height -> messageHeights[message.id] = height }
                )
            }
            item {
                // The spacer ensures we can scroll the last user message to the 180dp mark
                Spacer(modifier = Modifier.height(extraPadding))
            }
        }
    }
}

@Composable
fun MessageItem(
    message: ChatMessage, 
    onEdit: (String, String) -> Unit, 
    isStreaming: Boolean = false,
    isEditingAllowed: Boolean = true,
    isEditing: Boolean = false,
    isInContext: Boolean = false,
    visualizeContextRollout: Boolean = false,
    onStartEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    branchIndex: Int = 0,
    totalBranches: Int = 1,
    onSwitchBranch: (Int) -> Unit = {},
    onHeightChanged: (Int) -> Unit = {}
) {
    var editText by remember { mutableStateOf(message.text) }
    // Track if this is the first time this message is being composed to skip initial animation
    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { isFirstComposition = false }

    // Re-trigger height reporting when text or status changes
    var currentHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(message.text, message.status, isEditing) {
        // A small delay to allow the layout to settle before reporting the new height
        kotlinx.coroutines.delay(50)
        onHeightChanged(currentHeight)
    }

    // Reset edit text when entering edit mode
    LaunchedEffect(isEditing) {
        if (isEditing) {
            editText = message.text
        }
    }

    val alignment = when (message.participant) {
        Participant.USER -> Alignment.End
        Participant.MODEL -> Alignment.Start
        Participant.ERROR -> Alignment.CenterHorizontally
    }

    val backgroundColor = when (message.participant) {
        Participant.USER -> MaterialTheme.colorScheme.primaryContainer
        Participant.MODEL -> Color.Transparent
        Participant.ERROR -> MaterialTheme.colorScheme.errorContainer
    }

    val textColor = when (message.participant) {
        Participant.USER -> MaterialTheme.colorScheme.onPrimaryContainer
        Participant.MODEL -> MaterialTheme.colorScheme.onSurface
        Participant.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    val shape = when (message.participant) {
        Participant.USER -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        Participant.MODEL -> RoundedCornerShape(0.dp)
        Participant.ERROR -> RoundedCornerShape(12.dp)
    }

    val currentTypography = MaterialTheme.typography
    val customTypography = markdownTypography(
        text = currentTypography.bodyMedium,
        h1 = currentTypography.headlineSmall,
        h2 = currentTypography.titleLarge,
        h3 = currentTypography.titleMedium,
        h4 = currentTypography.titleSmall,
        h5 = currentTypography.titleSmall,
        h6 = currentTypography.titleSmall,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { 
                currentHeight = it.height
                onHeightChanged(it.height) 
            }
            .padding(vertical = 8.dp)
            .then(if (visualizeContextRollout && !isInContext) Modifier.alpha(0.38f) else Modifier),
        horizontalAlignment = alignment
    ) {
        if (message.participant == Participant.USER) {
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = shape,
                    color = backgroundColor,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .then(if (!isFirstComposition) Modifier.animateContentSize(animationSpec = tween(150)) else Modifier)
                ) {
                    if (isEditing) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            TextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { 
                                    onCancelEdit()
                                    editText = message.text
                                }) {
                                    Text("Cancel")
                                }
                                TextButton(onClick = {
                                    onEdit(message.id, editText)
                                }) {
                                    Text("Send")
                                }
                            }
                        }
                    } else {
                        // User messages use simple text for stability
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Branch Selector at the bottom left of the bubble area
                    if (totalBranches > 1 && !isEditing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(100))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp)
                        ) {
                            IconButton(
                                onClick = { onSwitchBranch(-1) },
                                enabled = branchIndex > 0 && isEditingAllowed,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft, 
                                    contentDescription = "Previous Branch",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (branchIndex > 0 && isEditingAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                            Text(
                                text = "${branchIndex + 1} / $totalBranches",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { onSwitchBranch(1) },
                                enabled = branchIndex < totalBranches - 1 && isEditingAllowed,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight, 
                                    contentDescription = "Next Branch",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (branchIndex < totalBranches - 1 && isEditingAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (!isEditing && message.participant == Participant.USER) {
                        TextButton(
                            onClick = { onStartEdit() },
                            modifier = Modifier.padding(top = 4.dp),
                            enabled = isEditingAllowed,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        ) {
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            // Model or Error
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .then(if (isStreaming || !isFirstComposition) Modifier.animateContentSize(animationSpec = tween(150)) else Modifier)
            ) {
                Column {
                    // Status Header
                    if (message.participant == Participant.MODEL) {
                        val infiniteTransition = rememberInfiniteTransition(label = "sending_rotation")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing)
                            ),
                            label = "rotation"
                        )

                        val statusText = if (isStreaming) {
                            "Sending..."
                        } else {
                            when (message.status) {
                                MessageStatus.SENDING -> "Sending..." // Fallback for DB state
                                MessageStatus.SUCCESS -> if (message.tokenCount > 0) "Cost ${message.tokenCount} tokens" else null
                                MessageStatus.STOPPED -> null
                                MessageStatus.ERROR -> null
                            }
                        }
                        
                        if (statusText != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                if (isStreaming || message.status == MessageStatus.SENDING) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sending",
                                        modifier = Modifier
                                            .size(12.dp)
                                            .rotate(rotation),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    val icon = when (message.status) {
                                        MessageStatus.SUCCESS -> Icons.Default.CheckCircle
                                        MessageStatus.STOPPED -> Icons.Default.Stop
                                        else -> Icons.Default.Info
                                    }
                                    val tint = if (message.status == MessageStatus.SUCCESS) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                    
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = statusText,
                                        modifier = Modifier.size(12.dp),
                                        tint = tint
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (message.status == MessageStatus.ERROR || message.status == MessageStatus.STOPPED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Throttle updates during streaming to prevent Markdown flashing
                    var debouncedText by remember { mutableStateOf(message.text) }
                    
                    if (isStreaming) {
                        LaunchedEffect(message.text) {
                            // Only update text every 100ms to allow Markdown layout to settle
                            kotlinx.coroutines.delay(100)
                            debouncedText = message.text
                        }
                    } else {
                        debouncedText = message.text
                    }

                    // Content Rendering
                    Column {
                        val isError = message.status == MessageStatus.ERROR || message.participant == Participant.ERROR
                        
                        if (isError) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Error",
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = debouncedText.ifEmpty { "Generation failed." },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 18.sp
                                        ),
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        } else if (debouncedText.isNotEmpty()) {
                            Markdown(
                                content = debouncedText,
                                modifier = Modifier.fillMaxWidth(),
                                typography = customTypography
                            )
                        }

                        // Bottom status text for Stopped
                        if (!isStreaming) {
                            if (message.status == MessageStatus.STOPPED) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = if (debouncedText.isNotEmpty()) 8.dp else 0.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Generation stopped.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
}

fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    color: Color,
    width: androidx.compose.ui.unit.Dp = 3.dp
): Modifier = drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val viewPortHeight = size.height
        val totalHeight = scrollState.maxValue + viewPortHeight
        val thumbHeight = (viewPortHeight / totalHeight) * viewPortHeight
        val thumbOffset = (scrollState.value / totalHeight.toFloat()) * viewPortHeight
        
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 4.dp.toPx(), thumbOffset),
            size = Size(width.toPx(), thumbHeight),
            cornerRadius = CornerRadius(width.toPx() / 2)
        )
    }
}

@Composable
fun ChatBottomBar(
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit = {},
    isLoading: Boolean,
    enabledModels: Set<String>,
    selectedModel: String,
    modelAliases: Map<String, String> = emptyMap(),
    codeExecutionEnabled: Boolean = false,
    googleSearchEnabled: Boolean = false,
    onCodeExecutionToggle: (Boolean) -> Unit = {},
    onGoogleSearchToggle: (Boolean) -> Unit = {},
    onModelSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textFieldState = rememberTextFieldState()
    val scrollState = rememberScrollState()
    var expanded by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val isModelValid = selectedModel.isNotBlank() && enabledModels.contains(selectedModel)
    
    val isMultiLine = textFieldState.text.contains('\n') || textFieldState.text.length > 50

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.fillMaxHeight().statusBarsPadding() else Modifier)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { isExpanded = false }) {
                    Icon(
                        Icons.Default.CloseFullscreen,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Input widget
        Box(modifier = Modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.weight(1f) else Modifier)
        ) {
            TextField(
                state = textFieldState,
                scrollState = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isExpanded) Modifier.fillMaxHeight() else Modifier)
                    .verticalScrollbar(
                        scrollState = scrollState,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                placeholder = { 
                    Text(
                        "Ask Agora anything...", 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ) 
                },
                enabled = true,
                lineLimits = TextFieldLineLimits.MultiLine(1, if (isExpanded) Int.MAX_VALUE else 6),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            
            if (isMultiLine && !isExpanded) {
                IconButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 4.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInFull, 
                        contentDescription = "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Fixed bottom control bar - stays at bottom because weight(1f) is above it
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Model and settings on the bottom left
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                val displayText = when {
                    isModelValid -> modelAliases[selectedModel] ?: selectedModel.removePrefix("models/")
                    enabledModels.isNotEmpty() -> "Select Model"
                    else -> "No model selected"
                }

                Box {
                    TextButton(
                        onClick = { expanded = true },
                        modifier = Modifier.widthIn(max = 160.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 8.dp)
                    ) {
                        Text(
                            displayText, 
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isModelValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (enabledModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No models enabled", style = MaterialTheme.typography.bodyMedium) },
                                onClick = { expanded = false },
                                enabled = false
                            )
                        } else {
                            enabledModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(modelAliases[model] ?: model.removePrefix("models/"), style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        onModelSelect(model)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Settings, 
                        contentDescription = "Settings", 
                        modifier = Modifier.size(18.dp), 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                var toolsMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { toolsMenuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.MoreVert, 
                            contentDescription = "Tools", 
                            modifier = Modifier.size(18.dp), 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = toolsMenuExpanded,
                        onDismissRequest = { toolsMenuExpanded = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Code Execution")
                                }
                            },
                            trailingIcon = {
                                Switch(
                                    checked = codeExecutionEnabled,
                                    onCheckedChange = { 
                                        onCodeExecutionToggle(it)
                                    },
                                    modifier = Modifier.scale(0.7f)
                                )
                            },
                            onClick = { onCodeExecutionToggle(!codeExecutionEnabled) }
                        )
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Google Search")
                                }
                            },
                            trailingIcon = {
                                Switch(
                                    checked = googleSearchEnabled,
                                    onCheckedChange = { 
                                        onGoogleSearchToggle(it)
                                    },
                                    modifier = Modifier.scale(0.7f)
                                )
                            },
                            onClick = { onGoogleSearchToggle(!googleSearchEnabled) }
                        )
                    }
                }
            }

            // Send or Stop button
            val canSend = textFieldState.text.isNotBlank() && !isLoading && isModelValid
            
            FloatingActionButton(
                onClick = {
                    if (isLoading) {
                        onStopGeneration()
                    } else if (canSend) {
                        onSendMessage(textFieldState.text.toString())
                        textFieldState.edit { replace(0, length, "") }
                        isExpanded = false // Auto-collapse on send
                    }
                },
                containerColor = if (canSend || isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (canSend || isLoading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            ) {
                if (isLoading) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
