package ru.sber.cb.aichallenge_one.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.sber.cb.aichallenge_one.models.ChatMessage
import ru.sber.cb.aichallenge_one.models.ModelInfo
import ru.sber.cb.aichallenge_one.models.SenderType
import ru.sber.cb.aichallenge_one.models.TokenUsage
import ru.sber.cb.aichallenge_one.viewmodel.ChatViewModel

/**
 * Main Chat Screen with Material Design 3 components.
 * Features:
 * - Enhanced visual hierarchy with proper elevation
 * - Smooth animations and transitions
 * - Dark/Light theme toggle
 * - Responsive layout with sidebar for OpenRouter stats
 * - FAB for quick actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val viewModel = remember { ChatViewModel() }
    val messages by viewModel.messages.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val provider by viewModel.provider.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val tokenUsage by viewModel.tokenUsage.collectAsState()
    val lastResponseTokenUsage by viewModel.lastResponseTokenUsage.collectAsState()
    val responseTimeMs by viewModel.responseTimeMs.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val useRag by viewModel.useRag.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    // Вычисляем hasInput без подписки на изменения inputText в main scope
    val hasInput by remember {
        derivedStateOf {
            viewModel.inputText.value.isNotBlank() && !isLoading
        }
    }

    // Launch notification polling
    LaunchedEffect(Unit) {
        viewModel.pollNotifications()
    }

    Scaffold(
        topBar = {
            EnhancedTopAppBar(
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                onSettingsClick = { showSettings = !showSettings },
                onNewChat = { viewModel.clearChat() },
                isLoading = isLoading
            )
        },
        floatingActionButton = {
            if (hasInput) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.sendMessage() },
                    icon = { Icon(Icons.Filled.Send, "Send message") },
                    text = { Text("Отправить") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = viewModel.snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main chat interface
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Settings Panel (collapsible)
                AnimatedVisibility(
                    visible = showSettings,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SystemPromptInput(
                        systemPrompt = systemPrompt,
                        onSystemPromptChanged = viewModel::onSystemPromptChanged,
                        temperature = temperature,
                        onTemperatureChanged = viewModel::onTemperatureChanged,
                        provider = provider,
                        onProviderChanged = viewModel::onProviderChanged,
                        selectedModel = selectedModel,
                        onModelChanged = viewModel::onModelChanged,
                        availableModels = availableModels,
                        isLoadingModels = isLoadingModels,
                        maxTokens = maxTokens,
                        onMaxTokensChanged = viewModel::onMaxTokensChanged,
                        useRag = useRag,
                        onUseRagChanged = viewModel::onUseRagChanged,
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Message List
                MessageList(
                    messages = messages,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Message Input - передаём ViewModel напрямую для изоляции recomposition
                MessageInput(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Token usage sidebar (only for OpenRouter)
            AnimatedVisibility(
                visible = provider == "openrouter",
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TokenUsageStats(
                            tokenUsage = tokenUsage,
                            modifier = Modifier.fillMaxWidth()
                        )

                        LastResponseTokenUsageStats(
                            lastResponseTokenUsage = lastResponseTokenUsage,
                            responseTimeMs = responseTimeMs,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enhanced Material Design 3 Top App Bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTopAppBar(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChat: () -> Unit,
    isLoading: Boolean
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Chat,
                    contentDescription = "GigaChat",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "GigaChat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        actions = {
            // Theme toggle
            IconButton(onClick = onThemeToggle) {
                Icon(
                    if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Toggle theme",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Settings toggle
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // New chat button
            FilledTonalButton(
                onClick = onNewChat,
                enabled = !isLoading,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Новый чат")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/**
 * Message list with smooth scrolling and animations.
 */
@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        EmptyState(modifier = modifier)
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = messages,
                key = { message -> message.id }  // ✅ Stable key для предотвращения лишних recomposition
            ) { message ->
                AnimatedMessageBubble(message)
            }
            // Extra space for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * Empty state when no messages.
 */
@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.ChatBubble,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                "Начните разговор",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Отправьте сообщение, чтобы начать диалог с AI",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Animated message bubble with enhanced MD3 styling.
 * ✅ Оптимизировано: transition вычисляется один раз и кэшируется.
 */
@Composable
fun AnimatedMessageBubble(message: ChatMessage) {
    val isUser = message.sender == SenderType.USER

    // ✅ remember с ключом для стабильности
    val enterTransition = remember(isUser) {
        slideInHorizontally(
            initialOffsetX = { if (isUser) it else -it },
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        ) + fadeIn()
    }

    AnimatedVisibility(
        visible = true,
        enter = enterTransition
    ) {
        MessageBubble(message)
    }
}

