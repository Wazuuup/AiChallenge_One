package ru.sber.cb.aichallenge_one.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        // Fetch models on initialization
        fetchModels()
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
        } else if (provider == "openrouter" && _availableModels.value.isNotEmpty() && _selectedModel.value == null) {
            // Set first model as default when switching to OpenRouter
            _selectedModel.value = _availableModels.value.firstOrNull()?.id
        }
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

    private fun fetchModels() {
        _isLoadingModels.value = true
        viewModelScope.launch {
            try {
                val models = chatApi.fetchAvailableModels()
                _availableModels.value = models
                // Set default model if OpenRouter is selected and no model is chosen
                if (_provider.value == "openrouter" && _selectedModel.value == null && models.isNotEmpty()) {
                    _selectedModel.value = models.first().id
                }
            } catch (e: Exception) {
                println("Failed to fetch models: $e")
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank() || _isLoading.value) return

        val userMessage = ChatMessage(text, SenderType.USER)
        _messages.value = _messages.value + userMessage
        _inputText.value = ""
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = chatApi.sendMessage(
                    text = text,
                    systemPrompt = _systemPrompt.value,
                    temperature = _temperature.value,
                    provider = _provider.value,
                    model = _selectedModel.value,
                    maxTokens = _maxTokens.value
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
}
