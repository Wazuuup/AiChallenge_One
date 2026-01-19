package ru.sber.cb.aichallenge_one.client

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.sber.cb.aichallenge_one.domain.AiProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Comprehensive unit tests for OllamaClientAdapter.
 *
 * Tests cover:
 * - createMessage() with different role types
 * - getProvider() returns OLLAMA enum value
 * - getLocalizedRoleName() mappings for all role types
 * - getModel() returns the configured model name
 * - Data structure validation
 */
class OllamaClientAdapterTest {

    private lateinit var mockOllamaApiClient: OllamaApiClient
    private lateinit var ollamaClientAdapter: OllamaClientAdapter

    @BeforeEach
    fun setup() {
        mockOllamaApiClient = mockk(relaxed = true)
        ollamaClientAdapter = OllamaClientAdapter(mockOllamaApiClient)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test createMessage with user role returns correct OllamaMessage`() {
        // Arrange
        val role = OllamaMessageRole.USER.value
        val content = "User message content"

        // Act
        val result = ollamaClientAdapter.createMessage(role, content)

        // Assert
        assertNotNull(result)
        assertEquals(role, result.role)
        assertEquals(content, result.content)
        assertNotNull(result.timestamp)
    }

    @Test
    fun `test createMessage with assistant role returns correct OllamaMessage`() {
        // Arrange
        val role = OllamaMessageRole.ASSISTANT.value
        val content = "Assistant response"

        // Act
        val result = ollamaClientAdapter.createMessage(role, content)

        // Assert
        assertNotNull(result)
        assertEquals(role, result.role)
        assertEquals(content, result.content)
        assertNotNull(result.timestamp)
    }

    @Test
    fun `test createMessage with system role returns correct OllamaMessage`() {
        // Arrange
        val role = OllamaMessageRole.SYSTEM.value
        val content = "System prompt instructions"

        // Act
        val result = ollamaClientAdapter.createMessage(role, content)

        // Assert
        assertNotNull(result)
        assertEquals(role, result.role)
        assertEquals(content, result.content)
        assertNotNull(result.timestamp)
    }

    @Test
    fun `test createMessage with custom role returns correct OllamaMessage`() {
        // Arrange
        val customRole = "custom"
        val content = "Custom role message"

        // Act
        val result = ollamaClientAdapter.createMessage(customRole, content)

        // Assert
        assertNotNull(result)
        assertEquals(customRole, result.role)
        assertEquals(content, result.content)
        assertNotNull(result.timestamp)
    }

    @Test
    fun `test createMessage with empty content handles correctly`() {
        // Arrange
        val role = OllamaMessageRole.USER.value
        val content = ""

        // Act
        val result = ollamaClientAdapter.createMessage(role, content)

        // Assert
        assertNotNull(result)
        assertEquals(role, result.role)
        assertEquals(content, result.content)
    }

    @Test
    fun `test getProvider returns OLLAMA enum value`() {
        // Act
        val provider = ollamaClientAdapter.getProvider()

        // Assert
        assertEquals(AiProvider.OLLAMA, provider)
    }

    @Test
    fun `test getLocalizedRoleName for user role returns English name`() {
        // Act
        val localizedName = ollamaClientAdapter.getLocalizedRoleName(OllamaMessageRole.USER.value)

        // Assert
        assertEquals("User", localizedName)
    }

    @Test
    fun `test getLocalizedRoleName for assistant role returns English name`() {
        // Act
        val localizedName = ollamaClientAdapter.getLocalizedRoleName(OllamaMessageRole.ASSISTANT.value)

        // Assert
        assertEquals("Assistant", localizedName)
    }

    @Test
    fun `test getLocalizedRoleName for system role returns English name`() {
        // Act
        val localizedName = ollamaClientAdapter.getLocalizedRoleName(OllamaMessageRole.SYSTEM.value)

        // Assert
        assertEquals("System", localizedName)
    }

    @Test
    fun `test getLocalizedRoleName for unknown role returns original value`() {
        // Arrange
        val unknownRole = "unknown_role"

        // Act
        val localizedName = ollamaClientAdapter.getLocalizedRoleName(unknownRole)

        // Assert
        assertEquals(unknownRole, localizedName)
    }

    @Test
    fun `test getLocalizedRoleName for custom role returns original value`() {
        // Arrange
        val customRole = "moderator"

        // Act
        val localizedName = ollamaClientAdapter.getLocalizedRoleName(customRole)

        // Assert
        assertEquals(customRole, localizedName)
    }

    @Test
    fun `test getOllamaApiClient returns underlying client instance`() {
        // Act
        val returnedClient = ollamaClientAdapter.getOllamaApiClient()

        // Assert
        assertEquals(mockOllamaApiClient, returnedClient)
    }

    @Test
    fun `test getModel returns model from OllamaApiClient`() {
        // Arrange
        val expectedModel = "gemma3:1b"
        every { mockOllamaApiClient.getModel() } returns expectedModel

        // Act
        val model = ollamaClientAdapter.getModel()

        // Assert
        assertEquals(expectedModel, model)
        verify(exactly = 1) { mockOllamaApiClient.getModel() }
    }

    @Test
    fun `test sendMessage delegates to OllamaApiClient`() = runTest {
        // Arrange
        val messageHistory = listOf(
            OllamaMessage(role = "user", content = "Hello, Ollama!"),
            OllamaMessage(role = "assistant", content = "Previous response")
        )
        val systemPrompt = "You are a helpful assistant."
        val temperature = 0.7
        val expectedResponse = "This is the AI response"

        val mockResult = OllamaMessageResult(
            text = expectedResponse,
            usage = OllamaUsage(
                promptTokens = 15,
                completionTokens = 25,
                totalTokens = 40
            ),
            responseTimeMs = 1500
        )

        coEvery {
            mockOllamaApiClient.sendMessage(
                messageHistory = messageHistory,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = null,
                modelOverride = null
            )
        } returns mockResult

        // Act
        val result = ollamaClientAdapter.sendMessage(
            messageHistory = messageHistory,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Assert
        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) {
            mockOllamaApiClient.sendMessage(
                messageHistory = messageHistory,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = null,
                modelOverride = null
            )
        }
    }

    @Test
    fun `test sendMessageWithMetadata returns full OllamaMessageResult`() = runTest {
        // Arrange
        val messageHistory = listOf(
            OllamaMessage(role = "user", content = "Test message")
        )
        val systemPrompt = "Test system prompt"
        val temperature = 0.5
        val modelOverride = "llama2:7b"

        val expectedUsage = OllamaUsage(
            promptTokens = 10,
            completionTokens = 20,
            totalTokens = 30
        )
        val expectedResponseTime = 1200L

        val mockResult = OllamaMessageResult(
            text = "Full response with metadata",
            usage = expectedUsage,
            responseTimeMs = expectedResponseTime
        )

        coEvery {
            mockOllamaApiClient.sendMessage(
                messageHistory = messageHistory,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = null,
                modelOverride = modelOverride
            )
        } returns mockResult

        // Act
        val result = ollamaClientAdapter.sendMessageWithMetadata(
            messageHistory = messageHistory,
            systemPrompt = systemPrompt,
            temperature = temperature,
            modelOverride = modelOverride
        )

        // Assert
        assertNotNull(result)
        assertEquals("Full response with metadata", result.text)
        assertEquals(expectedUsage, result.usage)
        assertEquals(expectedResponseTime, result.responseTimeMs)
    }

    @Test
    fun `test sendMessageWithMetadata without modelOverride uses default`() = runTest {
        // Arrange
        val messageHistory = listOf(
            OllamaMessage(role = "user", content = "Test")
        )
        val systemPrompt = ""
        val temperature = 0.7

        val mockResult = OllamaMessageResult(
            text = "Response with default model",
            usage = null,
            responseTimeMs = 1000
        )

        coEvery {
            mockOllamaApiClient.sendMessage(
                messageHistory = messageHistory,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = null,
                modelOverride = null
            )
        } returns mockResult

        // Act
        val result = ollamaClientAdapter.sendMessageWithMetadata(
            messageHistory = messageHistory,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Assert
        assertNotNull(result)
        assertEquals("Response with default model", result.text)

        coVerify(exactly = 1) {
            mockOllamaApiClient.sendMessage(
                messageHistory = messageHistory,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = null,
                modelOverride = null
            )
        }
    }

    @Test
    fun `test sendMessage with empty history delegates correctly`() = runTest {
        // Arrange
        val emptyHistory = emptyList<OllamaMessage>()
        val systemPrompt = ""
        val temperature = 1.0
        val expectedResponse = "Response with no history"

        val mockResult = OllamaMessageResult(
            text = expectedResponse,
            usage = null,
            responseTimeMs = 800
        )

        coEvery {
            mockOllamaApiClient.sendMessage(
                messageHistory = emptyHistory,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = null,
                modelOverride = null
            )
        } returns mockResult

        // Act
        val result = ollamaClientAdapter.sendMessage(
            messageHistory = emptyHistory,
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        // Assert
        assertEquals(expectedResponse, result)
    }

    @Test
    fun `test sendMessage with high temperature delegates correctly`() = runTest {
        // Arrange
        val messageHistory = listOf(
            OllamaMessage(role = "user", content = "Creative response please")
        )
        val temperature = 1.5
        val expectedResponse = "Creative and varied response"

        val mockResult = OllamaMessageResult(
            text = expectedResponse,
            usage = null,
            responseTimeMs = 2000
        )

        coEvery {
            mockOllamaApiClient.sendMessage(
                messageHistory = messageHistory,
                systemPrompt = "",
                temperature = temperature,
                maxTokens = null,
                modelOverride = null
            )
        } returns mockResult

        // Act
        val result = ollamaClientAdapter.sendMessage(
            messageHistory = messageHistory,
            systemPrompt = "",
            temperature = temperature
        )

        // Assert
        assertEquals(expectedResponse, result)
    }
}
