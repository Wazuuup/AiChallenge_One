# Voice Input Feature - Technical Specification

**Version:** 1.0
**Status:** Ready for Implementation
**Date:** 2026-01-27

## Overview

Голосовой ввод для AI Chat приложения с автоматическим распознаванием речи через OpenRouter API (Gemini 3 Flash Preview)
и отправкой распознанного текста в LLM.

**User Flow:**

```
Голосовой ввод → Запись аудио → STT (OpenRouter) → Текст → LLM → Ответ текстом
```

## Requirements Summary

| Category              | Decision                                               |
|-----------------------|--------------------------------------------------------|
| **UI Layout**         | Кнопка микрофона вместо кнопки отправки (переключение) |
| **Audio Format**      | WebM/Opus                                              |
| **Recording UI**      | Анимированный микрофон + таймер                        |
| **Post-STT**          | Автоотправка распознанного текста                      |
| **Transport**         | Через backend прокси (/api/transcribe)                 |
| **Duration**          | Max 30 секунд                                          |
| **STT Model**         | Gemini 3 Flash via OpenRouter                          |
| **Error Handling**    | Toast об ошибке                                        |
| **Permission**        | Banner сверху при первом доступе                       |
| **Recording Control** | Toggle click (клик = старт, ещё клик = стоп)           |
| **Language**          | Автоопределение языка STT                              |
| **Cost**              | Отображать в Token Usage sidebar                       |
| **Silence Detection** | Нет, только ручная остановка                           |
| **VAD**               | Без VAD                                                |
| **Audio Quality**     | Low (16kHz mono)                                       |
| **Storage**           | Не сохранять аудио                                     |
| **Backend Auth**      | Переиспользовать OPENAI_API_KEY                        |
| **Chunking**          | Single request                                         |
| **History**           | Только текст в истории                                 |
| **Fallback**          | Ручной ввод при ошибке                                 |
| **Web API**           | MediaRecorder                                          |
| **Visual Feedback**   | Анимация иконки + таймер                               |
| **Interrupt**         | Сбросить запись                                        |
| **Preview**           | Нет, сразу отправить                                   |
| **Feature Toggle**    | Включено по умолчанию                                  |
| **Config**            | localStorage                                           |
| **Mobile**            | Web-only (mobile browser)                              |
| **Analytics**         | Без аналитики                                          |

---

## Architecture

### Frontend (Compose Web)

```
┌─────────────────────────────────────────────────────────────┐
│                      ChatScreen.kt                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              MessageInput Component                  │   │
│  │  ┌─────────────┐ ┌─────────────────┐ ┌──────────┐  │   │
│  │  │ TextField   │ │ Record/Send     │ │ Timer?   │  │   │
│  │  └─────────────┘ └─────────────────┘ └──────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │ AudioRecorder │
                    │   (JS Interop)│
                    └───────────────┘
                            │
                            ▼
                    POST /api/transcribe
                    (WebM blob → base64)
```

### Backend (Ktor)

```
┌─────────────────────────────────────────────────────────────┐
│                     TranscribeRouting.kt                    │
│                                                             │
│  POST /api/transcribe                                      │
│    ↓                                                        │
│  - Validate WebM/Opus format                               │
│  - Check duration ≤ 30s                                    │
│  - Convert to OpenRouter STT request format                │
│    ↓                                                        │
│  OpenRouterApiClient → Gemini 3 Flash                     │
│    ↓                                                        │
│  TranscribeResponse → { text, tokens, cost }              │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │  ChatService  │
                    │  sendMessage()│
                    └───────────────┘
```

---

## API Specification

### POST /api/transcribe

Запрос на распознавание речи из аудио.

**Request:**

```http
POST /api/transcribe
Content-Type: application/json

{
  "audioData": "base64_encoded_webm_audio",
  "format": "webm",
  "language": "auto"  // optional, auto-detect
}
```

**Success Response (200):**

```json
{
  "text": "Распознанный текст сообщения",
  "status": "SUCCESS",
  "tokenUsage": {
    "promptTokens": 150,
    "completionTokens": 0,
    "totalTokens": 150
  },
  "duration": 12.5,
  "cost": 0.0001
}
```

