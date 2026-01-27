# Whisper Setup Guide

This guide provides step-by-step instructions for installing and configuring OpenAI Whisper for local speech-to-text
functionality.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Model Details](#model-details)
- [Performance](#performance)
- [References](#references)

---

## Prerequisites

### System Requirements

**Minimum Requirements:**

- CPU: Any modern 4-core processor
- RAM: 4 GB (for `small` model)
- Storage: 2 GB free space
- Python: 3.8 or higher

**Recommended Requirements (for GPU acceleration):**

- GPU: NVIDIA GPU with CUDA 11.x+ support
- VRAM: 2 GB (for `small` model)
- RAM: 8 GB or more
- CUDA Toolkit: 11.x or 12.x

### Supported Platforms

- **Windows:** 10/11
- **macOS:** 10.15 (Catalina) or later
- **Linux:** Ubuntu 18.04+, Debian 10+, or similar

---

## Installation

### Step 1: Install Python 3.8+

#### Windows

```bash
# Using Chocolatey (recommended)
choco install python

# Or download from https://www.python.org/downloads/
# Make sure to check "Add Python to PATH" during installation
```

#### macOS

```bash
# Using Homebrew
brew install python@3.11

# Verify installation
python3 --version
```

#### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install python3.11 python3-pip python3-venv

# Verify installation
python3 --version
```

### Step 2: Install Whisper

```bash
# Install OpenAI Whisper via pip
pip install openai-whisper

# Verify installation
whisper --help
```

**Note:** If you get a "command not found" error, make sure Python Scripts are in your PATH:

- **Windows:** Add `%APPDATA%\Python\Python311\Scripts` to PATH
- **macOS/Linux:** Use full path `python3 -m whisper ...` or create alias

### Step 3: Install ffmpeg

Whisper requires ffmpeg for audio processing.

#### Windows

```bash
# Using Chocolatey (recommended)
choco install ffmpeg

# Or download from https://ffmpeg.org/download.html
# Extract and add to PATH
```

#### macOS

```bash
brew install ffmpeg
```

#### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install ffmpeg
```

### Step 4: Download Whisper Model (Optional)

Models are auto-downloaded on first use, but you can pre-download:

```bash
# Download small model (244MB)
whisper --model small

# Model will be cached in:
# - Windows: %USERPROFILE%\.cache\whisper
# - macOS/Linux: ~/.cache/whisper
```

### Step 5: (Optional) GPU Acceleration

If you have an NVIDIA GPU, install CUDA Toolkit and PyTorch with CUDA support for faster transcription.

#### Install CUDA Toolkit

Download from: https://developer.nvidia.com/cuda-downloads

#### Install PyTorch with CUDA

```bash
# For CUDA 11.8
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118

# For CUDA 12.1
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121

# Verify CUDA is available
python3 -c "import torch; print(torch.cuda.is_available())"
```

---

## Configuration

### Environment Variables

Whisper configuration can be customized via environment variables in `application.conf`:

```bash
# Whisper Command
WHISPER_COMMAND=whisper

# Model Selection (tiny, base, small, medium, large)
WHISPER_MODEL=small

# Language
WHISPER_LANGUAGE=Russian

# Task Type (transcribe or translate)
WHISPER_TASK=transcribe

# Device (cuda, cpu, auto)
WHISPER_DEVICE=auto

# Timeout (seconds)
WHISPER_TIMEOUT=120

# Temp Directory
WHISPER_TEMP_DIR=temp

# Max File Size (bytes, default: 25MB)
WHISPER_MAX_FILE_SIZE=26214400

# Max Duration (seconds, default: 30)
WHISPER_MAX_DURATION=30
```

### application.conf

Configuration is loaded from `server/src/main/resources/application.conf`:

```hocon
whisper {
  command = "whisper"
  model = "small"
  language = "Russian"
  task = "transcribe"
  outputFormat = "txt"
  device = "auto"
  timeout = 120
  tempDir = "temp"
  maxFileSize = 26214400
  maxDuration = 30
  encoding = "UTF-8"
}
```

---

## Testing

### Test Whisper Installation

```bash
# Test Whisper help
whisper --help

# Test with a sample audio file (if you have one)
whisper test_audio.wav --model small --language Russian
```

### Test ffmpeg Installation

```bash
ffmpeg -version
```

### Test Server Integration

1. Start the server:
   ```bash
   ./gradlew.bat :server:run
   ```

2. Check Whisper health:
   ```bash
   curl http://localhost:8080/api/transcribe/health
   ```

3. Expected response:
   ```json
   {
     "available": true,
     "message": "STT service is available"
   }
   ```

---

## Troubleshooting

### "whisper: command not found"

**Problem:** Whisper command is not found in PATH.

**Solutions:**

1. **Check Python installation:**
   ```bash
   python --version
   pip --version
   ```

2. **Add Python Scripts to PATH (Windows):**
    - Find Python Scripts directory: `%APPDATA%\Python\Python311\Scripts`
    - Add to PATH: System Properties → Environment Variables → Path
    - Restart terminal

3. **Use full path or python -m:**
   ```bash
   # Full path
   C:\Python311\Scripts\whisper --help

   # Or use python -m
   python -m whisper --help
   ```

4. **Set WHISPER_COMMAND environment variable:**
   ```bash
   # Windows
   set WHISPER_COMMAND=python -m whisper

   # macOS/Linux
   export WHISPER_COMMAND="python -m whisper"
   ```

### "ffmpeg: command not found"

**Problem:** ffmpeg command is not found in PATH.

**Solutions:**

1. **Install ffmpeg (if not installed):**
    - Windows: `choco install ffmpeg`
    - macOS: `brew install ffmpeg`
    - Linux: `sudo apt install ffmpeg`

2. **Restart terminal after installation**

3. **Verify installation:**
   ```bash
   ffmpeg -version
   ```

### "CUDA not available"

**Problem:** Whisper is using CPU instead of GPU.

**Solutions:**

1. **Check NVIDIA GPU:**
   ```bash
   nvidia-smi
   ```

2. **Install CUDA Toolkit:**
    - Download from: https://developer.nvidia.com/cuda-downloads
    - Follow installation instructions

3. **Reinstall PyTorch with CUDA:**
   ```bash
   pip uninstall torch torchvision torchaudio
   pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
   ```

4. **Verify CUDA in PyTorch:**
   ```bash
   python3 -c "import torch; print(torch.cuda.is_available())"
   # Should print: True
   ```

5. **Force GPU usage:**
   ```bash
   set WHISPER_DEVICE=cuda
   ```

### "Model not found"

**Problem:** Whisper model is not downloaded.

**Solutions:**

1. **Check internet connection**

2. **Manually download model:**
   ```bash
   whisper --model small
   ```

3. **Check cache directory:**
    - Windows: `%USERPROFILE%\.cache\whisper`
    - macOS/Linux: `~/.cache/whisper`

4. **Clear cache and retry:**
   ```bash
   # Windows
   del /s /q %USERPROFILE%\.cache\whisper

   # macOS/Linux
   rm -rf ~/.cache/whisper
   ```

### "Permission denied" on temp directory

**Problem:** Whisper cannot write to temp directory.

**Solutions:**

1. **Create temp directory:**
   ```bash
   mkdir temp
   ```

2. **Check permissions:**
   ```bash
   # macOS/Linux
   chmod 755 temp

   # Windows: Ensure directory is writable
   ```

3. **Use absolute path in config:**
   ```bash
   WHISPER_TEMP_DIR=C:\path\to\temp
   ```

### Slow transcription on CPU

**Problem:** Transcription is very slow (30s audio → 5+ minutes).

**Solutions:**

1. **Use smaller model:**
   ```bash
   WHISPER_MODEL=tiny  # Fastest, less accurate
   WHISPER_MODEL=base   # Balanced
   ```

2. **Enable GPU acceleration** (see above)

3. **Use faster-whisper** (3-4x faster):
   ```bash
   pip install faster-whisper
   # Note: Requires code changes to use faster-whisper API
   ```

4. **Consider whisper.cpp** (maximum speed):
    - https://github.com/ggerganov/whisper.cpp

### Encoding issues (Cyrillic text)

**Problem:** Transcribed Russian text appears as garbled characters.

**Solutions:**

1. **Ensure UTF-8 encoding:**
   ```bash
   WHISPER_ENCODING=UTF-8
   ```

2. **Check terminal encoding:**
   ```bash
   # Windows
   chcp 65001

   # macOS/Linux
   export LANG=en_US.UTF-8
   ```

---

## Model Details

| Model  | Parameters | English-only | Multilingual | Req. VRAM | Speed | Size    |
|--------|------------|--------------|--------------|-----------|-------|---------|
| tiny   | 39 M       | Yes          | Yes          | ~1 GB     | ~32x  | ~75 MB  |
| base   | 74 M       | Yes          | Yes          | ~1 GB     | ~16x  | ~150 MB |
| small  | 244 M      | Yes          | Yes          | ~2 GB     | ~6x   | ~500 MB |
| medium | 769 M      | No           | Yes          | ~5 GB     | ~2x   | ~1.5 GB |
| large  | 1550 M     | No           | Yes          | ~10 GB    | 1x    | ~3 GB   |

**Recommendation:** Use `small` model for best balance of speed and accuracy for Russian language.

### Model Selection Guide

- **tiny:** Fastest, good for testing, lower accuracy
- **base:** Balanced speed/accuracy, good for production
- **small:** Recommended, good accuracy, reasonable speed
- **medium:** High accuracy, slower
- **large:** Best accuracy, slowest, requires more resources

---

## Performance

### Real-Time Factor (RTF)

RTF = Processing Time / Audio Duration

| Configuration        | RTF       | Example (30s audio)          |
|----------------------|-----------|------------------------------|
| CPU (4 cores)        | 5-10x     | 150-300 seconds (2.5-5 min)  |
| CPU (8 cores)        | 3-5x      | 90-150 seconds (1.5-2.5 min) |
| GPU (CUDA)           | 0.1-0.5x  | 3-15 seconds                 |
| GPU (faster-whisper) | 0.03-0.1x | 1-3 seconds                  |

### Optimization Tips

1. **Use GPU acceleration** (30-100x faster)
2. **Use smaller model** (tiny/base for speed)
3. **Use faster-whisper** (3-4x faster than openai-whisper)
4. **Limit audio duration** (30s default)
5. **Use SSD for temp files** (faster I/O)

---

## References

- [OpenAI Whisper GitHub](https://github.com/openai/whisper)
- [Whisper Model Card](https://github.com/openai/whisper/blob/main/model-card.md)
- [faster-whisper (CTranslate2)](https://github.com/guillaumekln/faster-whisper)
- [whisper.cpp (C++ port)](https://github.com/ggerganov/whisper.cpp)
- [ffmpeg Documentation](https://ffmpeg.org/documentation.html)
- [CUDA Toolkit Download](https://developer.nvidia.com/cuda-downloads)

---

## Quick Start Checklist

- [ ] Python 3.8+ installed
- [ ] Whisper installed (`pip install openai-whisper`)
- [ ] ffmpeg installed
- [ ] Whisper command works (`whisper --help`)
- [ ] ffmpeg command works (`ffmpeg -version`)
- [ ] Temp directory exists (`mkdir temp`)
- [ ] Server configured (`application.conf`)
- [ ] Health check passes (`curl /api/transcribe/health`)
- [ ] Test transcription works

---

**End of Guide**
