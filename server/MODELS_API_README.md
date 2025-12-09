# OpenRouter Models API

This API allows you to fetch and query available AI models from OpenRouter.

## Configuration

The models service is automatically configured when you set up the OpenAI-compatible client. It uses the same `baseUrl`
and `apiKey` from your configuration.

### Required Configuration

In `application-dev.conf`:

```hocon
openai {
    baseUrl = "https://openrouter.ai/api/v1"
    apiKey = "sk-or-v1-your-api-key"
}
```

Or via environment variables:

```bash
export OPENAI_BASE_URL="https://openrouter.ai/api/v1"
export OPENAI_API_KEY="sk-or-v1-your-api-key"
```

## API Endpoints

Base URL: `http://localhost:8080/api/models`

### 1. Get All Models

**Endpoint**: `GET /api/models`

**Description**: Fetch all available models from OpenRouter. Results are cached for 1 hour.

**Query Parameters**:

- `refresh` (boolean, optional): Set to `true` to force refresh and bypass cache

**Example Request**:

```bash
curl http://localhost:8080/api/models
```

**Example Request with Refresh**:

```bash
curl http://localhost:8080/api/models?refresh=true
```

**Example Response**:

```json
{
  "models": [
    {
      "id": "openai/gpt-3.5-turbo",
      "name": "OpenAI: GPT-3.5 Turbo",
      "description": "GPT-3.5 Turbo is OpenAI's fastest model",
      "contextLength": 16385,
      "pricing": {
        "prompt": "0.0000005",
        "completion": "0.0000015"
      },
      "topProvider": {
        "maxCompletionTokens": 4096,
        "isModerated": true
      },
      "architecture": {
        "modality": "text",
        "tokenizer": "GPT",
        "instructType": "none"
      }
    },
    ...
  ],
  "count": 150
}
```

---

### 2. Get Model by ID

**Endpoint**: `GET /api/models/{id}`

**Description**: Get details for a specific model by its ID. Supports nested IDs with `/`.

**Path Parameters**:

- `id`: Model ID (e.g., `openai/gpt-3.5-turbo`, `anthropic/claude-2`)

**Example Request**:

```bash
curl http://localhost:8080/api/models/openai/gpt-3.5-turbo
```

**Example Response**:

```json
{
  "id": "openai/gpt-3.5-turbo",
  "name": "OpenAI: GPT-3.5 Turbo",
  "description": "GPT-3.5 Turbo is OpenAI's fastest model",
  "contextLength": 16385,
  "pricing": {
    "prompt": "0.0000005",
    "completion": "0.0000015"
  }
}
```

**Error Response** (404):

```json
{
  "error": "Model not found: openai/invalid-model"
}
```

---

### 3. Search Models

**Endpoint**: `GET /api/models/search`

**Description**: Search models by name, ID, or description.

**Query Parameters**:

- `q` (string, required): Search query

**Example Request**:

```bash
curl "http://localhost:8080/api/models/search?q=gpt-4"
```

**Example Response**:

```json
{
  "query": "gpt-4",
  "models": [
    {
      "id": "openai/gpt-4",
      "name": "OpenAI: GPT-4",
      "description": "More capable than any GPT-3.5 model"
    },
    {
      "id": "openai/gpt-4-turbo",
      "name": "OpenAI: GPT-4 Turbo",
      "description": "The latest GPT-4 model with improved performance"
    }
  ],
  "count": 2
}
```

---

### 4. Get Models by Provider

**Endpoint**: `GET /api/models/by-provider`

**Description**: Get all models grouped by provider.

**Example Request**:

```bash
curl http://localhost:8080/api/models/by-provider
```

**Example Response**:

```json
{
  "providers": {
    "openai": [
      {
        "id": "openai/gpt-3.5-turbo",
        "name": "OpenAI: GPT-3.5 Turbo"
      },
      {
        "id": "openai/gpt-4",
        "name": "OpenAI: GPT-4"
      }
    ],
    "anthropic": [
      {
        "id": "anthropic/claude-2",
        "name": "Anthropic: Claude 2"
      }
    ],
    "meta-llama": [
      {
        "id": "meta-llama/llama-2-70b-chat",
        "name": "Meta: Llama 2 70B Chat"
      }
    ]
  },
  "providerCount": 3
}
```

---

### 5. Get Model Statistics

**Endpoint**: `GET /api/models/stats`

**Description**: Get statistics about available models.

**Example Request**:

```bash
curl http://localhost:8080/api/models/stats
```

**Example Response**:

```json
{
  "totalModels": 150,
  "providers": 15,
  "providerBreakdown": {
    "openai": 8,
    "anthropic": 5,
    "meta-llama": 10,
    "google": 7
  },
  "modalityBreakdown": {
    "text": 120,
    "text+image": 25,
    "image": 5
  },
  "lastFetchTime": 1703001234567,
  "cacheAge": 120000
}
```

---

### 6. Clear Cache

**Endpoint**: `POST /api/models/cache/clear`

