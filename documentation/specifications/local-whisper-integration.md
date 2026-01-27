# Local Whisper Integration - Technical Specification

**Version:** 1.0
**Status:** Ready for Implementation
**Date:** 2026-01-27
**Author:** Claude (with user requirements)

## Overview

Замена OpenRouterSTTClient на локальную установку OpenAI Whisper для Speech-to-Text (STT) функциональности.
Интеграция осуществляется через ProcessBuilder с конвертацией аудио форматов через ffmpeg.

**Current Flow:**

```
Frontend (WebM) → Backend → OpenRouter STT API → Text
```

**New Flow:**

```
Frontend (WebM) → Backend → ffmpeg (WebM→WAV) → Whisper (Local) → Text
```

---

## Requirements Summary

| Category                   | Decision                                                |
|----------------------------|---------------------------------------------------------|
| **Whisper Installation**   | openai-whisper (official via pip)                       |
| **Model**                  | small (244M, ~2GB RAM)                                  |
| **Integration Method**     | ProcessBuilder (shell command execution)                |
| **Primary Priority**       | Простота поддержки и деплоя                             |
| **Concurrency**            | Единственный пользователь (без очереди)                 |
| **Audio Input Format**     | WebM from browser                                       |
| **Audio Conversion**       | ffmpeg via ProcessBuilder (WebM→WAV, 16kHz mono 16-bit) |
| **Error Handling**         | Log detailed errors, show user-friendly message         |
| **GPU Acceleration**       | Auto-detect CUDA with CPU fallback                      |
| **Language**               | Russian only (--language Russian flag)                  |
| **Task Type**              | Transcribe only (--task transcribe, no translation)     |
| **Punctuation**            | Preserve as-is (Whisper default)                        |
| **Case**                   | Keep Whisper default (preserve as-is)                   |
| **Max Duration**           | 30 seconds (as per current spec)                        |
| **Max File Size**          | 25 MB (as per current spec)                             |
| **Temporary Files**        | Project temp directory, overwrite strategy              |
| **Timeout**                | Configurable, default 120 seconds                       |
| **Class Name**             | WhisperSTTClient                                        |
| **Replacement Strategy**   | Complete replacement of OpenRouterSTTClient             |
| **Health Check**           | Command check only (whisper --help)                     |
| **Fallback on Error**      | Show message + manual input fallback                    |
| **Transcription Timer UI** | Do not show (but fix recording timer bug)               |
| **Configuration**          | application.conf (hocon format)                         |
| **Logging**                | Full DEBUG logging (command, exit code, stdout, stderr) |
| **Metrics**                | Execution time, file sizes                              |
| **Log File**               | Use existing logging system                             |
| **Hot Reload**             | Requires server restart                                 |
| **Tests**                  | Add test audio files for unit testing                   |
| **Documentation**          | Create separate WHISPER_SETUP.md                        |
| **Version Check**          | Log Whisper version on startup                          |
| **Encoding**               | UTF-8 (standard)                                        |
| **Empty Result**           | Return empty string + SUCCESS status                    |
| **ffmpeg Path**            | In PATH (ffmpeg command)                                |
| **Whisper Path**           | In PATH (whisper command)                               |
| **ffmpeg Error**           | Show conversion error to user                           |
| **Punctuation**            | Preserve (no --no-punctuation)                          |