**Error Response (400):**

```json
{
  "status": "ERROR",
  "error": "INVALID_AUDIO_FORMAT",
  "message": "Audio must be in WebM/Opus format"
}
```

**Error Response (413):**

```json
{
  "status": "ERROR",
  "error": "AUDIO_TOO_LONG",
  "message": "Audio duration exceeds 30 seconds limit"
}
```

**Error Response (502):**

```json
{
  "status": "ERROR",
  "error": "STT_SERVICE_UNAVAILABLE",
  "message": "Speech-to-Text service is unavailable. Please try again or type manually."
}
```

---

## Data Models

### Shared Models (`shared/`)

```kotlin
@Serializable
data class TranscribeRequest(
    val audioData: String,  // base64 encoded WebM
    val format: String = "webm",
    val language: String = "auto"
)

@Serializable
data class TranscribeResponse(
    val text: String,
    val status: String,  // SUCCESS | ERROR
    val tokenUsage: TokenUsage,
    val duration: Double,  // seconds
    val cost: Double,
    val error: String? = null
)
```

### Backend Models

```kotlin
// OpenRouter STT Request
@Serializable
data class OpenRouterSTTRequest(
    val model: String = "google/gemini-3-flash-preview",
    val audio: AudioContent,
    val config: STTConfig
)

@Serializable
data class AudioContent(
    val mimeType: String = "audio/webm;codecs=opus",
    val data: String  // base64
)

@Serializable
data class STTConfig(
    val language: String = "auto",
    val enablePunctuation: Boolean = true,
    val enableTimestamps: Boolean = false
)
```

---

## Frontend Implementation Details

### Components

#### 1. AudioRecorder (JS Interop)

```kotlin
// composeApp/src/webMain/kotlin/ru/sber/cb/aichallenge_one/audio/AudioRecorder.kt

@JsModule("browser")
external object MediaRecorderAPI {
    class MediaRecorder(stream: MediaStream, options: MediaRecorderOptions) {
        var mimeType: String
        var state: String  // "inactive" | "recording" | "paused"
        var ondataavailable: ((event: BlobEvent) -> Unit)?
        var onstop: ((event: Event) -> Unit)?

        fun start(timeslice?: Int)
        fun stop()
        fun pause()
        fun resume()
    }

    interface MediaRecorderOptions {
        var mimeType: String?
        var audioBitsPerSecond: Int?
    }
}

external class BlobEvent {
    val data: Blob
}
```

#### 2. VoiceInputButton Composable

```kotlin
@Composable
fun VoiceInputButton(
    isRecording: Boolean,
    recordingDuration: Duration,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    IconButton(
        onClick = onToggleRecording,
        modifier = modifier
            .size(56.dp)
            .then(
                if (isRecording) Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                else Modifier
            )
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isRecording) "Stop recording" else "Start recording",
            tint = if (isRecording) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.primary
        )
    }
}
```

#### 3. Recording Timer

```kotlin
@Composable
fun RecordingTimer(duration: Duration) {
    Text(
        text = duration.toComponents { minutes, seconds, _ ->
            "%02d:%02d".format(minutes, seconds)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error
    )
}
```

### State Management

```kotlin
// ChatViewModel additions

class ChatViewModel : KoinComponent {
    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _recordingDuration = mutableStateOf(Duration.ZERO)
    val recordingDuration: State<Duration> = _recordingDuration

    private val _audioBlob = mutableStateOf<Blob?>(null)
    val audioBlob: State<Blob?> = _audioBlob

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        // Request mic permission
        // Start MediaRecorder
        // Start timer
    }

    private fun stopRecording() {
        // Stop MediaRecorder
        // Get blob
        // Send to /api/transcribe
        // Auto-send to LLM on success
    }

    private suspend fun transcribeAudio(audioBlob: Blob): TranscribeResponse {
        return chatApi.transcribe(
            TranscribeRequest(
                audioData = audioBlob.toBase64(),
                format = "webm",
                language = "auto"
            )
        )
    }
}
```

---

## Backend Implementation Details

### TranscribeRouting

