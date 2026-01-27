# Test Audio Files

This directory contains test audio files for Whisper STT unit testing.

## Test Files

### Required Test Files

- `test_russian_5s.wav` - 5 seconds Russian speech (for basic transcription test)
- `test_russian_empty.wav` - Silence/empty audio (for edge case testing)
- `test_russian_30s.wav` - Max duration (30 seconds) (for timeout testing)

### Optional Test Files

- `test_english_5s.wav` - English speech for language detection
- `test_mixed_10s.wav` - Mixed language audio
- `test_noise_5s.wav` - Audio with background noise

## How to Create Test Files

### Option 1: Use Online TTS

1. Go to https://ttsmp3.com/ or similar TTS service
2. Select Russian language
3. Generate and download audio
4. Convert to WAV (16kHz, mono, 16-bit):
   ```bash
   ffmpeg -i input.mp3 -ar 16000 -ac 1 -f wav test_russian_5s.wav
   ```

### Option 2: Record with Whisper

```bash
# Record 5 seconds of audio
ffmpeg -f dshow -i audio="Microphone" -t 5 test_russian_5s.wav

# Convert to required format
ffmpeg -i test_russian_5s.wav -ar 16000 -ac 1 -f wav test_russian_5s_16k.wav
```

### Option 3: Use Existing Audio

If you have existing Russian audio files, convert them:

```bash
ffmpeg -i input.mp3 -ar 16000 -ac 1 -f wav test_russian_5s.wav
```

## Audio Specifications

All test audio files must meet these requirements:

- **Format:** WAV
- **Sample Rate:** 16000 Hz (16kHz)
- **Channels:** 1 (mono)
- **Bit Depth:** 16-bit PCM
- **Duration:** Varies (5s, 10s, 30s)
- **Content:** Russian speech

## Verification

Verify test files with ffprobe:

```bash
ffprobe test_russian_5s.wav

# Expected output:
# - Sample rate: 16000 Hz
# - Channels: 1
# - Bit depth: 16-bit
```

## Usage in Tests

Example unit test:

```kotlin
@Test
fun `should transcribe Russian audio correctly`() {
    val audioData = File("server/src/test/resources/audio/test_russian_5s.wav")
        .readBytes()
        .let { Base64.getEncoder().encodeToString(it) }

    val response = runBlocking {
        whisperClient.transcribe(audioData, "wav", "Russian")
    }

    assertEquals("SUCCESS", response.status)
    assertTrue(response.text.isNotBlank())
}
```

## Notes

- Test files should be small (ideally < 1MB)
- Use clear, high-quality audio for reliable testing
- Include common Russian phrases for better test coverage
- Test files should not contain copyrighted material

---

**TODO:** Add actual test audio files for complete test coverage.