---

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend (Compose Web)                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              MessageInput Component                  │   │
│  │  ┌─────────────┐ ┌─────────────────┐ ┌──────────┐  │   │
│  │  │ TextField   │ │ Mic Button      │ │ Timer    │  │   │
│  │  └─────────────┘ └─────────────────┘ └──────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────────┘
                     │ WebM blob (base64)
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                  Backend (Ktor Server)                       │
│                                                             │
│  POST /api/transcribe                                       │
│    ↓                                                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │         TranscribeRouting.kt                        │   │
│  │  - Validate WebM format                            │   │
│  │  - Check size ≤ 25MB                               │   │
│  │  - Decode base64 → bytes                           │   │
│  │  - Save to temp/ (overwrite)                       │   │
│  └─────────────────────────────────────────────────────┘   │
│    ↓                                                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │      WhisperSTTClient (replaces OpenRouterSTTClient)│   │
│  │                                                       │   │
│  │  Step 1: Convert WebM → WAV                          │   │
│  │    ffmpeg -i input.webm -ar 16000 -ac 1 -f wav ...   │   │
│  │                                                       │   │
│  │  Step 2: Transcribe with Whisper                     │   │
│  │    whisper input.wav \                               │   │
│  │      --model small \                                 │   │
│  │      --language Russian \                            │   │
│  │      --task transcribe \                             │   │
│  │      --output_format txt \                           │   │
│  │      --device cuda (auto-detect)                     │   │
│  │                                                       │   │
│  │  Step 3: Parse stdout (last line)                    │   │
│  │  Step 4: Clean up temp files (overwrite next time)   │   │
│  └─────────────────────────────────────────────────────┘   │
│    ↓                                                        │
│  TranscribeResponse → { text, status, duration }           │
└─────────────────────────────────────────────────────────────┘
```

---

## Data Flow

### 1. Audio Recording (Frontend)

```
User clicks Mic → MediaRecorder starts → 30s max duration
     ↓
User clicks Stop → MediaRecorder stops → WebM blob
     ↓
Blob → base64 encode → POST /api/transcribe
```

### 2. Backend Processing

```
POST /api/transcribe (WebM base64)
     ↓
Validate: format, size (100 bytes - 25MB)
     ↓
Decode base64 → byte[]
     ↓
Save to temp/recording_{timestamp}.webm
     ↓
Execute: ffmpeg -i temp/recording.webm -ar 16000 -ac 1 -f wav temp/recording.wav
     ↓
Execute: whisper temp/recording.wav --model small --language Russian --task transcribe --output_format txt
     ↓
Parse stdout (last line with timestamps) → extract text only
     ↓
Return: TranscribeResponse { text, status: "SUCCESS" }
```

---

## Configuration

### application.conf

```hocon
# Whisper STT Configuration
whisper {
  // Command to execute (must be in PATH)
  command = "whisper"

  // Model size: tiny, base, small, medium, large
  model = "small"

  // Language code (Russian for this project)
  language = "Russian"

  // Task type: transcribe (keep original) or translate (to English)
  task = "transcribe"

  // Output format: txt, json, srt, vtt
  outputFormat = "txt"

  // Device: cuda, cpu, or auto (default: auto)
  device = "auto"

  // Execution timeout in seconds (default: 120)
  timeout = 120

  // Temp directory for audio files (relative to project root)
  tempDir = "temp"

  // Max audio file size in bytes (25 MB)
  maxFileSize = 26214400

  // Max audio duration in seconds
  maxDuration = 30

  // Character encoding for stdout parsing (UTF-8)
  encoding = "UTF-8"
}

