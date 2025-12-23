# Frontend Module - Compose Multiplatform Web UI

## Overview

The Frontend module is a Compose Multiplatform web application that provides an interactive chat interface for AI
conversations with support for multiple providers, RAG-enhanced responses, and MCP tool calling. It's built using
Kotlin/JS (Wasm) and runs entirely in the browser.

**Port**: 8080 (served with backend)
**Technology**: Compose Multiplatform + Kotlin/JS (Wasm) + Ktor Client
**Pattern**: MVVM (Model-View-ViewModel)

## Architecture

```
User Input → ChatScreen (View)
                ↓
         ChatViewModel (StateFlow)
                ↓
         ChatApi (HTTP Client)
                ↓
         Server API (port 8080)
```

## Key Components

### 1. main.kt

**Purpose**: Application entry point for web platform

**Features**:

- Initializes Compose web runtime
- Renders root `App()` composable to `#root` div
- Platform-specific initialization

```kotlin
fun main() {
    onWasmReady {
        CanvasBasedWindow("AiChallenge_One") {
            App()
        }
    }
}
```

### 2. App.kt

**Purpose**: Root composable with theme and ViewModel initialization

**Structure**:

```kotlin
@Composable
fun App() {
    val viewModel = remember { ChatViewModel() }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        // Collect StateFlows
        val messages by viewModel.messages.collectAsState()
        val inputText by viewModel.inputText.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val systemPrompt by viewModel.systemPrompt.collectAsState()
        val temperature by viewModel.temperature.collectAsState()
        val provider by viewModel.provider.collectAsState()
        val selectedModel by viewModel.selectedModel.collectAsState()
        val maxTokens by viewModel.maxTokens.collectAsState()
        val useRag by viewModel.useRag.collectAsState()
        val tokenUsage by viewModel.tokenUsage.collectAsState()

        ChatScreen(
            messages = messages,
            inputText = inputText,
            isLoading = isLoading,
            // ... all state and callbacks
        )
    }
}
```

**Responsibilities**:

- ViewModel lifecycle management
- Theme configuration (Material 3 Dark)
- State collection from ViewModel
- Passing state and callbacks to ChatScreen

### 3. ChatViewModel.kt

**Purpose**: State management and business logic (MVVM pattern)

**State (StateFlow)**:

```kotlin
// Messages
private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
val messages: StateFlow<List<ChatMessage>> = _messages

// Input
private val _inputText = MutableStateFlow("")
val inputText: StateFlow<String> = _inputText

// Loading state
private val _isLoading = MutableStateFlow(false)
val isLoading: StateFlow<Boolean> = _isLoading

// Configuration
private val _systemPrompt = MutableStateFlow("")
val systemPrompt: StateFlow<String> = _systemPrompt

private val _temperature = MutableStateFlow(0.7)
val temperature: StateFlow<Double> = _temperature

private val _provider = MutableStateFlow("gigachat")
val provider: StateFlow<String> = _provider

private val _selectedModel = MutableStateFlow<String?>(null)
val selectedModel: StateFlow<String?> = _selectedModel

private val _maxTokens = MutableStateFlow<Int?>(null)
val maxTokens: StateFlow<Int?> = _maxTokens

private val _useRag = MutableStateFlow(false)
val useRag: StateFlow<Boolean> = _useRag

// Token usage (OpenRouter only)
private val _tokenUsage = MutableStateFlow<TokenUsage?>(null)
val tokenUsage: StateFlow<TokenUsage?> = _tokenUsage
```

**Key Methods**:

#### sendMessage()

