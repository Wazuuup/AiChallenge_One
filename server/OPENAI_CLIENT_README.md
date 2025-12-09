# OpenAI-Compatible API Client

This project now includes support for OpenAI-compatible API models through the `OpenAIApiClient` class.

## Features

- Full OpenAI Chat Completions API compatibility
- **Response History Tracking**: Automatically saves copies of all OpenAIResponse objects
- Support for multiple providers:
    - **OpenAI** (GPT-3.5, GPT-4, GPT-4-turbo, etc.)
    - **Azure OpenAI**
    - **LocalAI** (self-hosted)
    - **Ollama** (with OpenAI compatibility)
    - **OpenRouter**
    - Any other OpenAI-compatible API

## Configuration

### Environment Variables

You can configure the OpenAI client using environment variables:

```bash
export OPENAI_BASE_URL="https://api.openai.com/v1"
export OPENAI_API_KEY="sk-your-api-key-here"
export OPENAI_MODEL="gpt-3.5-turbo"
export OPENAI_MAX_TOKENS="2048"
export OPENAI_TOP_P="0.9"
```

### Configuration File

Alternatively, configure in `server/src/main/resources/application-dev.conf`:

#### For OpenAI:

```hocon
openai {
    baseUrl = "https://api.openai.com/v1"
    apiKey = "sk-your-openai-api-key"
    model = "gpt-3.5-turbo"
    maxTokens = 2048
    topP = 0.9
}
```

#### For Azure OpenAI:

```hocon
openai {
    baseUrl = "https://your-resource.openai.azure.com/openai/deployments/your-deployment"
    apiKey = "your-azure-key"
    model = "gpt-35-turbo"
}
```

#### For LocalAI (running locally):

```hocon
openai {
    baseUrl = "http://localhost:8080/v1"
    apiKey = "not-needed"
    model = "gpt-3.5-turbo"
}
```

#### For Ollama with OpenAI compatibility:

```hocon
openai {
    baseUrl = "http://localhost:11434/v1"
    apiKey = "ollama"
    model = "llama2"
}
```

#### For OpenRouter:

```hocon
openai {
    baseUrl = "https://openrouter.ai/api/v1"
    apiKey = "sk-or-v1-your-key"
    model = "openai/gpt-3.5-turbo"
}
```

## Usage

### Programmatic Usage

The `OpenAIApiClient` is automatically injected via Koin DI when configured. You can inject it in your services:

```kotlin
import ru.sber.cb.aichallenge_one.client.OpenAIApiClient
import ru.sber.cb.aichallenge_one.client.OpenAIMessage
import org.koin.core.component.inject

class MyService : KoinComponent {
    private val openAIClient: OpenAIApiClient? by inject()

    suspend fun chat(userMessage: String): String? {
        return openAIClient?.let { client ->
            val messages = listOf(
                OpenAIMessage(role = "user", content = userMessage)
            )
            client.sendMessage(
                messageHistory = messages,
                customSystemPrompt = "You are a helpful assistant.",
                temperature = 0.7
            )
        }
    }
}
```

### API Parameters

The `OpenAIApiClient.sendMessage()` method accepts:

- **messageHistory**: `List<OpenAIMessage>` - Conversation history
- **customSystemPrompt**: `String` - System prompt (default: "You are a helpful assistant.")
- **temperature**: `Double` - Response randomness (0.0-2.0, default: 0.7)

Additional parameters configured at client creation:

- **model**: Model name (e.g., "gpt-3.5-turbo", "gpt-4")
- **maxTokens**: Maximum response tokens (optional)
- **topP**: Top-p sampling parameter (optional, 0.0-1.0)

## Data Models

### OpenAIMessage

```kotlin
@Serializable
data class OpenAIMessage(
    val role: String,      // "system", "user", "assistant", "function"
    val content: String    // Message content
)
```

### OpenAIRequest

```kotlin
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.7,
    val top_p: Double? = null,
    val max_tokens: Int? = null,
    val stream: Boolean = false
)
```

### OpenAIResponse

```kotlin
@Serializable
data class OpenAIResponse(
    val id: String,
    val object: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)
```

## Compatibility Helpers

The client includes helper methods to convert between GigaChat and OpenAI message formats:

```kotlin
// Convert GigaChatMessage to OpenAIMessage
val openAIMessage = openAIClient.convertFromGigaChatMessage(gigaChatMessage)

// Convert OpenAIMessage to GigaChatMessage
val gigaChatMessage = openAIClient.convertToGigaChatMessage(openAIMessage)
```

## Example: Testing the Client

You can test the OpenAI client by running the server with configuration:

1. Set environment variables:
   ```bash
   export GIGACHAT_CLIENT_ID="your-gigachat-id"
   export GIGACHAT_CLIENT_SECRET="your-gigachat-secret"
   export OPENAI_BASE_URL="https://api.openai.com/v1"
   export OPENAI_API_KEY="sk-your-key"
   export OPENAI_MODEL="gpt-3.5-turbo"
   ```

2. Run the server:
   ```bash
   .\gradlew.bat :server:run
   ```

