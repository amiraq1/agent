package com.newoether.agora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.data.local.ChatDatabase
import com.newoether.agora.model.Participant
import com.newoether.agora.ui.components.ChatBottomBar
import com.newoether.agora.ui.components.MessageList
import com.newoether.agora.ui.screens.SettingsScreen
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.ChatViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = Room.databaseBuilder(
            applicationContext,
            ChatDatabase::class.java,
            "agora_db"
        )
        .fallbackToDestructiveMigration()
        .build()
        val settingsManager = SettingsManager(applicationContext)

        enableEdgeToEdge()
        setContent {
            AgoraTheme {
                val factory = ChatViewModelFactory(settingsManager, database.chatDao())
                val viewModel: ChatViewModel = viewModel(factory = factory)
                MainNavigation(viewModel)
            }
        }
    }
}

@Composable
fun MainNavigation(viewModel: ChatViewModel) {
    val navController = rememberNavController()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController, 
            startDestination = "chat",
            enterTransition = { slideInHorizontally(animationSpec = tween(400)) { it } + fadeIn(animationSpec = tween(400)) },
            exitTransition = { slideOutHorizontally(animationSpec = tween(400)) { -it / 3 } + fadeOut(animationSpec = tween(400)) },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(400)) { -it / 3 } + fadeIn(animationSpec = tween(400)) },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(400)) { it } + fadeOut(animationSpec = tween(400)) }
        ) {
            composable("chat") {
                ChatApp(
                    viewModel = viewModel,
                    onOpenSettings = { 
                        if (navController.currentDestination?.route == "chat") {
                            navController.navigate("settings")
                        }
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { 
                        if (navController.currentDestination?.route == "settings") {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatApp(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val enabledModels by viewModel.enabledModels.collectAsState()
    val modelAliases by viewModel.modelAliases.collectAsState()
    val isNewChatMode by viewModel.isNewChatMode.collectAsState()
    val totalTokens by viewModel.totalTokens.collectAsState()
    val visualizeContextRollout by viewModel.visualizeContextRollout.collectAsState()
    val maxContextWindow by viewModel.maxContextWindow.collectAsState()
    val codeExecutionEnabled by viewModel.codeExecutionEnabled.collectAsState()
    val googleSearchEnabled by viewModel.googleSearchEnabled.collectAsState()

    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var conversationToRename by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val drawerWidth = configuration.screenWidthDp.dp * 0.8f
    var bottomBarHeightPx by rememberSaveable { mutableFloatStateOf(0f) }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
    val listState = viewModel.listState

    // Persistent layout state to prevent jumps during transitions
    val messageHeights = remember { mutableStateMapOf<String, Int>() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    // Clear heights when switching conversations to ensure fresh calculations
    LaunchedEffect(currentConversationId) {
        messageHeights.clear()
    }

    BackHandler(enabled = drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = DrawerDefaults.scrimColor,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 2.dp,
                modifier = Modifier
                    .width(drawerWidth)
                    .graphicsLayer {
                        // Force the drawer onto its own layer for smoother swipe performance
                        clip = true
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text("Chat History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            viewModel.createNewChat()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Chat")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(conversations) { conversation ->
                            val isSelected = conversation.id == currentConversationId
                            var showMenu by remember { mutableStateOf(false) }
                            var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
                            var lastPosition by remember { mutableStateOf(Offset.Zero) }
                            val density = LocalDensity.current

                            Box {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(CircleShape)
                                        .pointerInput(showMenu) {
                                            if (!showMenu) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                                        lastPosition = event.changes.first().position
                                                    }
                                                }
                                            }
                                        }
                                        .combinedClickable(
                                            onClick = {
                                                viewModel.selectConversation(conversation.id)
                                                scope.launch { drawerState.close() }
                                            },
                                            onLongClick = {
                                                pressOffset = with(density) {
                                                    // Clamp horizontal offset to prevent menu overflow
                                                    val x = lastPosition.x.toDp().coerceIn(16.dp, 200.dp)
                                                    DpOffset(x, lastPosition.y.toDp() - 28.dp)
                                                }
                                                showMenu = true
                                            }
                                        ),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = conversation.title,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    offset = pressOffset,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showRenameDialog = conversation.id
                                            conversationToRename = conversation.title
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirmDialog = conversation.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    FilledTonalButton(
                        onClick = { 
                            onOpenSettings()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings")
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height }
        ) {
            // Global static background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(bottom = 60.dp)
                    ) {
                        TopAppBar(
                            title = { 
                                val currentTitle = if (isNewChatMode) "Agora Chat" else conversations.find { it.id == currentConversationId }?.title ?: "Agora Chat"
                                Column {
                                    Text(currentTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) 
                                    if (!isNewChatMode && totalTokens > 0) {
                                        Text("Total: $totalTokens tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.createNewChat() }) {
                                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            )
                        )
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = isNewChatMode,
                        transitionSpec = {
                            val enterEasing = LinearOutSlowInEasing
                            val exitEasing = FastOutLinearInEasing
                            (fadeIn(animationSpec = tween(400, easing = enterEasing)) + scaleIn(initialScale = 0.97f, animationSpec = tween(400, easing = enterEasing)))
                                .togetherWith(fadeOut(animationSpec = tween(200, easing = exitEasing)))
                        },
                        label = "MainContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetKey ->
                        if (targetKey) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(y = (-40).dp), 
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Ask Agora Anything...",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            MessageList(
                                messages = messages,
                                allMessages = allMessages,
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                isLoading = isLoading,
                                visualizeContextRollout = visualizeContextRollout,
                                maxContextWindow = maxContextWindow,
                                bottomBarHeight = bottomBarHeight,
                                viewportHeight = viewportHeightPx,
                                messageHeights = messageHeights,
                                onEditMessage = { id, text -> viewModel.editMessage(id, text) },
                                onSwitchBranch = { parentId, direction -> viewModel.switchBranch(parentId, direction) },
                                contentPadding = PaddingValues(
                                    start = 8.dp, 
                                    end = 8.dp, 
                                    top = 140.dp, 
                                    bottom = bottomBarHeight + 8.dp
                                )
                            )
                        }
                    }
                }
            }

            // Solid Bottom Bar with surface color extending to bottom
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { 
                        bottomBarHeightPx = it.height.toFloat()
                    },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    ChatBottomBar(
                        onSendMessage = { 
                            val isFirstMessage = messages.isEmpty()
                            viewModel.sendMessage(it)
                            scope.launch {
                                // Only scroll if it's not the first message in the chat
                                if (!isFirstMessage) {
                                    // Wait for keyboard/layout to start moving (200ms)
                                    kotlinx.coroutines.delay(200)
                                    
                                    val totalMessagesHeightPx = messages.sumOf { messageHeights[it.id] ?: 0 }
                                    
                                    with(density) {
                                        val topPaddingPx = 140.dp.toPx()
                                        val bottomPaddingPx = (bottomBarHeight + 8.dp).toPx()
                                        
                                        // Re-calculate extraPadding in Px as it's defined in MessageList
                                        val lastUserMessageIndex = messages.indexOfLast { it.participant == Participant.USER }
                                        var contentBelowUserPx = 0
                                        if (lastUserMessageIndex != -1) {
                                            for (i in lastUserMessageIndex until messages.size) {
                                                contentBelowUserPx += messageHeights[messages[i].id] ?: 0
                                            }
                                        }
                                        val targetTopPx = 180.dp.toPx() // targetTopDp from MessageList
                                        val availableSpacePx = viewportHeightPx - targetTopPx - bottomPaddingPx
                                        val extraPaddingPx = (availableSpacePx - contentBelowUserPx).coerceAtLeast(0f)
                                        
                                        val totalContentHeightPx = totalMessagesHeightPx + topPaddingPx + bottomPaddingPx + extraPaddingPx
                                        
                                        // Calculate current offset by summing heights of items before the first visible one
                                        var currentOffsetPx = listState.firstVisibleItemScrollOffset.toFloat()
                                        for (i in 0 until listState.firstVisibleItemIndex) {
                                            if (i < messages.size) {
                                                currentOffsetPx += (messageHeights[messages[i].id] ?: 0)
                                            }
                                        }
                                        
                                        val remainingScrollPx = (totalContentHeightPx - viewportHeightPx - currentOffsetPx).coerceAtLeast(0f)

                                        if (remainingScrollPx > 0f) {
                                            listState.animateScrollBy(
                                                value = remainingScrollPx, 
                                                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onStopGeneration = { viewModel.stopGeneration() },
                        isLoading = isLoading,
                        enabledModels = enabledModels,
                        selectedModel = selectedModel,
                        modelAliases = modelAliases,
                        codeExecutionEnabled = codeExecutionEnabled,
                        googleSearchEnabled = googleSearchEnabled,
                        onCodeExecutionToggle = { viewModel.setCodeExecutionEnabled(it) },
                        onGoogleSearchToggle = { viewModel.setGoogleSearchEnabled(it) },
                        onModelSelect = { viewModel.setSelectedModel(it) },
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier
                    )
                }
            }
        }
    }

    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Chat") },
            text = {
                TextField(
                    value = conversationToRename,
                    onValueChange = { conversationToRename = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameConversation(showRenameDialog!!, conversationToRename)
                    showRenameDialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete Chat?") },
            text = { Text("Are you sure you want to delete this conversation? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(showDeleteConfirmDialog!!)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