```kotlin
fun sendMessage() {
    if (inputText.value.isBlank() || isLoading.value) return

    val userMessage = ChatMessage(
        text = inputText.value,
        sender = SenderType.USER
    )
    _messages.value += userMessage
    _isLoading.value = true

    viewModelScope.launch {
        try {
            val response = chatApi.sendMessage(
                text = inputText.value,
                systemPrompt = systemPrompt.value,
                temperature = temperature.value,
                provider = provider.value,
                model = selectedModel.value,
                maxTokens = maxTokens.value,
                useRag = useRag.value
            )

            val botMessage = ChatMessage(
                text = response.text,
                sender = SenderType.BOT
            )
            _messages.value += botMessage

            // Update token usage (if available)
            _tokenUsage.value = response.tokenUsage

            _inputText.value = ""
        } catch (e: Exception) {
            val errorMessage = ChatMessage(
                text = "Error: ${e.message}",
                sender = SenderType.BOT
            )
            _messages.value += errorMessage
        } finally {
            _isLoading.value = false
        }
    }
}
```

#### Input Handlers

```kotlin
fun onInputTextChange(text: String) {
    _inputText.value = text
}

fun onSystemPromptChange(text: String) {
    _systemPrompt.value = text
}

fun onTemperatureChange(temp: Double) {
    _temperature.value = temp
}

fun onProviderChange(prov: String) {
    _provider.value = prov
}

fun onModelChange(model: String) {
    _selectedModel.value = model
}

fun onMaxTokensChange(tokens: Int?) {
    _maxTokens.value = tokens
}

fun onUseRagChanged(enabled: Boolean) {
    _useRag.value = enabled
}
```

#### clearHistory()

```kotlin
suspend fun clearHistory() {
    _isLoading.value = true
    try {
        chatApi.clearHistory()
        _messages.value = emptyList()
        _tokenUsage.value = null
    } catch (e: Exception) {
        println("Error clearing history: ${e.message}")
    } finally {
        _isLoading.value = false
    }
}
```

### 4. ChatApi.kt

**Purpose**: HTTP client for server communication

**Configuration**:

```kotlin
class ChatApi {
    private val serverUrl = "http://localhost:${SERVER_PORT}"

    private val client = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}
```

**Methods**:

#### sendMessage()

```kotlin
suspend fun sendMessage(
    text: String,
    systemPrompt: String = "",
    temperature: Double = 0.7,
    provider: String = "gigachat",
    model: String? = null,
    maxTokens: Int? = null,
    useRag: Boolean = false
): ChatResponse {
    return try {
        val response = client.post("$serverUrl/api/send-message") {
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(
                text = text,
                systemPrompt = systemPrompt,
                temperature = temperature,
                provider = provider,
                model = model,
                maxTokens = maxTokens,
                enableTools = true,
                useRag = useRag
            ))
        }
        response.body()
    } catch (e: Exception) {
        println("Error sending message: $e")
        throw e
    }
}
```

#### clearHistory()

```kotlin
suspend fun clearHistory() {
    try {
        client.post("$serverUrl/api/clear-history")
    } catch (e: Exception) {
        println("Error clearing history: $e")
        throw e
    }
}
```

### 5. ChatScreen.kt

**Purpose**: Main UI composable with all chat components

**Component Hierarchy**:

```
ChatScreen (Column)
├── TopAppBar
│   ├── Title: "AI Chat"
│   └── Clear History Button
├── SystemPromptInput (Collapsible)
│   ├── System Prompt TextField
│   ├── Temperature Slider + Input
│   ├── Provider Dropdown (GigaChat/OpenRouter)
│   ├── Model Dropdown (OpenRouter only)
│   ├── Max Tokens Input (OpenRouter only)
│   └── Use RAG Checkbox
├── MessageList (LazyColumn)
│   └── MessageBubble (for each message)
│       ├── User messages (aligned right, blue)
│       └── Bot messages (aligned left, gray)
├── TokenUsageDisplay (OpenRouter only)
│   ├── Cumulative tokens
│   └── Last response tokens
└── MessageInput (Row)
    ├── TextField (auto-focus, enter to send)
    └── Send Button (with loading indicator)
```

**Key Composables**:

#### ChatScreen