```kotlin
// server/src/main/kotlin/ru/sber/cb/aichallenge_one/routing/TranscribeRouting.kt

fun Route.transcribeRoutes() {
    post("/api/transcribe") {
        try {
            val request = call.receive<TranscribeRequest>()

            // Validate audio format
            if (!request.format.equals("webm", ignoreCase = true)) {
                call.respond(HttpStatusCode.BadRequest, TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = TokenUsage(0, 0, 0),
                    duration = 0.0,
                    cost = 0.0,
                    error = "INVALID_AUDIO_FORMAT"
                ))
                return@post
            }

            // Decode base64
            val audioBytes = Base64.getDecoder().decode(request.audioData)

            // Check duration (from file header or estimate)
            val duration = estimateAudioDuration(audioBytes)
            if (duration > 30) {
                call.respond(HttpStatusCode.PayloadTooLarge, TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = TokenUsage(0, 0, 0),
                    duration = duration,
                    cost = 0.0,
                    error = "AUDIO_TOO_LONG"
                ))
                return@post
            }

            // Call OpenRouter STT
            val response = openRouterClient.transcribe(
                audioData = request.audioData,
                format = request.format,
                language = request.language
            )

            call.respond(HttpStatusCode.OK, response)

        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadGateway, TranscribeResponse(
                text = "",
                status = "ERROR",
                tokenUsage = TokenUsage(0, 0, 0),
                duration = 0.0,
                cost = 0.0,
                error = "STT_SERVICE_UNAVAILABLE"
            ))
        }
    }
}
```

### OpenRouterSTTClient

```kotlin
// server/src/main/kotlin/ru/sber/cb/aichallenge_one/client/OpenRouterSTTClient.kt

class OpenRouterSTTClient(
    private val config: OpenAIConfig
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun transcribe(
        audioData: String,
        format: String,
        language: String
    ): TranscribeResponse {
        return client.post("${config.baseUrl}/audio/transcriptions") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                append(HttpHeaders.ContentType, ContentType.Application.Json)
            }

            setBody(
                OpenRouterSTTRequest(
                    model = "google/gemini-3-flash-preview",
                    audio = AudioContent(
                        mimeType = "audio/webm;codecs=opus",
                        data = audioData
                    ),
                    config = STTConfig(
                        language = language,
                        enablePunctuation = true,
                        enableTimestamps = false
                    )
                )
            )
        }.body()
    }
}
```

---

## Configuration

### application.conf

```hocon
# Voice Input / Speech-to-Text Settings
voiceInput {
  enabled = true

  stt {
    provider = "openrouter"
    model = "google/gemini-3-flash-preview"
    language = "auto"
  }

  audio {
    maxDuration = 30  // seconds
    format = "webm"
    codec = "opus"
    sampleRate = 16000  // Hz (16kHz)
    channels = 1  // mono
  }

  features {
    silenceDetection = false
    autoSend = true
    showPreview = false
  }
}
```

---

## Error Handling

| Error                   | UI Response                                                     | Technical Action                  |
|-------------------------|-----------------------------------------------------------------|-----------------------------------|
| Mic permission denied   | Banner: "Разрешите доступ к микрофону для голосового ввода"     | Показать banner, кнопка неактивна |
| Recording interrupted   | Toast: "Запись прервана"                                        | Сбросить запись                   |
| Invalid audio format    | Toast: "Неверный формат аудио"                                  | Логирование ошибки                |
| Audio too long (>30s)   | Toast: "Аудио не может быть длиннее 30 секунд"                  | Отклонить запрос                  |
| STT service unavailable | Toast: "Сервис распознавания недоступен. Введите текст вручную" | Fallback на ручной ввод           |
| Empty transcription     | Toast: "Не удалось распознать речь. Попробуйте ещё раз"         | Предложить повторить              |
| Network error           | Toast: "Ошибка сети. Проверьте подключение"                     | Retry logic                       |

---

## Token Usage Integration

Обновить `TokenUsage` модель для включения STT токенов:

```kotlin
@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val sttTokens: Int = 0,  // NEW
    val sttCost: Double = 0.0  // NEW
)
```

В сайдбаре отображать:

```
Token Usage
─────────────────
Prompt Tokens:    150
Completion Tokens: 200
STT Tokens:        150
─────────────────
Total Tokens:      500
```