3. The server will log if OpenAI configuration is loaded:
   ```
   OpenAI-compatible API configuration loaded successfully
   OpenAI Base URL: https://api.openai.com/v1
   OpenAI Model: gpt-3.5-turbo
   ```

## Response History Tracking

The `OpenAIApiClient` automatically saves copies of all `OpenAIResponse` objects received from the API along with
response time measurements. This feature allows you to:

- Track all API interactions
- Measure and analyze response times
- Analyze token usage over time
- Review previous responses
- Monitor model performance
- Compare speed across different models

### Available Methods

#### Get All Response History

```kotlin
val allResponses: List<TimedOpenAIResponse> = openAIClient.getResponseHistory()
println("Total responses: ${allResponses.size}")

// Access individual timed responses
allResponses.forEach { timedResponse ->
    println("Model: ${timedResponse.response.model}")
    println("Response time: ${timedResponse.responseTimeMs}ms")
    println("Tokens used: ${timedResponse.response.usage?.totalTokens}")
}
```

#### Get the Latest Response

```kotlin
val latestResponse: TimedOpenAIResponse? = openAIClient.getLatestResponse()
latestResponse?.let { timedResponse ->
    println("Latest model: ${timedResponse.response.model}")
    println("Response ID: ${timedResponse.response.id}")
    println("Response time: ${timedResponse.responseTimeMs}ms")
    println("Token usage: ${timedResponse.response.usage?.totalTokens}")
}
```

#### Get Response Count

```kotlin
val count = openAIClient.getResponseCount()
println("Total API calls: $count")
```

#### Get Response by Index

```kotlin
val firstResponse = openAIClient.getResponseAt(0)
val thirdResponse = openAIClient.getResponseAt(2)

firstResponse?.let {
    println("First response took ${it.responseTimeMs}ms")
}
```

#### Get Total Token Usage

```kotlin
val tokenUsage = openAIClient.getTotalTokenUsage()
println("Total prompt tokens: ${tokenUsage["prompt_tokens"]}")
println("Total completion tokens: ${tokenUsage["completion_tokens"]}")
println("Total tokens: ${tokenUsage["total_tokens"]}")
```

#### Filter Responses by Model

```kotlin
val gpt4Responses = openAIClient.getResponsesByModel("gpt-4")
println("GPT-4 responses: ${gpt4Responses.size}")

// Analyze GPT-4 performance
val avgGpt4Time = gpt4Responses.map { it.responseTimeMs }.average()
println("Average GPT-4 response time: ${avgGpt4Time}ms")
```

#### Filter Responses by Time Range

```kotlin
val startTime = System.currentTimeMillis() / 1000 - 3600 // 1 hour ago
val endTime = System.currentTimeMillis() / 1000
val recentResponses = openAIClient.getResponsesByTimeRange(startTime, endTime)
println("Responses in last hour: ${recentResponses.size}")
```

#### Clear Response History

```kotlin
openAIClient.clearResponseHistory()
println("Response history cleared")
```

### Response Time Analysis Methods

#### Get Average Response Time

```kotlin
val avgTime = openAIClient.getAverageResponseTime()
println("Average response time: ${avgTime}ms")
```

#### Get Fastest Response Time

```kotlin
val fastestTime = openAIClient.getFastestResponseTime()
fastestTime?.let {
    println("Fastest response: ${it}ms")
}
```

#### Get Slowest Response Time

```kotlin
val slowestTime = openAIClient.getSlowestResponseTime()
slowestTime?.let {
    println("Slowest response: ${it}ms")
}
```

#### Get Response Time Statistics

```kotlin
val stats = openAIClient.getResponseTimeStats()
println("Response Time Statistics:")
println("  Min: ${stats["min"]}ms")
println("  Max: ${stats["max"]}ms")
println("  Average: ${stats["average"]}ms")
println("  Median: ${stats["median"]}ms")
```

### Example: Analyzing Token Usage and Performance