```kotlin
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    inputText: String,
    isLoading: Boolean,
    systemPrompt: String,
    temperature: Double,
    provider: String,
    selectedModel: String?,
    maxTokens: Int?,
    useRag: Boolean,
    tokenUsage: TokenUsage?,
    onInputTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClearHistory: suspend () -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onProviderChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onMaxTokensChange: (Int?) -> Unit,
    onUseRagChanged: (Boolean) -> Unit
)
```

#### MessageBubble

```kotlin
@Composable
private fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == SenderType.USER

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser)
            Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 600.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
```

#### SystemPromptInput

```kotlin
@Composable
private fun SystemPromptInput(
    systemPrompt: String,
    temperature: Double,
    provider: String,
    selectedModel: String?,
    maxTokens: Int?,
    useRag: Boolean,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Double) -> Unit,
    onProviderChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onMaxTokensChange: (Int?) -> Unit,
    onUseRagChanged: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        // Expand/Collapse button
        TextButton(onClick = { expanded = !expanded }) {
            Text("Advanced Settings")
            Icon(
                imageVector = if (expanded)
                    Icons.Default.KeyboardArrowUp
                else
                    Icons.Default.KeyboardArrowDown
            )
        }

        if (expanded) {
            // System prompt
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                label = { Text("System Prompt") },
                modifier = Modifier.fillMaxWidth()
            )

            // Temperature slider + input
            TemperatureControl(
                temperature = temperature,
                onTemperatureChange = onTemperatureChange
            )

            // Provider dropdown
            ProviderSelector(
                provider = provider,
                onProviderChange = onProviderChange
            )

            // Model selection (OpenRouter only)
            if (provider == "openrouter") {
                ModelSelector(
                    selectedModel = selectedModel,
                    onModelChange = onModelChange
                )
            }

            // Use RAG checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useRag,
                    onCheckedChange = onUseRagChanged
                )
                Text("Use RAG (Retrieval-Augmented Generation)")
            }
        }
    }
}
```

#### TokenUsageDisplay

```kotlin
@Composable
private fun TokenUsageDisplay(
    tokenUsage: TokenUsage?,
    modifier: Modifier = Modifier
) {
    tokenUsage?.let { usage ->
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Token Usage (Cumulative)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text("Prompt: ${usage.promptTokens}")
                Text("Completion: ${usage.completionTokens}")
                Text("Total: ${usage.totalTokens}")
            }
        }
    }
}
```

### 6. Theme Configuration

