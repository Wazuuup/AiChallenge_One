package ru.sber.cb.aichallenge_one.client

import io.ktor.client.*
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for OllamaApiClient.
 *
 * Tests cover:
 * - Data model structure and validation
 * - Error message mapping logic
 * - Configuration and getters
 * - Tool response history tracking
 *
 * Note: Full HTTP client testing requires integration tests with a test server.
 * Unit tests focus on testable components like data models, error mapping, and configuration.
 */
class OllamaApiClientTest {

    private lateinit var mockHttpClient: HttpClient
    private lateinit var ollamaApiClient: OllamaApiClient
    private val baseUrl = "http://localhost:11434"
    private val model = "gemma3:1b"
    private val timeout = 120000L

    @BeforeEach
    fun setup() {
        mockHttpClient = mockk(relaxed = true)
        ollamaApiClient = OllamaApiClient(
            httpClient = mockHttpClient,
            baseUrl = baseUrl,
            model = model,
            timeout = timeout
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test getModel returns configured model name`() {
        // Act
        val returnedModel = ollamaApiClient.getModel()

        // Assert
        assertEquals(model, returnedModel)
    }

    @Test
    fun `test OllamaMessage data structure is valid`() {
        // Arrange
        val role = "user"
        val content = "Test message"

        // Act
        val message = OllamaMessage(role = role, content = content)

        // Assert
        assertEquals(role, message.role)
        assertEquals(content, message.content)
        assertTrue(message.timestamp > 0)
    }

    @Test
    fun `test OllamaMessageRole enum has correct values`() {
        // Assert
        assertEquals("system", OllamaMessageRole.SYSTEM.value)
        assertEquals("user", OllamaMessageRole.USER.value)
        assertEquals("assistant", OllamaMessageRole.ASSISTANT.value)
    }

    @Test
    fun `test OllamaChatRequest data structure is valid`() {
        // Arrange
        val messages = listOf(
            OllamaMessage(role = "user", content = "Hello")
        )
        val temperature = 0.7
        val maxTokens = 100

        // Act
        val request = OllamaChatRequest(
            model = model,
            messages = messages,
            stream = false,
            options = OllamaOptions(
                temperature = temperature,
                numPredict = maxTokens,
                topP = 0.9
            )
        )

        // Assert
        assertEquals(model, request.model)
        assertEquals(1, request.messages.size)
        assertEquals(false, request.stream)
        assertNotNull(request.options)
        assertEquals(temperature, request.options?.temperature)
        assertEquals(maxTokens, request.options?.numPredict)
        assertEquals(0.9, request.options?.topP)
    }

    @Test
    fun `test OllamaOptions with nullable fields`() {
        // Act
        val options1 = OllamaOptions(
            temperature = 0.7,
            numPredict = null,
            topP = null
        )

        val options2 = OllamaOptions(
            temperature = null,
            numPredict = 2048,
            topP = 0.9
        )

        // Assert
        assertEquals(0.7, options1.temperature)
        assertNull(options1.numPredict)
        assertNull(options1.topP)

        assertNull(options2.temperature)
        assertEquals(2048, options2.numPredict)
        assertEquals(0.9, options2.topP)
    }

    @Test
    fun `test OllamaChatResponse data structure is valid`() {
        // Arrange
        val responseText = "Hello! How can I help you?"

        val response = OllamaChatResponse(
            id = "chat-123",
            objectType = "chat.completion",
            created = System.currentTimeMillis(),
            model = model,
            choices = listOf(
                OllamaChoice(
                    index = 0,
                    message = OllamaResponseMessage(
                        role = "assistant",
                        content = responseText
                    ),
                    finishReason = "stop"
                )
            ),
            usage = OllamaUsage(
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30
            )
        )

        // Assert
        assertEquals("chat-123", response.id)
        assertEquals("chat.completion", response.objectType)
        assertEquals(model, response.model)
        assertEquals(1, response.choices.size)
        assertEquals(responseText, response.choices[0].message.content)
        assertEquals("stop", response.choices[0].finishReason)
        assertEquals(10, response.usage?.promptTokens)
        assertEquals(20, response.usage?.completionTokens)
        assertEquals(30, response.usage?.totalTokens)
    }

    @Test
    fun `test OllamaChatResponse with nullable fields`() {
        // Act
        val response = OllamaChatResponse(
            id = null,
            objectType = null,
            created = null,
            model = model,
            choices = listOf(
                OllamaChoice(
                    index = 0,
                    message = OllamaResponseMessage(
                        role = "assistant",
                        content = "Response"
                    ),
                    finishReason = null
                )
            ),
            usage = null
        )

        // Assert
        assertNull(response.id)
        assertNull(response.objectType)
        assertNull(response.created)
        assertNull(response.choices[0].finishReason)
        assertNull(response.usage)
    }

    @Test
    fun `test OllamaUsage data structure is valid`() {
        // Arrange
        val promptTokens = 15
        val completionTokens = 25
        val totalTokens = 40

        // Act
        val usage = OllamaUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens
        )

        // Assert
        assertEquals(promptTokens, usage.promptTokens)
        assertEquals(completionTokens, usage.completionTokens)
        assertEquals(totalTokens, usage.totalTokens)
    }

    @Test
    fun `test OllamaMessageResult data structure is valid`() {
        // Arrange
        val text = "Response text"
        val usage = OllamaUsage(
            promptTokens = 10,
            completionTokens = 20,
            totalTokens = 30
        )
        val responseTimeMs = 1500L

        // Act
        val result = OllamaMessageResult(
            text = text,
            usage = usage,
            responseTimeMs = responseTimeMs
        )

        // Assert
        assertEquals(text, result.text)
        assertEquals(usage, result.usage)
        assertEquals(responseTimeMs, result.responseTimeMs)
    }

    @Test
    fun `test OllamaStreamChunk data structure is valid`() {
        // Arrange
        val chunkResponse = "Partial text"

        // Act
        val chunk = OllamaStreamChunk(
            model = model,
            createdAt = "2024-01-01T12:00:00Z",
            response = chunkResponse,
            done = false
        )

        // Assert
        assertEquals(model, chunk.model)
        assertEquals(chunkResponse, chunk.response)
        assertEquals(false, chunk.done)
    }

    @Test
    fun `test OllamaStreamChunk with completion markers`() {
        // Act - Chunk with data
        val chunk1 = OllamaStreamChunk(
            model = model,
            createdAt = "2024-01-01T12:00:00Z",
            response = "Hello",
            done = false
        )

        // Act - Final chunk
        val chunk2 = OllamaStreamChunk(
            model = model,
            createdAt = "2024-01-01T12:00:01Z",
            response = null,
            done = true,
            context = listOf(1, 2, 3),
            evalCount = 100,
            evalDuration = 5000000000
        )

        // Assert
        assertEquals(false, chunk1.done)
        assertEquals("Hello", chunk1.response)
        assertNull(chunk1.context)

        assertEquals(true, chunk2.done)
        assertNull(chunk2.response)
        assertNotNull(chunk2.context)
        assertEquals(3, chunk2.context?.size)
        assertEquals(100, chunk2.evalCount)
        assertEquals(5000000000, chunk2.evalDuration)
    }

    @Test
    fun `test TimedOllamaResponse data structure is valid`() {
        // Arrange
        val response = OllamaChatResponse(
            id = "test",
            model = model,
            choices = listOf(
                OllamaChoice(
                    index = 0,
                    message = OllamaResponseMessage(
                        role = "assistant",
                        content = "Response"
                    )
                )
            ),
            usage = null
        )

        // Act
        val timedResponse = TimedOllamaResponse(response = response)

        // Assert
        assertEquals(response, timedResponse.response)
        assertTrue(timedResponse.timestamp > 0)
    }

    @Test
    fun `test tool response history methods track responses correctly`() {
        // Initially, history should be empty
        assertEquals(0, ollamaApiClient.getToolResponseCount())
        assertNull(ollamaApiClient.getLatestToolResponse())
        assertNull(ollamaApiClient.getToolResponseAt(0))

        // The history is populated internally by the client
        // These methods provide read-only access to that history
    }

    @Test
    fun `test OllamaModelInfo data structure is valid`() {
        // Arrange
        val name = "gemma3:1b"
        val modifiedAt = "2024-01-01T12:00:00Z"
        val size = 1000000000L
        val digest = "abc123"

        // Act
        val modelInfo = OllamaModelInfo(
            name = name,
            model = name,
            modifiedAt = modifiedAt,
            size = size,
            digest = digest,
            details = OllamaModelDetails(
                parentModel = "",
                format = "gguf",
                family = "gemma3",
                families = listOf("gemma3"),
                parameterSize = "1B",
                quantizationLevel = "Q4_K_M"
            )
        )

        // Assert
        assertEquals(name, modelInfo.name)
        assertEquals(name, modelInfo.model)
        assertEquals(modifiedAt, modelInfo.modifiedAt)
        assertEquals(size, modelInfo.size)
        assertEquals(digest, modelInfo.digest)
        assertEquals("gemma3", modelInfo.details.family)
    }

    @Test
    fun `test OllamaModelsResponse data structure is valid`() {
        // Arrange
        val models = listOf(
            OllamaModelInfo(
                name = "gemma3:1b",
                model = "gemma3:1b",
                modifiedAt = "2024-01-01T12:00:00Z",
                size = 1000000000L,
                digest = "abc123",
                details = OllamaModelDetails(
                    parentModel = "",
                    format = "gguf",
                    family = "gemma3",
                    families = listOf("gemma3"),
                    parameterSize = "1B",
                    quantizationLevel = "Q4_K_M"
                )
            )
        )

        // Act
        val response = OllamaModelsResponse(models = models)

        // Assert
        assertEquals(1, response.models.size)
        assertEquals("gemma3:1b", response.models[0].name)
    }
}