/**
 * Message bubble component.
 */
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == SenderType.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI Avatar
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Bottom),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        ElevatedCard(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 2.dp
            )
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            // User Avatar
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Bottom),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "User",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Enhanced Settings Panel with MD3 styling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptInput(
    systemPrompt: String,
    onSystemPromptChanged: (String) -> Unit,
    temperature: Double,
    onTemperatureChanged: (Double) -> Unit,
    provider: String,
    onProviderChanged: (String) -> Unit,
    selectedModel: String?,
    onModelChanged: (String) -> Unit,
    availableModels: List<ModelInfo>,
    isLoadingModels: Boolean,
    maxTokens: Int?,
    onMaxTokensChanged: (Int?) -> Unit,
    useRag: Boolean,
    onUseRagChanged: (Boolean) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Настройки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // API Provider
            Text(
                "API Provider",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var providerExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = !providerExpanded && !isLoading }
            ) {
                OutlinedTextField(
                    value = if (provider == "gigachat") "GigaChat" else "OpenRouter",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("GigaChat") },
                        onClick = {
                            onProviderChanged("gigachat")
                            providerExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("OpenRouter") },
                        onClick = {
                            onProviderChanged("openrouter")
                            providerExpanded = false
                        }
                    )
                }
            }

            // RAG Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useRag,
                    onCheckedChange = onUseRagChanged,
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Use RAG (Retrieval-Augmented Generation)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Model Selector (OpenRouter only)
            AnimatedVisibility(visible = provider == "openrouter") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Model",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLoadingModels) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (availableModels.isNotEmpty()) {
                        var modelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = !modelExpanded && !isLoading }
                        ) {
                            OutlinedTextField(
                                value = selectedModel?.let { modelId ->
                                    availableModels.find { it.id == modelId }?.name ?: modelId
                                } ?: "Select a model",
                                onValueChange = {},
                                readOnly = true,
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.name) },
                                        onClick = {
                                            onModelChanged(model.id)
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Max Tokens
                    Text(
                        "Max Tokens (optional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = maxTokens?.toString() ?: "",
                        onValueChange = { newValue ->
                            if (newValue.isEmpty()) onMaxTokensChanged(null)
                            else newValue.toIntOrNull()?.let { onMaxTokensChanged(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., 1024") },
                        enabled = !isLoading,
                        singleLine = true
                    )
                }
            }

            HorizontalDivider()

            // System Prompt
            Text(
                "Системный промпт",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите системный промпт...") },
                enabled = !isLoading,
                maxLines = 3,
                minLines = 2
            )

            // Temperature
            Text(
                "Temperature: ${temperature.toString().take(4)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Slider(
                    value = temperature.toFloat(),
                    onValueChange = { onTemperatureChanged(it.toDouble()) },
                    valueRange = 0f..2f,
                    steps = 19,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = temperature.toString().take(4),
                    onValueChange = { newValue ->
                        newValue.toDoubleOrNull()?.let { onTemperatureChanged(it) }
                    },
                    modifier = Modifier.width(90.dp),
                    enabled = !isLoading,
                    singleLine = true
                )
            }
        }
    }
}

/**
 * Enhanced Message Input with MD3 styling.
 * ✅ Оптимизировано: работает напрямую с ViewModel для изоляции recomposition.
 * Изменения inputText не вызывают recomposition родительских компонентов.
 */
@Composable
fun MessageInput(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    // ✅ Подписываемся на state только внутри этого компонента
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Surface(
        modifier = modifier,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = viewModel::onInputChanged,  // ✅ Прямой вызов ViewModel метода
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                            if (!keyEvent.isShiftPressed && inputText.isNotBlank() && !isLoading) {
                                viewModel.sendMessage()
                                true
                            } else false
                        } else false
                    },
                placeholder = { Text("Введите сообщение...") },
                enabled = !isLoading,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/**
 * Token Usage Statistics Card.
 */
@Composable
fun TokenUsageStats(
    tokenUsage: TokenUsage,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Token Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            StatRow("Prompt Tokens:", tokenUsage.promptTokens.toString())
            StatRow("Completion Tokens:", tokenUsage.completionTokens.toString())
            HorizontalDivider()
            StatRow("Total Tokens:", tokenUsage.totalTokens.toString(), isTotal = true)
            Spacer(Modifier.height(8.dp))
            Text(
                "Cumulative for current session",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Last Response Token Usage Statistics Card.
 */
@Composable
fun LastResponseTokenUsageStats(
    lastResponseTokenUsage: TokenUsage?,
    responseTimeMs: Long?,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Last Response",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            if (lastResponseTokenUsage != null) {
                StatRow("Prompt Tokens:", lastResponseTokenUsage.promptTokens.toString())
                StatRow("Completion Tokens:", lastResponseTokenUsage.completionTokens.toString())
                StatRow("Total Tokens:", lastResponseTokenUsage.totalTokens.toString())
                if (responseTimeMs != null) {
                    HorizontalDivider()
                    StatRow("Response Time:", "${responseTimeMs}ms")
                }
            } else {
                Text(
                    "No response yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Statistics Row Component.
 */
@Composable
fun StatRow(
    label: String,
    value: String,
    isTotal: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