#### Theme.kt

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF90CAF9),
            secondary = Color(0xFFCE93D8),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    } else {
        lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

#### Color.kt

```kotlin
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

## State Management

### StateFlow Pattern

The frontend uses Kotlin's StateFlow for reactive state management:

**Benefits**:

- Type-safe state
- Automatic UI updates on state changes
- Lifecycle-aware (with `collectAsState()`)
- No need for manual observer management

**Flow**:

```
User Action → ViewModel Method → StateFlow Update → UI Recomposition
```

**Example**:

```kotlin
// ViewModel
private val _inputText = MutableStateFlow("")
val inputText: StateFlow<String> = _inputText

fun onInputTextChange(text: String) {
    _inputText.value = text
}

// App.kt
val inputText by viewModel.inputText.collectAsState()

// ChatScreen.kt
TextField(
    value = inputText,
    onValueChange = onInputTextChange
)
```

## Features

### 1. Multi-Provider Support

Users can switch between AI providers:

- **GigaChat**: Russian AI provider (default)
- **OpenRouter**: Access to multiple models (Claude, GPT-4, Qwen, etc.)

**UI**: Dropdown selector in advanced settings

### 2. RAG Integration

Users can enable RAG for context-aware responses:

- **Checkbox**: "Use RAG" in advanced settings
- **Default**: OFF
- **Behavior**: Retrieves similar chunks from knowledge base and includes in prompt

### 3. Temperature Control

Users can adjust response randomness:

- **Range**: 0.0 (deterministic) to 2.0 (creative)
- **Default**: 0.7
- **UI**: Slider + numeric input

### 4. Token Usage Tracking

For OpenRouter, displays:

- **Cumulative**: Total tokens used in session
- **Last Response**: Tokens for most recent message
- **Breakdown**: Prompt tokens, completion tokens, total

### 5. Model Selection

OpenRouter users can select from multiple models:

- `anthropic/claude-3.5-sonnet`
- `openai/gpt-4-turbo`
- `qwen/qwen-2.5-72b-instruct`
- `google/gemini-pro-1.5`
- And more...

### 6. System Prompt Customization

Users can set custom system prompts:

- **Use Cases**: Role-play, formatting instructions, constraints
- **Example**: "You are a helpful coding assistant. Always provide code examples."

### 7. Auto-Scroll

Message list automatically scrolls to bottom on new messages:

```kotlin
LaunchedEffect(messages.size) {
    listState.animateScrollToItem(messages.size)
}
```

### 8. Enter to Send

TextField supports Enter key to send (Shift+Enter for new line):

```kotlin
onKeyEvent { event ->
    if (event.key == Key.Enter && !event.isShiftPressed) {
        onSendMessage()
        true
    } else false
}
```

## Running the Frontend

### Development Mode (Hot Reload)

```bash
# Wasm target (recommended - fastest)
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun

# JS target (fallback)
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

**Features**:

- Webpack dev server with hot reload
- Automatic browser refresh on code changes
- Source maps for debugging
- Opens browser automatically at http://localhost:8080

### Production Build

```bash
# Wasm
.\gradlew.bat :composeApp:wasmJsBrowserDistribution

# JS
.\gradlew.bat :composeApp:jsBrowserDistribution
```

**Output**: `composeApp/build/dist/wasmJs/productionExecutable/`

**Files**:

- `index.html` - Entry point
- `*.js` - Compiled Kotlin code
- `*.wasm` - WebAssembly binary (Wasm only)
- Assets (images, fonts, etc.)

## Deployment

### Static Hosting

The built frontend is static files that can be deployed to:

**1. AWS S3 + CloudFront**

```bash
aws s3 sync composeApp/build/dist/wasmJs/productionExecutable/ s3://your-bucket/
aws cloudfront create-invalidation --distribution-id XXX --paths "/*"
```

**2. Netlify**

```bash
netlify deploy --prod --dir=composeApp/build/dist/wasmJs/productionExecutable
```

**3. Vercel**

```bash
vercel --prod composeApp/build/dist/wasmJs/productionExecutable
```

**4. GitHub Pages**

```bash
# Push to gh-pages branch
git subtree push --prefix composeApp/build/dist/wasmJs/productionExecutable origin gh-pages
```

**5. Nginx**

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /var/www/aichallenge;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### API Configuration

For production, update server URL in `Constants.kt`:

```kotlin
// Development
const val SERVER_PORT = 8080
const val SERVER_URL = "http://localhost:$SERVER_PORT"

// Production
const val SERVER_URL = "https://api.your-domain.com"
```

Or use environment-based configuration:

```kotlin
const val SERVER_URL = js("process.env.API_URL || 'http://localhost:8080'")
```

## Performance Considerations

### Bundle Size

**Wasm**:

- Initial load: ~2-3 MB (gzipped)
- Subsequent loads: Cached

**JS**:

- Initial load: ~1.5-2 MB (gzipped)

**Optimization**:

- Code splitting (future enhancement)
- Tree shaking (enabled by default)
- Minification in production

### Rendering Performance

**Compose Multiplatform**:

- Efficient recomposition (only changed parts re-render)
- Virtual DOM-like diffing
- Hardware-accelerated rendering (Skia)

**Best Practices**:

- Use `remember {}` for expensive calculations
- Use `derivedStateOf {}` for computed state
- Avoid creating new lambdas in composables (use `remember { {} }`)

## Browser Compatibility

### Wasm Target

**Requirements**:

- Modern browsers with WebAssembly GC support
- Chrome 119+
- Firefox 120+
- Safari 17.4+
- Edge 119+

**Not Supported**:

- IE 11
- Older mobile browsers

### JS Target (Fallback)

**Requirements**:

- Any modern browser with ES6 support
- Chrome 51+
- Firefox 54+
- Safari 10+
- Edge 15+

## Development Tips

### 1. Hot Reload

Changes to `.kt` files trigger automatic recompilation and browser refresh.

**Note**: Changes to `index.html` require manual refresh.

### 2. Debugging

**Browser DevTools**:

- Source maps available in development mode
- Can set breakpoints in Kotlin code
- Console.log equivalent: `println()`

**Example**:

```kotlin
println("Message sent: ${inputText.value}")
```

### 3. State Inspection

Use `println()` to inspect StateFlow values:

```kotlin
LaunchedEffect(messages.size) {
    println("Messages updated: ${messages.size} total")
}
```

### 4. Error Handling

Wrap API calls in try-catch:

```kotlin
try {
    val response = chatApi.sendMessage(...)
} catch (e: Exception) {
    println("Error: ${e.message}")
    // Show error message to user
}
```

## Testing

### Manual Testing Checklist

- [ ] Send message with GigaChat
- [ ] Send message with OpenRouter
- [ ] Change temperature and verify different responses
- [ ] Enable RAG and verify context in response
- [ ] Select different models (OpenRouter)
- [ ] Set custom system prompt
- [ ] Clear history
- [ ] Verify token usage display (OpenRouter)
- [ ] Test on different browsers
- [ ] Test mobile responsiveness

### Unit Tests (Future)

```bash
.\gradlew.bat :composeApp:test
```

## Dependencies

```kotlin
dependencies {
    implementation(project(":shared"))

    // Compose Multiplatform
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.js)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
}
```

## Troubleshooting

### Issue: White Screen on Load

**Causes**:

- Server not running
- CORS error
- JavaScript error

**Solutions**:

1. Check browser console for errors
2. Verify server is running: `curl http://localhost:8080/`
3. Check CORS configuration in server
4. Clear browser cache

### Issue: Messages Not Sending

**Symptoms**: Click Send, nothing happens

**Solutions**:

1. Check browser console for API errors
2. Verify server API is accessible
3. Check network tab for failed requests
4. Ensure input is not blank

### Issue: Hot Reload Not Working

**Solution**: Stop and restart dev server

```bash
# Stop with Ctrl+C
# Restart
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

### Issue: Wasm Not Loading

**Symptoms**: "WebAssembly is not supported" error

**Solution**: Use JS target instead

```bash
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

## Future Enhancements

1. **Markdown Rendering**: Parse and render markdown in bot messages
2. **Code Syntax Highlighting**: Highlight code blocks
3. **Message Actions**: Copy, edit, regenerate buttons
4. **Conversation Export**: Save conversation to file
5. **Voice Input**: Speech-to-text integration
6. **Image Upload**: Send images to multimodal models
7. **Streaming Responses**: Show AI response as it's generated (SSE/WebSocket)
8. **Conversation Threads**: Save and resume multiple conversations
9. **User Preferences**: Save settings to localStorage
10. **Mobile App**: Android/iOS targets with Compose Multiplatform

## Related Documentation

- [Server Module](server.md) - Backend API
- [Shared Module](shared.md) - Data models
- [Architecture Overview](../ARCHITECTURE.md) - System design
- [Getting Started](../GETTING-STARTED.md) - Setup guide

## References

- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kotlin/JS](https://kotlinlang.org/docs/js-overview.html)
- [Kotlin/Wasm](https://kotlinlang.org/docs/wasm-overview.html)
- [Ktor Client](https://ktor.io/docs/client.html)
- [Material 3 Design](https://m3.material.io/)