# ffmpeg Configuration
ffmpeg {
  // Command to execute (must be in PATH)
  command = "ffmpeg"

  // Input format (from browser)
  inputFormat = "webm"

  // Output format for Whisper
  outputFormat = "wav"

  // Audio parameters (16kHz, mono, 16-bit PCM - Whisper standard)
  sampleRate = 16000  // Hz
  channels = 1        // mono
  bitDepth = 16       // 16-bit PCM

  // Execution timeout in seconds
  timeout = 30
}
```

---

## API Specification

### POST /api/transcribe

**Request:**

```json
{
  "audioData": "base64_encoded_webm_audio",
  "format": "webm",
  "language": "Russian"
}
```

**Success Response (200):**

```json
{
  "text": "Распознанный текст сообщения",
  "status": "SUCCESS",
  "tokenUsage": null,
  "duration": 12.5,
  "cost": 0.0
}
```

**Error Responses:**

| Code | Error                      | Description                           |
|------|----------------------------|---------------------------------------|
| 400  | INVALID_AUDIO_FORMAT       | Unsupported audio format              |
| 400  | INVALID_BASE64             | Failed to decode base64               |
| 400  | AUDIO_TOO_SMALL            | Audio file must be at least 100 bytes |
| 413  | AUDIO_TOO_LARGE            | Maximum size is 25MB                  |
| 500  | FFMPEG_ERROR               | Audio conversion failed               |
| 500  | WHISPER_ERROR              | Transcription failed                  |
| 503  | STT_SERVICE_NOT_CONFIGURED | Whisper not found in PATH             |

---

## Implementation Details

### WhisperSTTClient

**Location:** `server/src/main/kotlin/ru/sber/cb/aichallenge_one/client/WhisperSTTClient.kt`

**Responsibilities:**

- Execute whisper command via ProcessBuilder
- Parse stdout for transcribed text
- Log detailed execution metrics
- Handle timeouts and errors

**Key Methods:**

```kotlin
class WhisperSTTClient(
    private val whisperConfig: WhisperConfig,
    private val ffmpegConfig: FfmpegConfig
) {
    /**
     * Transcribe audio file using local Whisper
     */
    suspend fun transcribe(
        audioData: String,
        format: String = "webm",
        language: String = "Russian"
    ): TranscribeResponse

    /**
     * Check if Whisper is available in PATH
     */
    suspend fun isAvailable(): Boolean

    /**
     * Get Whisper version
     */
    suspend fun getVersion(): String?

    private fun convertAudio(inputPath: String, outputPath: String): Boolean
    private fun executeWhisper(audioPath: String): String?
    private fun parseWhisperOutput(stdout: String): String
    private fun cleanupTempFiles(directory: String)
}
```

**Command Execution:**

```kotlin
private fun executeWhisper(audioPath: String): String? {
    val command = listOf(
        whisperConfig.command,
        audioPath,
        "--model", whisperConfig.model,
        "--language", whisperConfig.language,
        "--task", whisperConfig.task,
        "--output_format", whisperConfig.outputFormat,
        "--device", detectDevice() // cuda or cpu
    )

    val process = ProcessBuilder(command)
        .directory(File(whisperConfig.tempDir))
        .redirectErrorStream(true)
        .start()

    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor(whisperConfig.timeout, TimeUnit.SECONDS)

    if (exitCode == Int.MAX_VALUE) {
        process.destroyForcibly()
        throw TimeoutException("Whisper execution timed out")
    }

    logger.debug("Whisper exit code: $exitCode")
    logger.debug("Whisper stdout: $stdout")

    return parseWhisperOutput(stdout)
}
```

**Output Parsing:**

Whisper stdout format (with timestamps):

```
[00:00:00.000 --> 00:00:02.500]  Привет, это тестовое сообщение
[00:00:02.500 --> 00:00:05.000]  Распознавание речи работает
```

Parse logic:

```kotlin
private fun parseWhisperOutput(stdout: String): String {
    return stdout.lines()
        .filter { it.contains(" --> ") } // Lines with timestamps
        .map { line ->
            // Extract text after " --> HH:MM:SS.mmm] "
            line.substringAfter("] ").trim()
        }
        .joinToString(" ") // Combine all segments
        .ifEmpty { stdout.trim() } // Fallback to full output
}
```

### Audio Conversion (ffmpeg)

**Location:** `server/src/main/kotlin/ru/sber/cb/aichallenge_one/client/AudioConverter.kt`

**Command:**

```bash
ffmpeg -i input.webm -ar 16000 -ac 1 -f wav output.wav
```

**Parameters:**

- `-ar 16000`: Sample rate 16kHz (Whisper optimal)
- `-ac 1`: Mono channel
- `-f wav`: WAV format output
- `-y`: Overwrite output file

**Implementation:**

```kotlin
private fun convertAudio(inputPath: String, outputPath: String): Boolean {
    val command = listOf(
        ffmpegConfig.command,
        "-i", inputPath,
        "-ar", ffmpegConfig.sampleRate.toString(),
        "-ac", ffmpegConfig.channels.toString(),
        "-f", ffmpegConfig.outputFormat,
        "-y", // Overwrite
        outputPath
    )

    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()

    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor(ffmpegConfig.timeout, TimeUnit.SECONDS)

    logger.debug("ffmpeg exit code: $exitCode, stdout: $stdout")

    return exitCode == 0
}
```

### GPU Detection

**Auto-detection Logic:**

```kotlin
private fun detectDevice(): String {
    return when (whisperConfig.device.lowercase()) {
        "auto" -> {
            // Try to detect CUDA
            try {
                val process = ProcessBuilder("nvidia-smi").start()
                if (process.waitFor() == 0) "cuda" else "cpu"
            } catch (e: Exception) {
                logger.warn("CUDA detection failed, using CPU: ${e.message}")
                "cpu"
            }
        }
        else -> whisperConfig.device
    }
}
```

### Health Check

**Implementation:**

```kotlin
suspend fun isAvailable(): Boolean {
    return try {
        val process = ProcessBuilder(whisperConfig.command, "--help")
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor(5, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().use { it.readText() }

        if (exitCode == 0 && output.contains("whisper")) {
            logger.info("Whisper health check passed")
            true
        } else {
            logger.warn("Whisper health check failed: exit code=$exitCode")
            false
        }
    } catch (e: Exception) {
        logger.error("Whisper health check error", e)
        false
    }
}

suspend fun getVersion(): String? {
    return try {
        val process = ProcessBuilder(whisperConfig.command, "--version")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        // Whisper doesn't have --version, extract from help output
        output.lines()
            .find { it.contains("version", ignoreCase = true) }
            ?.trim()
    } catch (e: Exception) {
        logger.warn("Failed to get Whisper version", e)
        null
    }
}
```

---

## Error Handling

### Error Categories

| Error Type               | Detection                     | Response to User                                                                   |
|--------------------------|-------------------------------|------------------------------------------------------------------------------------|
| **Whisper not in PATH**  | ProcessBuilder fails to start | "Сервис распознавания недоступен. Установите Whisper (pip install openai-whisper)" |
| **ffmpeg not in PATH**   | ProcessBuilder fails to start | "Ошибка конвертации аудио. Установите ffmpeg"                                      |
| **Invalid audio format** | ffmpeg exit code != 0         | "Неверный формат аудио. Попробуйте записать ещё раз"                               |
| **Audio too small**      | < 100 bytes                   | "Аудио слишком короткое. Запишите сообщение длиннее"                               |
| **Audio too large**      | > 25MB                        | "Аудио слишком большое. Максимум 30 секунд записи"                                 |
| **Whisper timeout**      | waitFor() timeout             | "Распознавание заняло слишком много времени. Попробуйте короткое сообщение"        |
| **Empty transcription**  | Whisper returns empty text    | "Не удалось распознать речь. Попробуйте ещё раз или введите текст вручную"         |
| **Conversion error**     | ffmpeg fails                  | "Ошибка обработки аудио. Попробуйте другой браузер или введите текст вручную"      |

### Logging Strategy

**DEBUG Level (Full Logging):**

```kotlin
logger.debug("=== Whisper Execution ===")
logger.debug("Command: ${command.joinToString(" ")}")
logger.debug("Working directory: ${whisperConfig.tempDir}")
logger.debug("Exit code: $exitCode")
logger.debug("Stdout: $stdout")
logger.debug("Stderr: $stderr")
logger.debug("Execution time: ${executionTime}ms")
logger.debug("File size: ${File(audioPath).length()} bytes")
logger.debug("========================")
```

**Metrics Logging:**

```kotlin
logger.info("Whisper metrics: duration=${audioDuration}s, processingTime=${processingTime}ms, " +
    "inputSize=${inputBytes} bytes, outputSize=${outputBytes} bytes")
```

---

## Temp File Management

### Directory Structure

```
AiChallenge_One/
├── temp/                          # Project temp directory
│   ├── recording_1706384000.webm  # Overwritten on next request
│   ├── recording_1706384000.wav
│   └── recording_1706384000.txt   # Whisper output
└── server/
    └── src/main/resources/
        └── application.conf
```

### Cleanup Strategy

**Overwrite (Reuse):**

```kotlin
private fun getTempFilePath(extension: String): String {
    val timestamp = System.currentTimeMillis() / 1000 // Unique per second
    return "${whisperConfig.tempDir}/recording_$timestamp.$extension"
}
```

**No Deletion:** Files are overwritten on next request (simpler, no cleanup overhead)

**Optional Cleanup (Dev Mode):**

```kotlin
// Can be called manually via endpoint or on startup
fun cleanupOldFiles(maxAgeMs: Long = 24 * 60 * 60 * 1000) { // 24 hours
    val tempDir = File(whisperConfig.tempDir)
    tempDir.listFiles()?.forEach { file ->
        if (System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
            file.delete()
            logger.debug("Deleted old temp file: ${file.name}")
        }
    }
}
```

---

## UI/UX Changes

### Fix Recording Timer Bug

**Current Issue:** Timer shows 00:00 during recording

**Solution:**

```kotlin
// composeApp/src/webMain/kotlin/ru/sber/cb/aichallenge_one/audio/AudioRecorder.kt

@OptIn(ExperimentalTime::class)
class AudioRecorderImpl(
    private val callbacks: AudioRecorderCallbacks
) : AudioRecorder {

    private var mediaRecorder: MediaRecorderAPI.MediaRecorder? = null
    private var startTime: Long? = null
    private val timerUpdater = Timer()

    override fun startRecording(): Boolean {
        startTime = System.currentTimeMillis()
        startTimerUpdates()
        // ... rest of recording logic
    }

    private fun startTimerUpdates() {
        timerUpdater.schedule(100) { // Update every 100ms
            val currentDuration = getCurrentDuration()
            // Emit duration through callback
        }
    }

    override fun getCurrentDuration(): Long {
        return startTime?.let { System.currentTimeMillis() - it } ?: 0L
    }
}
```

### Transcription State

**No Processing Timer:** Do not show transcription time (as per requirements)

**Visual Feedback:**

```
Recording:     [🔴] 00:15  (animated pulse)
Processing:    [⏳] Распознавание...  (spinner)
Success:       [✅] Text sent to LLM
Error:         [❌] Toast: "Не удалось распознать речь"
```

---

## Dependency Injection

### AppModule.kt Changes

**Remove OpenRouterSTTClient:**

```kotlin
// DELETE THIS BLOCK:
// OpenRouter STT Client for audio transcription (optional)
single {
    if (openAIBaseUrl != null && openAIApiKey != null) {
        OpenRouterSTTClient(
            httpClient = get(org.koin.core.qualifier.named("openai")),
            baseUrl = openAIBaseUrl,
            apiKey = openAIApiKey,
            model = "whisper-1"
        )
    } else {
        null
    }
}
```

**Add WhisperSTTClient:**

```kotlin
// Whisper STT Client (local)
single {
    WhisperSTTClient(
        whisperConfig = WhisperConfig(
            command = "whisper",
            model = "small",
            language = "Russian",
            task = "transcribe",
            outputFormat = "txt",
            device = "auto",
            timeout = 120,
            tempDir = "temp",
            maxFileSize = 25 * 1024 * 1024,
            maxDuration = 30,
            encoding = "UTF-8"
        ),
        ffmpegConfig = FfmpegConfig(
            command = "ffmpeg",
            inputFormat = "webm",
            outputFormat = "wav",
            sampleRate = 16000,
            channels = 1,
            bitDepth = 16,
            timeout = 30
        )
    )
}
```

---

## Testing

### Test Audio Files

**Location:** `server/src/test/resources/audio/`

**Files:**

- `test_russian_5s.wav` - 5 seconds Russian speech
- `test_russian_empty.wav` - Silence/empty audio
- `test_russian_30s.wav` - Max duration (30 seconds)

**Unit Test Example:**

```kotlin
class WhisperSTTClientTest {

    @Test
    fun `should transcribe Russian audio correctly`() {
        val client = WhisperSTTClient(testWhisperConfig, testFfmpegConfig)
        val audioData = loadTestAudio("test_russian_5s.wav")

        val response = runBlocking {
            client.transcribe(audioData, "wav", "Russian")
        }

        assertEquals("SUCCESS", response.status)
        assertTrue(response.text.isNotBlank())
        assertTrue(response.text.contains("привет", ignoreCase = true))
    }

    @Test
    fun `should handle empty audio gracefully`() {
        val client = WhisperSTTClient(testWhisperConfig, testFfmpegConfig)
        val audioData = loadTestAudio("test_russian_empty.wav")

        val response = runBlocking {
            client.transcribe(audioData, "wav", "Russian")
        }

        assertEquals("SUCCESS", response.status)
        assertEquals("", response.text) // Empty text is valid
    }

    @Test
    fun `should timeout on long audio`() {
        val client = WhisperSTTClient(
            testWhisperConfig.copy(timeout = 1), // 1 second timeout
            testFfmpegConfig
        )
        val audioData = loadTestAudio("test_russian_30s.wav")

        val response = runBlocking {
            client.transcribe(audioData, "wav", "Russian")
        }

        assertEquals("ERROR", response.status)
        assertTrue(response.error?.contains("timeout") == true)
    }
}
```

---

## Performance Considerations

### Resource Requirements

**Whisper small model (244M):**

- RAM: ~2GB
- CPU: Any modern CPU (4+ cores recommended)
- GPU: Optional (CUDA 11.x+)
- Storage: ~500MB for model files

**Expected Performance (RTF - Real-Time Factor):**

| Configuration | RTF (Processing Time / Audio Duration)  |
|---------------|-----------------------------------------|
| CPU (4 cores) | 5-10x (30s audio → 150-300s processing) |
| GPU (CUDA)    | 0.1-0.5x (30s audio → 3-15s processing) |

### Optimization Opportunities

1. **Use faster-whisper:** CTranslate2 optimized version (3-4x faster, less memory)
2. **Use whisper.cpp:** C++ port with maximum speed
3. **Batch processing:** Process multiple audio files in parallel (if needed in future)
4. **Model caching:** Keep model in memory between requests (Whisper default behavior)

---

## Documentation

### WHISPER_SETUP.md

**Create:** `documentation/WHISPER_SETUP.md`

**Content:**

```markdown
# Whisper Setup Guide

## Prerequisites

### 1. Install Python 3.8+

```bash
# Windows (Chocolatey)
choco install python

# macOS (Homebrew)
brew install python@3.11

# Linux (Ubuntu/Debian)
sudo apt install python3.11
```

### 2. Install Whisper

```bash
pip install openai-whisper
```

### 3. Install ffmpeg

**Windows:**

```bash
choco install ffmpeg
```

**macOS:**

```bash
brew install ffmpeg
```

**Linux:**

```bash
sudo apt install ffmpeg
```

### 4. Download Whisper Model (auto-downloaded on first use)

```bash
whisper --model small
# Model will be downloaded to ~/.cache/whisper or %USERPROFILE%\.cache\whisper
```

### 5. Verify Installation

```bash
whisper --help
ffmpeg -version
```

### 6. (Optional) GPU Acceleration

Install CUDA Toolkit 11.x or 12.x:
https://developer.nvidia.com/cuda-downloads

Install PyTorch with CUDA support:

```bash
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
```

## Testing

Test Whisper with a sample audio file:

```bash
whisper test_audio.wav --model small --language Russian
```

## Troubleshooting

### "whisper: command not found"

- Add Python Scripts to PATH: `%APPDATA%\Python\Python311\Scripts`
- Or use full path: `python -m whisper ...`

### "ffmpeg: command not found"

- Install ffmpeg (see step 3)
- Restart terminal after installation

### CUDA not available

- Check: `nvidia-smi`
- Install CUDA Toolkit
- Reinstall PyTorch with CUDA support

## Model Details

| Model  | Parameters | English-only | Multilingual | Req. VRAM | Speed |
|--------|------------|--------------|--------------|-----------|-------|
| tiny   | 39 M       | Yes          | Yes          | ~1 GB     | ~32x  |
| base   | 74 M       | Yes          | Yes          | ~1 GB     | ~16x  |
| small  | 244 M      | Yes          | Yes          | ~2 GB     | ~6x   |
| medium | 769 M      | No           | Yes          | ~5 GB     | ~2x   |
| large  | 1550 M     | No           | Yes          | ~10 GB    | 1x    |

## References

- [OpenAI Whisper GitHub](https://github.com/openai/whisper)
- [Whisper Model Card](https://github.com/openai/whisper/blob/main/model-card.md)
- [ffmpeg Documentation](https://ffmpeg.org/documentation.html)

```

---

## Migration Checklist

### Code Changes

- [ ] Delete `OpenRouterSTTClient.kt`
- [ ] Create `WhisperSTTClient.kt`
- [ ] Create `AudioConverter.kt` (ffmpeg wrapper)
- [ ] Create `WhisperConfig.kt` (data class)
- [ ] Create `FfmpegConfig.kt` (data class)
- [ ] Update `AppModule.kt` DI configuration
- [ ] Update `TranscribeRouting.kt` (remove OpenRouter references)
- [ ] Fix recording timer bug in `AudioRecorder.kt`

### Configuration

- [ ] Add `whisper` section to `application.conf`
- [ ] Add `ffmpeg` section to `application.conf`
- [ ] Create `temp/` directory in project root
- [ ] Update `.gitignore` to exclude `temp/`

### Testing

- [ ] Create test audio files in `server/src/test/resources/audio/`
- [ ] Write unit tests for `WhisperSTTClient`
- [ ] Write unit tests for `AudioConverter`
- [ ] Integration test for `/api/transcribe` endpoint

### Documentation

- [ ] Create `WHISPER_SETUP.md`
- [ ] Update `CLAUDE.md` with Whisper integration details
- [ ] Update `voice-input-feature.md` specification

### Validation

- [ ] Test Whisper installation: `whisper --help`
- [ ] Test ffmpeg installation: `ffmpeg -version`
- [ ] Test full flow: Record → Transcribe → Response
- [ ] Test error cases: Invalid audio, timeout, empty result
- [ ] Test GPU detection (if available)
- [ ] Performance test: 30s audio transcription time

---

## Success Criteria

✅ User can record voice message (max 30 seconds)
✅ Audio is automatically transcribed using local Whisper
✅ Transcribed text is automatically sent to LLM
✅ Russian language is recognized correctly
✅ Recording timer shows correct duration
✅ Error handling shows user-friendly messages
✅ Whisper logs version on server startup
✅ Health check endpoint verifies Whisper availability
✅ GPU acceleration is used when available (with CPU fallback)
✅ Temporary files are managed (overwrite strategy)
✅ Full DEBUG logging for troubleshooting
✅ Documentation explains Whisper setup

---

## Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Slow transcription on CPU** | Poor UX (30s audio → 5min wait) | High | Document GPU requirement, recommend faster-whisper |
| **Whisper not installed** | Feature broken | Medium | Clear error messages, WHISPER_SETUP.md |
| **ffmpeg not installed** | Feature broken | Medium | Clear error messages, WHISPER_SETUP.md |
| **GPU detection fails** | Slower processing | Low | Auto-fallback to CPU with warning log |
| **Encoding issues on Windows** | Garbled Cyrillic text | Low | Force UTF-8, test on Windows |
| **Process hangs** | Server thread blocked | Low | Configurable timeout (120s default) |
| **Temp files fill disk** | Disk space exhausted | Low | Overwrite strategy (no accumulation) |
| **Concurrent requests** | Race conditions, file conflicts | Low | Single user assumption, document limitation |

---

## Future Enhancements

1. **Faster-Whisper Integration:** Replace openai-whisper with faster-whisper (3-4x speedup)
2. **Whisper.cpp:** Maximum performance with C++ port
3. **Concurrent Processing:** Queue system for multiple users
4. **VAD (Voice Activity Detection):** Auto-stop recording on silence
5. **Streaming Transcription:** Real-time results during recording
6. **Language Detection:** Auto-detect language instead of hardcoding Russian
7. **Model Selection:** Allow users to choose model (tiny/base/small)
8. **Caching:** Cache transcription results for identical audio
9. **Docker Support:** Containerize Whisper for easy deployment
10. **Batch Processing:** Process multiple audio files in parallel

---

## References

- [OpenAI Whisper GitHub](https://github.com/openai/whisper)
- [Whisper Model Card](https://github.com/openai/whisper/blob/main/model-card.md)
- [ffmpeg Documentation](https://ffmpeg.org/documentation.html)
- [ProcessBuilder JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/lang/ProcessBuilder.html)
- [Kotlin Process](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.process/index.html)

---

**End of Specification**
