package ru.sber.cb.aichallenge_one.config

/**
 * Configuration for OpenAI Whisper STT (Speech-to-Text) integration
 *
 * This configuration controls the local Whisper installation for audio transcription.
 * Whisper is executed via ProcessBuilder with support for GPU acceleration.
 *
 * @property command Command to execute (must be in PATH, typically "whisper")
 * @property model Model size: tiny, base, small, medium, large (default: "small")
 * @property language Language code for transcription (default: "Russian")
 * @property task Task type: "transcribe" (keep original) or "translate" (to English)
 * @property outputFormat Output format: txt, json, srt, vtt (default: "txt")
 * @property device Device: cuda, cpu, or auto for GPU auto-detection (default: "auto")
 * @property timeout Execution timeout in seconds (default: 120)
 * @property tempDir Temp directory for audio files (relative to project root, default: "temp")
 * @property maxFileSize Max audio file size in bytes (default: 25MB = 26214400)
 * @property maxDuration Max audio duration in seconds (default: 30)
 * @property encoding Character encoding for stdout parsing (default: "UTF-8")
 */
data class WhisperConfig(
    val command: String = "whisper",
    val model: String = "small",
    val language: String = "Russian",
    val task: String = "transcribe",
    val outputFormat: String = "txt",
    val device: String = "auto",
    val timeout: Int = 120,
    val tempDir: String = "temp",
    val maxFileSize: Long = 25 * 1024 * 1024, // 25 MB
    val maxDuration: Int = 30,
    val encoding: String = "UTF-8"
) {
    /**
     * Validates the configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate() {
        require(command.isNotBlank()) { "Whisper command cannot be blank" }
        require(model in listOf("tiny", "base", "small", "medium", "large")) {
            "Invalid model: $model. Must be one of: tiny, base, small, medium, large"
        }
        require(task in listOf("transcribe", "translate")) {
            "Invalid task: $task. Must be one of: transcribe, translate"
        }
        require(outputFormat in listOf("txt", "json", "srt", "vtt")) {
            "Invalid output format: $outputFormat. Must be one of: txt, json, srt, vtt"
        }
        require(device in listOf("cuda", "cpu", "auto")) {
            "Invalid device: $device. Must be one of: cuda, cpu, auto"
        }
        require(timeout > 0) { "Timeout must be positive: $timeout" }
        require(maxFileSize > 0) { "Max file size must be positive: $maxFileSize" }
        require(maxDuration > 0) { "Max duration must be positive: $maxDuration" }
        require(encoding.isNotBlank()) { "Encoding cannot be blank" }
    }

    /**
     * Creates a copy of this config with the specified parameters loaded from environment variables
     * if they exist. This allows overriding configuration via environment variables.
     *
     * Environment variable format: WHISPER_<PROPERTY_NAME>
     * Example: WHISPER_MODEL, WHISPER_TIMEOUT, etc.
     */
    fun withEnvOverrides(): WhisperConfig {
        return this.copy(
            command = System.getenv("WHISPER_COMMAND") ?: this.command,
            model = System.getenv("WHISPER_MODEL") ?: this.model,
            language = System.getenv("WHISPER_LANGUAGE") ?: this.language,
            task = System.getenv("WHISPER_TASK") ?: this.task,
            outputFormat = System.getenv("WHISPER_OUTPUT_FORMAT") ?: this.outputFormat,
            device = System.getenv("WHISPER_DEVICE") ?: this.device,
            timeout = System.getenv("WHISPER_TIMEOUT")?.toIntOrNull() ?: this.timeout,
            tempDir = System.getenv("WHISPER_TEMP_DIR") ?: this.tempDir,
            maxFileSize = System.getenv("WHISPER_MAX_FILE_SIZE")?.toLongOrNull() ?: this.maxFileSize,
            maxDuration = System.getenv("WHISPER_MAX_DURATION")?.toIntOrNull() ?: this.maxDuration,
            encoding = System.getenv("WHISPER_ENCODING") ?: this.encoding
        )
    }

    companion object {
        /**
         * Model details for reference
         */
        val MODELS = mapOf(
            "tiny" to ModelInfo(39, "~1 GB", "~32x", "39 M"),
            "base" to ModelInfo(74, "~1 GB", "~16x", "74 M"),
            "small" to ModelInfo(244, "~2 GB", "~6x", "244 M"),
            "medium" to ModelInfo(769, "~5 GB", "~2x", "769 M"),
            "large" to ModelInfo(1550, "~10 GB", "1x", "1550 M")
        )

        data class ModelInfo(
            val parameters: Int, // in millions
            val requiredVRAM: String,
            val speed: String,
            val size: String
        )
    }
}
