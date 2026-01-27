package ru.sber.cb.aichallenge_one.config

/**
 * Configuration for ffmpeg audio conversion
 *
 * This configuration controls the ffmpeg integration for converting audio formats
 * (primarily WebM from browser to WAV for Whisper processing).
 *
 * @property command Command to execute (must be in PATH, typically "ffmpeg")
 * @property inputFormat Input audio format from browser (default: "webm")
 * @property outputFormat Output audio format for Whisper (default: "wav")
 * @property sampleRate Sample rate in Hz (Whisper requires 16000 Hz)
 * @property channels Number of audio channels (1 = mono, required by Whisper)
 * @property bitDepth Audio bit depth (16-bit PCM, required by Whisper)
 * @property timeout Execution timeout in seconds (default: 30)
 */
data class FfmpegConfig(
    val command: String = "ffmpeg",
    val inputFormat: String = "webm",
    val outputFormat: String = "wav",
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val timeout: Int = 30
) {
    /**
     * Validates the configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate() {
        require(command.isNotBlank()) { "FFmpeg command cannot be blank" }
        require(inputFormat.isNotBlank()) { "Input format cannot be blank" }
        require(outputFormat.isNotBlank()) { "Output format cannot be blank" }
        require(sampleRate > 0) { "Sample rate must be positive: $sampleRate" }
        require(channels > 0) { "Channels must be positive: $channels" }
        require(bitDepth > 0) { "Bit depth must be positive: $bitDepth" }
        require(timeout > 0) { "Timeout must be positive: $timeout" }

        // Whisper-specific requirements
        require(sampleRate == 16000) { "Whisper requires 16000 Hz sample rate, got: $sampleRate" }
        require(channels == 1) { "Whisper requires mono audio (1 channel), got: $channels" }
        require(bitDepth == 16) { "Whisper requires 16-bit PCM, got: $bitDepth" }
    }

    /**
     * Creates a copy of this config with the specified parameters loaded from environment variables
     * if they exist. This allows overriding configuration via environment variables.
     *
     * Environment variable format: FFMPEG_<PROPERTY_NAME>
     * Example: FFMPEG_COMMAND, FFMPEG_TIMEOUT, etc.
     */
    fun withEnvOverrides(): FfmpegConfig {
        return this.copy(
            command = System.getenv("FFMPEG_COMMAND") ?: this.command,
            inputFormat = System.getenv("FFMPEG_INPUT_FORMAT") ?: this.inputFormat,
            outputFormat = System.getenv("FFMPEG_OUTPUT_FORMAT") ?: this.outputFormat,
            sampleRate = System.getenv("FFMPEG_SAMPLE_RATE")?.toIntOrNull() ?: this.sampleRate,
            channels = System.getenv("FFMPEG_CHANNELS")?.toIntOrNull() ?: this.channels,
            bitDepth = System.getenv("FFMPEG_BIT_DEPTH")?.toIntOrNull() ?: this.bitDepth,
            timeout = System.getenv("FFMPEG_TIMEOUT")?.toIntOrNull() ?: this.timeout
        )
    }

    /**
     * Builds the ffmpeg command arguments for audio conversion
     *
     * @param inputPath Path to input audio file
     * @param outputPath Path to output audio file
     * @return List of command arguments
     */
    fun buildCommand(inputPath: String, outputPath: String): List<String> {
        return listOf(
            command,
            "-i", inputPath,              // Input file
            "-ar", sampleRate.toString(), // Sample rate (16kHz for Whisper)
            "-ac", channels.toString(),   // Audio channels (1 = mono for Whisper)
            "-f", outputFormat,           // Output format (wav)
            "-y",                          // Overwrite output file
            outputPath
        )
    }

    companion object {
        /**
         * Standard audio formats supported by ffmpeg
         */
        val SUPPORTED_INPUT_FORMATS = listOf("webm", "wav", "mp3", "ogg", "flac", "m4a", "mp4", "mpeg")

        /**
         * Validate if input format is supported
         */
        fun isInputFormatSupported(format: String): Boolean {
            return format.lowercase() in SUPPORTED_INPUT_FORMATS
        }
    }
}