```kotlin
import ru.sber.cb.aichallenge_one.client.OpenAIApiClient
import org.koin.core.component.inject

class ApiAnalyzer : KoinComponent {
    private val openAIClient: OpenAIApiClient? by inject()

    fun analyzeTokenUsage() {
        val client = openAIClient ?: return

        // Get total token usage
        val totalUsage = client.getTotalTokenUsage()
        val totalTokens = totalUsage["total_tokens"] ?: 0
        val promptTokens = totalUsage["prompt_tokens"] ?: 0
        val completionTokens = totalUsage["completion_tokens"] ?: 0

        println("=== Token Usage Summary ===")
        println("Total API calls: ${client.getResponseCount()}")
        println("Total tokens used: $totalTokens")
        println("Prompt tokens: $promptTokens")
        println("Completion tokens: $completionTokens")

        // Calculate average tokens per request
        val avgTokensPerRequest = if (client.getResponseCount() > 0) {
            totalTokens / client.getResponseCount()
        } else 0

        println("Average tokens per request: $avgTokensPerRequest")

        // Analyze by model
        val allResponses = client.getResponseHistory()
        val modelUsage = allResponses.groupBy { it.response.model }
            .mapValues { (_, responses) ->
                responses.sumOf { it.response.usage?.totalTokens ?: 0 }
            }

        println("\n=== Usage by Model ===")
        modelUsage.forEach { (model, tokens) ->
            println("$model: $tokens tokens")
        }
    }

    fun analyzePerformance() {
        val client = openAIClient ?: return

        // Get response time statistics
        val stats = client.getResponseTimeStats()

        println("\n=== Performance Statistics ===")
        println("Total API calls: ${client.getResponseCount()}")
        println("Fastest response: ${stats["min"]}ms")
        println("Slowest response: ${stats["max"]}ms")
        println("Average response: ${stats["average"]}ms")
        println("Median response: ${stats["median"]}ms")

        // Analyze by model
        val allResponses = client.getResponseHistory()
        val modelPerformance = allResponses.groupBy { it.response.model }
            .mapValues { (_, responses) ->
                val times = responses.map { it.responseTimeMs }
                mapOf(
                    "count" to times.size,
                    "avg" to (times.average().toLong()),
                    "min" to (times.minOrNull() ?: 0),
                    "max" to (times.maxOrNull() ?: 0)
                )
            }

        println("\n=== Performance by Model ===")
        modelPerformance.forEach { (model, stats) ->
            println("$model:")
            println("  Calls: ${stats["count"]}")
            println("  Avg: ${stats["avg"]}ms")
            println("  Min: ${stats["min"]}ms")
            println("  Max: ${stats["max"]}ms")
        }
    }

    fun getLastResponseDetails() {
        val client = openAIClient ?: return
        val latestResponse = client.getLatestResponse() ?: return

        println("\n=== Latest Response ===")
        println("Model: ${latestResponse.response.model}")
        println("Response ID: ${latestResponse.response.id}")
        println("Response time: ${latestResponse.responseTimeMs}ms")
        println("Created: ${latestResponse.response.created}")
        println("Choices: ${latestResponse.response.choices.size}")

        latestResponse.response.usage?.let { usage ->
            println("Prompt tokens: ${usage.promptTokens}")
            println("Completion tokens: ${usage.completionTokens}")
            println("Total tokens: ${usage.totalTokens}")

            // Calculate tokens per second
            val tokensPerSecond = (usage.totalTokens.toDouble() / latestResponse.responseTimeMs) * 1000
            println("Tokens per second: ${"%.2f".format(tokensPerSecond)}")
        }
    }

    fun compareModels() {
        val client = openAIClient ?: return
        val allResponses = client.getResponseHistory()

        println("\n=== Model Comparison ===")
        val modelStats = allResponses.groupBy { it.response.model }
            .map { (model, responses) ->
                val avgTime = responses.map { it.responseTimeMs }.average()
                val avgTokens = responses.mapNotNull { it.response.usage?.totalTokens }.average()
                val tokensPerSecond = (avgTokens / avgTime) * 1000

                Triple(model, avgTime, tokensPerSecond)
            }
            .sortedBy { it.second } // Sort by speed

        modelStats.forEach { (model, avgTime, tokensPerSec) ->
            println("$model: ${"%.0f".format(avgTime)}ms avg, ${"%.2f".format(tokensPerSec)} tokens/sec")
        }
    }
}
```

### Response History Lifecycle

- **Automatic Saving**: Every successful API response is automatically saved
- **Persistence**: History is maintained in memory for the lifetime of the `OpenAIApiClient` instance
- **Memory Management**: Consider calling `clearResponseHistory()` periodically for long-running applications
- **Thread Safety**: The response history is stored in a mutable list; ensure proper synchronization if accessed from
  multiple threads

### Use Cases

1. **Cost Tracking**: Monitor token usage to estimate API costs
2. **Performance Analysis**: Track response times and model performance across different models
3. **Debugging**: Review previous interactions when troubleshooting
4. **Auditing**: Maintain logs of all AI interactions with timestamps
5. **A/B Testing**: Compare responses, performance, and token efficiency from different models
6. **Speed Optimization**: Identify slow models and optimize model selection based on response time
7. **Capacity Planning**: Analyze response time trends to predict infrastructure needs
8. **SLA Monitoring**: Track percentile response times (median, p95, p99) for service level agreements

## Error Handling

The client includes comprehensive error handling:

- HTTP errors are logged with status codes and response bodies
- Exceptions are propagated with descriptive messages
- Token usage is logged (when available from the API)
- Response history is only saved for successful responses

## Security Notes

- **Never commit API keys** to version control
- Use environment variables or secure secret management in production
- The `application-dev.conf` file should be in `.gitignore`
- Rotate API keys regularly

## Supported Models

### OpenAI Models

- gpt-3.5-turbo (fastest, most cost-effective)
- gpt-4 (most capable)
- gpt-4-turbo (optimized GPT-4)
- gpt-4-32k (extended context)

### Azure OpenAI

- Use your deployed model names
- Configure the full deployment URL in `baseUrl`

### LocalAI / Ollama

- Any model supported by your local installation
- llama2, mistral, codellama, etc.

### OpenRouter

- Access to 100+ models through a single API
- Prefix model names with provider (e.g., "openai/gpt-4", "anthropic/claude-2")

## License

This client implementation follows the same license as the main project.
