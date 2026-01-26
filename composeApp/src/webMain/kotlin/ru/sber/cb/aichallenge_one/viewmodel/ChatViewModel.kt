package ru.sber.cb.aichallenge_one.viewmodel

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.sber.cb.aichallenge_one.api.ChatApi
import ru.sber.cb.aichallenge_one.models.*

class ChatViewModel : ViewModel() {
    private val chatApi = ChatApi()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _temperature = MutableStateFlow(0.7)
    val temperature: StateFlow<Double> = _temperature.asStateFlow()

    private val _provider = MutableStateFlow("gigachat")
    val provider: StateFlow<String> = _provider.asStateFlow()

    private val _useRag = MutableStateFlow(false)
    val useRag: StateFlow<Boolean> = _useRag.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _tokenUsage = MutableStateFlow(TokenUsage())
    val tokenUsage: StateFlow<TokenUsage> = _tokenUsage.asStateFlow()

    private val _lastResponseTokenUsage = MutableStateFlow<TokenUsage?>(null)
    val lastResponseTokenUsage: StateFlow<TokenUsage?> = _lastResponseTokenUsage.asStateFlow()

    private val _responseTimeMs = MutableStateFlow<Long?>(null)
    val responseTimeMs: StateFlow<Long?> = _responseTimeMs.asStateFlow()

    private val _maxTokens = MutableStateFlow<Int?>(null)
    val maxTokens: StateFlow<Int?> = _maxTokens.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    val snackbarHostState = SnackbarHostState()

    private val _lastShownNotificationId = MutableStateFlow<String?>(null)

    init {
        // Fetch models and history on initialization
        fetchModels()
        loadHistory()
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun onSystemPromptChanged(text: String) {
        _systemPrompt.value = text
    }

    fun onTemperatureChanged(value: Double) {
        _temperature.value = value.coerceIn(0.0, 2.0)
    }

    fun onMaxTokensChanged(value: Int?) {
        _maxTokens.value = value
    }

    fun onProviderChanged(provider: String) {
        _provider.value = provider
        if (provider == "gigachat") {
            _selectedModel.value = null
            _availableModels.value = emptyList()
        } else if (provider == "openrouter" || provider == "ollama") {
            // Fetch models for OpenRouter or Ollama
            fetchModels()
        }
        // Load history for the new provider
        loadHistory()
    }

    fun onModelChanged(modelId: String) {
        // Clear chat and reset token usage when model changes
        if (_selectedModel.value != null && _selectedModel.value != modelId) {
            viewModelScope.launch {
                try {
                    chatApi.clearHistory()
                    _messages.value = emptyList()
                    _tokenUsage.value = TokenUsage()
                    _lastResponseTokenUsage.value = null
                    _responseTimeMs.value = null
                } catch (e: Exception) {
                    println("Failed to clear history on model change: $e")
                }
            }
        }
        _selectedModel.value = modelId
    }

    fun onUseRagChanged(enabled: Boolean) {
        _useRag.value = enabled
    }

    private fun fetchModels() {
        _isLoadingModels.value = true
        viewModelScope.launch {
            try {
                val provider = _provider.value
                val models = if (provider == "ollama") {
                    chatApi.fetchAvailableModels("ollama")
                } else if (provider == "openrouter") {
                    chatApi.fetchAvailableModels("openrouter")
                } else {
                    emptyList()
                }
                _availableModels.value = models
                // Set default model if OpenRouter or Ollama is selected and no model is chosen
                if ((provider == "openrouter" || provider == "ollama") && _selectedModel.value == null && models.isNotEmpty()) {
                    _selectedModel.value = models.first().id
                }
            } catch (e: Exception) {
                println("Failed to fetch models: $e")
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    private fun loadHistory() {
        _isLoadingHistory.value = true
        viewModelScope.launch {
            try {
                val history = chatApi.fetchHistory(_provider.value)
                _messages.value = history
            } catch (e: Exception) {
                println("Failed to load history: $e")
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank() || _isLoading.value) return

        // Parse /help command: syntax is "/help <question>"
        val isHelpCommand = text.startsWith("/help ")
        val actualTextFromHelp = if (isHelpCommand) {
            // Extract the question after "/help "
            text.substring(6).trim()
        } else {
            text
        }

        // Parse /analyse command: syntax is "/analyse <question>"
        val isAnalyseCommand = text.startsWith("/analyse ")
        val actualTextFromAnalyse = if (isAnalyseCommand) {
            text.substring(9).trim()
        } else {
            text
        }

        // Parse /about command: syntax is "/about <question>"
        val isAboutCommand = text.startsWith("/about ")
        val actualText = when {
            isHelpCommand -> actualTextFromHelp
            isAnalyseCommand -> actualTextFromAnalyse
            isAboutCommand -> text.substring(7).trim()  // "/about " = 7 chars
            else -> text
        }

        // Show original message in chat (including /help prefix if present)
        val userMessage = ChatMessage(text, SenderType.USER)
        _messages.value = _messages.value + userMessage
        _inputText.value = ""
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = chatApi.sendMessage(
                    text = actualText,
                    systemPrompt = _systemPrompt.value,
                    temperature = _temperature.value,
                    provider = _provider.value,
                    model = _selectedModel.value,
                    maxTokens = _maxTokens.value,
                    useRag = _useRag.value,
                    isHelpCommand = isHelpCommand,
                    isSupportCommand = false,
                    isAnalyseCommand = isAnalyseCommand,
                    isAboutCommand = isAboutCommand
                )

                val botMessage = if (response.status == ResponseStatus.SUCCESS) {
                    ChatMessage(response.text, SenderType.BOT)
                } else {
                    ChatMessage(response.text, SenderType.BOT)
                }

                _messages.value = _messages.value + botMessage

                // Update token usage if available
                response.tokenUsage?.let { usage ->
                    _tokenUsage.value = usage
                }

                // Update last response token usage
                _lastResponseTokenUsage.value = response.lastResponseTokenUsage

                // Update response time
                _responseTimeMs.value = response.responseTimeMs
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    "Не удалось отправить сообщение. Пожалуйста, попробуйте позже.",
                    SenderType.BOT
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            try {
                chatApi.clearHistory()
                _messages.value = emptyList()
                _tokenUsage.value = TokenUsage()
                _lastResponseTokenUsage.value = null
                _responseTimeMs.value = null
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    "Не удалось очистить историю чата.",
                    SenderType.BOT
                )
                _messages.value = _messages.value + errorMessage
            }
        }
    }

    suspend fun pollNotifications() {
        while (true) {
            try {
                val response = chatApi.fetchNotifications()

                // Show only the newest unread notification
                val newestNotification = response.notifications.firstOrNull()

                if (newestNotification != null &&
                    newestNotification.id != _lastShownNotificationId.value
                ) {

                    // Show snackbar
                    snackbarHostState.showSnackbar(
                        message = "Summary: ${newestNotification.text}",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )

                    // Mark as read
                    chatApi.markNotificationAsRead(newestNotification.id)

                    // Update last shown ID
                    _lastShownNotificationId.value = newestNotification.id
                }

            } catch (e: Exception) {
                println("Error polling notifications: $e")
            }

            // Wait 30 seconds before next poll
            delay(30_000L)
        }
    }
}
