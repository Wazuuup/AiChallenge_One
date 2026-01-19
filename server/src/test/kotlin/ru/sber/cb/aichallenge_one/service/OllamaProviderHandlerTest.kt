package ru.sber.cb.aichallenge_one.service

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.sber.cb.aichallenge_one.client.*
import ru.sber.cb.aichallenge_one.database.MessageEntity
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.domain.AiProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Comprehensive unit tests for OllamaProviderHandler.
 *
 * Tests cover:
 * - processMessage() with and without summarization
 * - processMessageWithMetadata() with model override
 * - clearHistory() from memory and database
 * - loadHistory() from database into memory
 * - History size and message count tracking
 * - Provider identification
 */
class OllamaProviderHandlerTest {

    private lateinit var mockOllamaClientAdapter: OllamaClientAdapter
    private lateinit var mockSummarizationService: SummarizationService
    private lateinit var mockMessageRepository: MessageRepository
    private lateinit var ollamaProviderHandler: OllamaProviderHandler

    @BeforeEach
    fun setup() {
        mockOllamaClientAdapter = mockk(relaxed = true)
        mockSummarizationService = mockk(relaxed = true)
        mockMessageRepository = mockk(relaxed = true)

        // Create handler with summarization disabled by default
        ollamaProviderHandler = OllamaProviderHandler(
            ollamaClientAdapter = mockOllamaClientAdapter,
            summarizationService = mockSummarizationService,
            messageRepository = mockMessageRepository,
            enableSummarization = false,
            mcpClientServiceList = null,
            toolAdapterService = null,
            toolExecutionService = null,
            enableTools = false
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test processMessage with successful response returns correct result`() = runTest {
        // Arrange
        val userText = "Hello, Ollama!"
        val systemPrompt = "You are a helpful assistant."
        val temperature = 0.7
        val model = "gemma3:1b"
        val responseText = "Hello! How can I help you today?"
        val responseTimeMs = 1500L

        val mockResult = OllamaMessageResult(
            text = responseText,
            usage = OllamaUsage(
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30
            ),
            responseTimeMs = responseTimeMs
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", userText) },
                systemPrompt = systemPrompt,
                temperature = temperature,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = userText
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = responseText
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns model

        // Act
        val result = ollamaProviderHandler.processMessage(
            userText = userText,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Assert
        assertNotNull(result)
        assertEquals(responseText, result.text)
        assertEquals(10, result.usage?.promptTokens)
        assertEquals(20, result.usage?.completionTokens)
        assertEquals(30, result.usage?.totalTokens)
        assertEquals(responseTimeMs, result.responseTimeMs)
        assertEquals(model, result.model)
        assertEquals(2, ollamaProviderHandler.getHistorySize()) // user + assistant
    }

    @Test
    fun `test processMessage without summarization does not call summarizationService`() = runTest {
        // Arrange
        val userText = "Test message"
        val systemPrompt = ""
        val temperature = 0.7

        val mockResult = OllamaMessageResult(
            text = "Response",
            usage = null,
            responseTimeMs = 1000
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", userText) },
                systemPrompt = systemPrompt,
                temperature = temperature,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = userText
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Response"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns "gemma3:1b"

        // Act
        ollamaProviderHandler.processMessage(
            userText = userText,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Assert - verify service was not called (summarization is disabled)
        coVerify(exactly = 0) {
            mockSummarizationService.shouldSummarize(any())
        }
    }

    @Test
    fun `test processMessageWithMetadata returns correct result with model override`() = runTest {
        // Arrange
        val userText = "Test with custom model"
        val systemPrompt = "You are creative."
        val temperature = 0.8
        val modelOverride = "llama2:7b"
        val responseText = "Response from custom model"

        val mockResult = OllamaMessageResult(
            text = responseText,
            usage = OllamaUsage(
                promptTokens = 15,
                completionTokens = 25,
                totalTokens = 40
            ),
            responseTimeMs = 1800
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", userText) },
                systemPrompt = systemPrompt,
                temperature = temperature,
                modelOverride = eq(modelOverride)
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = userText
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = responseText
            )
        } just Runs

        // Act
        val result = ollamaProviderHandler.processMessageWithMetadata(
            userText = userText,
            systemPrompt = systemPrompt,
            temperature = temperature,
            modelOverride = modelOverride
        )

        // Assert
        assertNotNull(result)
        assertEquals(responseText, result.text)
        assertEquals(modelOverride, result.model)
    }

    @Test
    fun `test processMessageWithMetadata without modelOverride uses default model`() = runTest {
        // Arrange
        val userText = "Test with default model"
        val systemPrompt = ""
        val temperature = 0.7
        val defaultModel = "gemma3:1b"

        val mockResult = OllamaMessageResult(
            text = "Response",
            usage = null,
            responseTimeMs = 1000
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", userText) },
                systemPrompt = systemPrompt,
                temperature = temperature,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = userText
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Response"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns defaultModel

        // Act
        val result = ollamaProviderHandler.processMessageWithMetadata(
            userText = userText,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Assert
        assertEquals(defaultModel, result.model)
    }

    @Test
    fun `test clearHistory removes messages from memory and database`() = runTest {
        // Arrange - Add some messages to history
        val mockResult = OllamaMessageResult(
            text = "Initial message",
            usage = null,
            responseTimeMs = 500
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", "Test") },
                systemPrompt = "",
                temperature = 0.7,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = "Test"
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Initial message"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns "gemma3:1b"

        ollamaProviderHandler.processMessage(
            userText = "Test",
            systemPrompt = "",
            temperature = 0.7
        )

        assertEquals(2, ollamaProviderHandler.getHistorySize())

        coEvery {
            mockMessageRepository.clearHistory(provider = "ollama")
        } just Runs

        // Act
        ollamaProviderHandler.clearHistory()

        // Assert
        assertEquals(0, ollamaProviderHandler.getHistorySize())
        coVerify(exactly = 1) {
            mockMessageRepository.clearHistory(provider = "ollama")
        }
    }

    @Test
    fun `test clearHistory with database error clears memory only`() = runTest {
        // Arrange
        val mockResult = OllamaMessageResult(
            text = "Initial message",
            usage = null,
            responseTimeMs = 500
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", "Test") },
                systemPrompt = "",
                temperature = 0.7,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = "Test"
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Initial message"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns "gemma3:1b"

        ollamaProviderHandler.processMessage(
            userText = "Test",
            systemPrompt = "",
            temperature = 0.7
        )

        // Database throws exception
        coEvery {
            mockMessageRepository.clearHistory(provider = "ollama")
        } throws Exception("Database connection failed")

        // Act - should not throw exception
        ollamaProviderHandler.clearHistory()

        // Assert - memory should still be cleared
        assertEquals(0, ollamaProviderHandler.getHistorySize())
    }

    @Test
    fun `test loadHistory loads messages from database into memory`() = runTest {
        // Arrange
        val mockMessages = listOf(
            MessageEntity(role = "user", content = "User message", isSummary = false),
            MessageEntity(role = "assistant", content = "Assistant response", isSummary = false),
            MessageEntity(role = "system", content = "System summary", isSummary = true)
        )

        coEvery {
            mockMessageRepository.getHistory(provider = "ollama")
        } returns mockMessages

        // Act
        ollamaProviderHandler.loadHistory()

        // Assert
        assertEquals(3, ollamaProviderHandler.getHistorySize())
        assertEquals(3, ollamaProviderHandler.getMessageCount())

        coVerify(exactly = 1) {
            mockMessageRepository.getHistory(provider = "ollama")
        }
    }

    @Test
    fun `test loadHistory with empty database sets history to empty`() = runTest {
        // Arrange
        coEvery {
            mockMessageRepository.getHistory(provider = "ollama")
        } returns emptyList()

        // Add a message first
        val mockResult = OllamaMessageResult(
            text = "Test",
            usage = null,
            responseTimeMs = 500
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", "Test") },
                systemPrompt = "",
                temperature = 0.7,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = "Test"
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Test"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns "gemma3:1b"

        ollamaProviderHandler.processMessage(
            userText = "Test",
            systemPrompt = "",
            temperature = 0.7
        )

        assertEquals(2, ollamaProviderHandler.getHistorySize())

        // Act
        ollamaProviderHandler.loadHistory()

        // Assert
        assertEquals(0, ollamaProviderHandler.getHistorySize())
        assertEquals(0, ollamaProviderHandler.getMessageCount())
    }

    @Test
    fun `test loadHistory with database error handles gracefully`() = runTest {
        // Arrange
        coEvery {
            mockMessageRepository.getHistory(provider = "ollama")
        } throws Exception("Database connection failed")

        // Act - should not throw exception
        ollamaProviderHandler.loadHistory()

        // Assert - history should remain in default state
        assertEquals(0, ollamaProviderHandler.getHistorySize())
    }

    @Test
    fun `test getProvider returns OLLAMA enum value`() {
        // Act
        val provider = ollamaProviderHandler.getProvider()

        // Assert
        assertEquals(AiProvider.OLLAMA, provider)
    }

    @Test
    fun `test getHistorySize returns correct count`() = runTest {
        // Arrange - Initially empty
        assertEquals(0, ollamaProviderHandler.getHistorySize())

        // Add messages
        val mockResult = OllamaMessageResult(
            text = "Response",
            usage = null,
            responseTimeMs = 500
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", "Message 1") },
                systemPrompt = "",
                temperature = 0.7,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = "Message 1"
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Response"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns "gemma3:1b"

        // Act - Add first message
        ollamaProviderHandler.processMessage(
            userText = "Message 1",
            systemPrompt = "",
            temperature = 0.7
        )

        // Assert
        assertEquals(2, ollamaProviderHandler.getHistorySize()) // user + assistant
    }

    @Test
    fun `test getMessageCount returns correct count`() = runTest {
        // Arrange - Initially zero
        assertEquals(0, ollamaProviderHandler.getMessageCount())

        // Add messages
        val mockResult = OllamaMessageResult(
            text = "Response",
            usage = null,
            responseTimeMs = 500
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", "Message 1") },
                systemPrompt = "",
                temperature = 0.7,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = "Message 1"
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Response"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns "gemma3:1b"

        // Act - Add first message
        ollamaProviderHandler.processMessage(
            userText = "Message 1",
            systemPrompt = "",
            temperature = 0.7
        )

        // Assert
        assertEquals(2, ollamaProviderHandler.getMessageCount()) // user + assistant
    }

    @Test
    fun `test processMessageWithMetadata with null usage handles gracefully`() = runTest {
        // Arrange
        val userText = "Test message"
        val systemPrompt = ""
        val temperature = 0.7

        val mockResult = OllamaMessageResult(
            text = "Response without usage",
            usage = null, // Ollama may not always return usage
            responseTimeMs = 1000
        )

        coEvery {
            mockOllamaClientAdapter.sendMessageWithMetadata(
                messageHistory = List<OllamaMessage>(1) { OllamaMessage("user", userText) },
                systemPrompt = systemPrompt,
                temperature = temperature,
                modelOverride = isNull()
            )
        } returns mockResult

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.USER.value,
                content = userText
            )
        } just Runs

        coEvery {
            mockMessageRepository.saveMessage(
                provider = "ollama",
                role = OllamaMessageRole.ASSISTANT.value,
                content = "Response without usage"
            )
        } just Runs

        every { mockOllamaClientAdapter.getModel() } returns "gemma3:1b"

        // Act
        val result = ollamaProviderHandler.processMessageWithMetadata(
            userText = userText,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Assert
        assertNotNull(result)
        assertEquals("Response without usage", result.text)
        assertNull(result.usage)
    }

    @Test
    fun `test OllamaHandlerResult data structure is valid`() {
        // Arrange
        val text = "Test response"
        val usage = OllamaUsage(
            promptTokens = 10,
            completionTokens = 20,
            totalTokens = 30
        )
        val responseTimeMs = 1000L
        val model = "gemma3:1b"

        // Act
        val result = OllamaHandlerResult(
            text = text,
            usage = usage,
            responseTimeMs = responseTimeMs,
            model = model
        )

        // Assert
        assertEquals(text, result.text)
        assertEquals(usage, result.usage)
        assertEquals(responseTimeMs, result.responseTimeMs)
        assertEquals(model, result.model)
    }

    @Test
    fun `test OllamaHandlerResult with nullable usage`() {
        // Arrange
        val text = "Test response"
        val responseTimeMs = 1000L
        val model = "gemma3:1b"

        // Act
        val result = OllamaHandlerResult(
            text = text,
            usage = null,
            responseTimeMs = responseTimeMs,
            model = model
        )

        // Assert
        assertEquals(text, result.text)
        assertNull(result.usage)
        assertEquals(responseTimeMs, result.responseTimeMs)
        assertEquals(model, result.model)
    }
}