---

## Security Considerations

1. **API Key Security**: Использовать существующий `OPENAI_API_KEY` из env variables
2. **Audio Sanitization**: Валидировать формат и размер аудио на backend
3. **Rate Limiting**: Ограничить количество STT запросов (например, 10/минута)
4. **No Audio Storage**: Аудио не сохраняется на диске, удаляется после обработки
5. **CORS**: Проверить CORS настройки для WebM blobs

---

## Browser Compatibility

| Browser       | MediaRecorder | WebM/Opus | Status                      |
|---------------|---------------|-----------|-----------------------------|
| Chrome 90+    | ✅             | ✅         | Fully Supported             |
| Firefox 88+   | ✅             | ✅         | Fully Supported             |
| Safari 14.1+  | ✅             | ⚠️        | Partial (may need fallback) |
| Edge 90+      | ✅             | ✅         | Fully Supported             |
| Mobile Chrome | ✅             | ✅         | Fully Supported             |
| Mobile Safari | ⚠️            | ⚠️        | May need AAC fallback       |

**Fallback strategy**: Если WebM не поддерживается, показывать сообщение "Голосовой ввод не поддерживается в вашем
браузере".

---

## Testing Checklist

### Unit Tests

- [ ] `AudioRecorder` state management
- [ ] Base64 encoding/decoding
- [ ] Duration estimation
- [ ] Token usage calculation

### Integration Tests

- [ ] POST /api/transcribe with valid WebM
- [ ] POST /api/transcribe with invalid format (400)
- [ ] POST /api/transcribe with >30s audio (413)
- [ ] OpenRouter STT client mocking

### E2E Tests

- [ ] Record audio → transcribe → send to LLM → response
- [ ] Mic permission flow
- [ ] Error states (network, STT unavailable)
- [ ] 30 second limit enforcement

### Manual Testing

- [ ] Record 5s audio → verify transcription
- [ ] Record 35s audio → verify error
- [ ] Toggle recording on/off
- [ ] Visual feedback during recording
- [ ] Mobile browser testing

---

## Implementation Order

1. **Phase 1: Backend API**
    - Add TranscribeRequest/Response models to shared
    - Implement TranscribeRouting
    - Implement OpenRouterSTTClient
    - Add to AppModule DI

2. **Phase 2: Frontend Audio Recording**
    - Implement AudioRecorder JS interop
    - Add VoiceInputButton composable
    - Add RecordingTimer composable
    - Update ChatViewModel with recording state

3. **Phase 3: Integration**
    - Connect audio recording to /api/transcribe
    - Implement auto-send flow
    - Add error handling (toasts)
    - Update TokenUsage sidebar

4. **Phase 4: Polish**
    - Add mic permission banner
    - Add visual feedback (animation, timer)
    - Test on mobile browsers
    - Documentation update

---

## Success Criteria

✅ Пользователь может записать голосовое сообщение кликом на иконку микрофона
✅ Запись автоматически останавливается через 30 секунд
✅ Распознанный текст автоматически отправляется в LLM
✅ При ошибке STT пользователь видит понятное сообщение
✅ STT токены отображаются в сайдбаре Token Usage
✅ Функция работает в Chrome, Firefox, Edge (desktop и mobile)
✅ Аудио не сохраняется на сервере

---

## Open Questions

1. **Gemini 3 Flash STT availability**: Подтвердить, что OpenRouter поддерживает audio transcription через эту модель
2. **OpenRouter pricing**: Уточнить стоимость STT для gemini-3-flash-preview
3. **Safari fallback**: Нужен ли AAC fallback для старых версий Safari?
4. **Rate limiting**: Какое ограничение на количество STT запросов?

---

## References

- [MediaRecorder API - MDN](https://developer.mozilla.org/en-US/docs/Web/API/MediaRecorder)
- [OpenRouter API Docs](https://openrouter.ai/docs)
- [Gemini 3 Flash - Model Card](https://openrouter.ai/models/google/gemini-3-flash-preview)
- [Web Audio Codec Support](https://developer.mozilla.org/en-US/docs/Web/Media/Formats/Audio_codecs)