**Description**: Clear the cached models data. Next request will fetch fresh data from OpenRouter.

**Example Request**:

```bash
curl -X POST http://localhost:8080/api/models/cache/clear
```

**Example Response**:

```json
{
  "message": "Cache cleared successfully"
}
```

---

## Service Features

### Caching

- Models are cached for **1 hour** (3600000 ms) to reduce API calls
- Use `?refresh=true` to force refresh
- Use `POST /api/models/cache/clear` to manually clear cache

### Error Handling

If the models service is not configured, all endpoints return:

**Status**: `503 Service Unavailable`

```json
{
  "error": "Models service is not configured. Please configure OpenAI API settings."
}
```

If an error occurs during fetching:

**Status**: `500 Internal Server Error`

```json
{
  "error": "Failed to fetch models: Connection timeout"
}
```

### Fallback Behavior

If fetching fresh models fails but cached data exists (even if expired), the service returns the stale cached data with
a warning in logs.

## Programmatic Usage

You can also use the `OpenRouterModelsService` directly in your code:

```kotlin
import ru.sber.cb.aichallenge_one.service.OpenRouterModelsService
import org.koin.core.component.inject

class MyService : KoinComponent {
    private val modelsService: OpenRouterModelsService? by inject()

    suspend fun listModels() {
        val models = modelsService?.fetchAvailableModels()
        models?.forEach { model ->
            println("${model.id}: ${model.name}")
        }
    }

    suspend fun findModel(query: String) {
        val results = modelsService?.searchModels(query)
        println("Found ${results?.size} models matching '$query'")
    }

    suspend fun getModelDetails(modelId: String) {
        val model = modelsService?.getModelById(modelId)
        model?.let {
            println("Model: ${it.name}")
            println("Context Length: ${it.contextLength}")
            println("Pricing - Prompt: ${it.pricing?.prompt}, Completion: ${it.pricing?.completion}")
        }
    }
}
```

## Data Models

### OpenRouterModel

```kotlin
@Serializable
data class OpenRouterModel(
    val id: String,                          // Model ID (e.g., "openai/gpt-3.5-turbo")
    val name: String? = null,                // Human-readable name
    val created: Long? = null,               // Creation timestamp
    val description: String? = null,         // Model description
    val contextLength: Int? = null,          // Maximum context length in tokens
    val pricing: OpenRouterPricing? = null,  // Pricing information
    val topProvider: OpenRouterProvider? = null,
    val architecture: OpenRouterArchitecture? = null
)
```

### OpenRouterPricing

```kotlin
@Serializable
data class OpenRouterPricing(
    val prompt: String? = null,       // Cost per prompt token
    val completion: String? = null,   // Cost per completion token
    val image: String? = null,        // Cost per image (if applicable)
    val request: String? = null       // Cost per request (if applicable)
)
```

### OpenRouterProvider

```kotlin
@Serializable
data class OpenRouterProvider(
    val maxCompletionTokens: Int? = null,  // Max tokens in completion
    val isModerated: Boolean? = null       // Whether content is moderated
)
```

### OpenRouterArchitecture

```kotlin
@Serializable
data class OpenRouterArchitecture(
    val modality: String? = null,      // "text", "text+image", "image", etc.
    val tokenizer: String? = null,     // Tokenizer type
    val instructType: String? = null   // Instruction format type
)
```

## Example Workflows

### 1. List All GPT-4 Models

```bash
curl "http://localhost:8080/api/models/search?q=gpt-4" | jq '.models[] | {id, name, contextLength}'
```

### 2. Find Cheapest Models

```bash
curl http://localhost:8080/api/models | jq '.models | sort_by(.pricing.prompt | tonumber) | .[0:5]'
```

### 3. Get All Anthropic Models

```bash
curl http://localhost:8080/api/models/by-provider | jq '.providers.anthropic'
```

### 4. Check Model Details Before Using

```bash
# Get model info
curl http://localhost:8080/api/models/openai/gpt-4 | jq '{id, contextLength, pricing}'

# Use the model
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{"text": "Hello!", "model": "openai/gpt-4"}'
```

## Notes

- The service automatically fetches models on first API call
- Models are cached to minimize API calls to OpenRouter
- All endpoints support JSON response format
- Model IDs with `/` in the path are properly handled (e.g., `openai/gpt-4`)

## Troubleshooting

### Service Not Available

If you see `503 Service Unavailable`, ensure that:

1. `openai.baseUrl` is set to `https://openrouter.ai/api/v1`
2. `openai.apiKey` is set to your OpenRouter API key
3. The server was restarted after configuration changes

### Empty Model List

If models return empty:

1. Check your OpenRouter API key is valid
2. Try forcing a refresh: `GET /api/models?refresh=true`
3. Check server logs for error messages

### Rate Limiting

OpenRouter may rate limit API calls. The caching mechanism helps avoid hitting rate limits by:

- Caching models for 1 hour
- Reusing cached data on errors
- Only fetching on explicit refresh or cache expiration

## License

This API implementation follows the same license as the main project.
