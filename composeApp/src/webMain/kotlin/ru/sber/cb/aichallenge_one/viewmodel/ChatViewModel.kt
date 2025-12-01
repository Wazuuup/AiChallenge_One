package ru.sber.cb.aichallenge_one.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.sber.cb.aichallenge_one.api.ChatApi
import ru.sber.cb.aichallenge_one.models.ChatMessage
import ru.sber.cb.aichallenge_one.models.ResponseStatus
import ru.sber.cb.aichallenge_one.models.SenderType

class ChatViewModel : ViewModel() {
    private val chatApi = ChatApi()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun onInputChanged(text: String) {
        _inputText.value = text
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
                val response = chatApi.sendMessage(text)

                val botMessage = if (response.status == ResponseStatus.SUCCESS) {
                    ChatMessage(response.text, SenderType.BOT)
                } else {
                    ChatMessage(response.text, SenderType.BOT)
                }

                _messages.value = _messages.value + botMessage
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
}
