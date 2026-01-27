# Whisper STT Integration - Quick Reference

## Quick Setup (5 minutes)

### 1. Install Whisper

```bash
pip install openai-whisper
```

### 2. Install ffmpeg

```bash
# Windows
choco install ffmpeg

# macOS
brew install ffmpeg

# Linux
sudo apt install ffmpeg
```

### 3. Verify Installation

```bash
whisper --help
ffmpeg -version
```

### 4. Test Server

```bash
./gradlew.bat :server:run
curl http://localhost:8080/api/transcribe/health
```

---

## Configuration Files

### application.conf

Location: `server/src/main/resources/application.conf`

```hocon
whisper {
  command = "whisper"
  model = "small"        # tiny, base, small, medium, large
  language = "Russian"
  task = "transcribe"
  device = "auto"        # cuda, cpu, auto
  timeout = 120
  tempDir = "temp"
}

ffmpeg {
  command = "ffmpeg"
  sampleRate = 16000
  channels = 1
  bitDepth = 16
}
```

---

## Environment Variables

```bash
# Override model
WHISPER_MODEL=base

# Force GPU
WHISPER_DEVICE=cuda

# Custom temp directory
WHISPER_TEMP_DIR=/tmp/whisper
```

---

## API Usage

### Transcribe Audio

```bash
curl -X POST http://localhost:8080/api/transcribe \
  -H "Content-Type: application/json" \
  -d '{
    "audioData": "base64_encoded_webm_audio",
    "format": "webm",
    "language": "Russian"
  }'
```

### Response

```json
{
  "text": "Распознанный текст",
  "status": "SUCCESS",
  "duration": 3.5,
  "cost": 0.0
}
```

---

## Model Comparison

| Model  | Size   | VRAM  | Speed | Accuracy  |
|--------|--------|-------|-------|-----------|
| tiny   | 75 MB  | 1 GB  | 32x   | Low       |
| base   | 150 MB | 1 GB  | 16x   | Medium    |
| small  | 500 MB | 2 GB  | 6x    | High      |
| medium | 1.5 GB | 5 GB  | 2x    | Very High |
| large  | 3 GB   | 10 GB | 1x    | Best      |

**Recommendation:** `small` for production

---

## Common Issues

### whisper: command not found

```bash
# Add Python Scripts to PATH (Windows)
# Or use:
set WHISPER_COMMAND=python -m whisper
```

### ffmpeg: command not found

```bash
# Install ffmpeg
choco install ffmpeg  # Windows
brew install ffmpeg   # macOS
sudo apt install ffmpeg  # Linux
```

### Slow transcription

```bash
# Enable GPU
WHISPER_DEVICE=cuda

# Or use smaller model
WHISPER_MODEL=tiny
```

---

## File Locations

**Configuration:**

- `server/src/main/resources/application.conf`

**Source Code:**

- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/config/WhisperConfig.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/config/FfmpegConfig.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/client/WhisperSTTClient.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/client/AudioConverter.kt`

**Documentation:**

- `documentation/WHISPER_SETUP.md` - Full setup guide
- `documentation/specifications/local-whisper-integration.md` - Technical spec
- `WHISPER_INTEGRATION_SUMMARY.md` - Implementation summary

**Temp Files:**

- `temp/` - Audio conversion temp files (auto-overwritten)

---

## Performance

### CPU (4 cores)

- 30s audio → 150-300s (2.5-5 min)
- RTF: 5-10x

### GPU (CUDA)

- 30s audio → 3-15s
- RTF: 0.1-0.5x

**Tip:** Use GPU for production!

---

## Testing

### Create Test Audio

```bash
# Convert to required format
ffmpeg -i input.mp3 -ar 16000 -ac 1 -f wav test.wav
```

### Test Transcription

```bash
whisper test.wav --model small --language Russian
```

---

## Next Steps

1. ✅ Install Whisper and ffmpeg
2. ✅ Test server: `./gradlew.bat :server:run`
3. ✅ Check health: `curl /api/transcribe/health`
4. ✅ Test transcription with real audio
5. ✅ (Optional) Enable GPU acceleration
6. ✅ (Optional) Delete old OpenRouterSTTClient.kt

---

## Support

**Full Documentation:** `documentation/WHISPER_SETUP.md`
**Technical Spec:** `documentation/specifications/local-whisper-integration.md`
**Implementation Summary:** `WHISPER_INTEGRATION_SUMMARY.md`
