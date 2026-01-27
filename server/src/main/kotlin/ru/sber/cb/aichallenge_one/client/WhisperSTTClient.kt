package ru.sber.cb.aichallenge_one.client

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.config.FfmpegConfig
import ru.sber.cb.aichallenge_one.config.WhisperConfig
import ru.sber.cb.aichallenge_one.models.TranscribeResponse
import java.io.File
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Local Whisper STT (Speech-to-Text) Client
 *
 * This client integrates OpenAI's Whisper for local audio transcription.
 * It uses ProcessBuilder to execute the whisper command with support for:
 * - GPU auto-detection with CPU fallback
 * - Audio format conversion (WebM → WAV via ffmpeg)
 * - Detailed logging for troubleshooting
 * - Configurable timeouts and model selection
 *
 * @property whisperConfig Whisper configuration
 * @property ffmpegConfig FFmpeg configuration for audio conversion
 */
class WhisperSTTClient(
    private val whisperConfig: WhisperConfig,
    private val ffmpegConfig: FfmpegConfig
) {
    private val logger = LoggerFactory.getLogger(WhisperSTTClient::class.java)
    private val audioConverter = AudioConverter(ffmpegConfig)

    init {
        whisperConfig.validate()
        ffmpegConfig.validate()
        logger.info(
            "WhisperSTTClient initialized with: model=${whisperConfig.model}, " +
                    "language=${whisperConfig.language}, device=${whisperConfig.device}"
        )
    }

    /**
     * Transcribe audio data to text using local Whisper
     *
     * @param audioData Base64 encoded audio data
     * @param format Audio format (webm, wav, mp3, etc.)
     * @param language Language code (default: "Russian")
     * @return TranscribeResponse with transcribed text or error
     */
    suspend fun transcribe(
        audioData: String,
        format: String = "webm",
        language: String = "Russian"
    ): TranscribeResponse {
        val startTime = System.currentTimeMillis()

        logger.info("=== Starting Transcription ===")
        logger.info("Format: $format, Language: $language")

        try {
            // Step 1: Decode base64 audio data
            val audioBytes = try {
                Base64.getDecoder().decode(audioData)
            } catch (e: Exception) {
                logger.error("Failed to decode base64 audio data", e)
                return TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = null,
                    duration = 0.0,
                    cost = 0.0,
                    error = "INVALID_BASE64: ${e.message}"
                )
            }

            // Step 2: Validate audio size
            if (audioBytes.size < 100) {
                logger.error("Audio file too small: ${audioBytes.size} bytes")
                return TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = null,
                    duration = 0.0,
                    cost = 0.0,
                    error = "AUDIO_TOO_SMALL: Audio file must be at least 100 bytes"
                )
            }

            if (audioBytes.size > whisperConfig.maxFileSize) {
                logger.error("Audio file too large: ${audioBytes.size} bytes")
                return TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = null,
                    duration = 0.0,
                    cost = 0.0,
                    error = "AUDIO_TOO_LARGE: Maximum size is ${whisperConfig.maxFileSize / (1024 * 1024)}MB"
                )
            }

            logger.info("Audio size: ${audioBytes.size} bytes (~${audioBytes.size / 1024}KB)")

            // Step 3: Create temp directory
            val tempDir = File(whisperConfig.tempDir)
            if (!tempDir.exists()) {
                tempDir.mkdirs()
                logger.debug("Created temp directory: ${tempDir.absolutePath}")
            }

            // Step 4: Generate unique file paths
            val timestamp = System.currentTimeMillis()
            val inputPath = "${tempDir.absolutePath}/recording_$timestamp.$format"
            val wavPath = "${tempDir.absolutePath}/recording_$timestamp.wav"

            try {
                // Step 5: Save audio data to temp file
                File(inputPath).writeBytes(audioBytes)
                logger.debug("Saved audio to: $inputPath")

                // Step 6: Convert audio to WAV format (if not already WAV)
                val convertedPath = if (format.lowercase() != "wav") {
                    logger.info("Converting $format to WAV...")
                    val conversionResult = audioConverter.convert(inputPath, wavPath)

                    if (!conversionResult.success) {
                        logger.error("Audio conversion failed: ${conversionResult.error}")
                        return TranscribeResponse(
                            text = "",
                            status = "ERROR",
                            tokenUsage = null,
                            duration = 0.0,
                            cost = 0.0,
                            error = "FFMPEG_ERROR: ${conversionResult.error}"
                        )
                    }

                    logger.info("Conversion successful in ${conversionResult.executionTimeMs}ms")
                    wavPath
                } else {
                    inputPath
                }

                // Step 7: Transcribe with Whisper
                logger.info("Transcribing with Whisper (model: ${whisperConfig.model})...")
                val transcriptionResult = executeWhisper(convertedPath)

                if (transcriptionResult == null) {
                    logger.error("Whisper transcription returned null")
                    return TranscribeResponse(
                        text = "",
                        status = "ERROR",
                        tokenUsage = null,
                        duration = 0.0,
                        cost = 0.0,
                        error = "WHISPER_ERROR: Transcription failed"
                    )
                }

                // Step 8: Calculate metrics
                val processingTime = System.currentTimeMillis() - startTime
                logger.info("Transcription completed in ${processingTime}ms")
                logger.info("Transcribed text: ${transcriptionResult.take(100)}...")

                logger.info("=== Transcription Successful ===")

                // Return success response (empty text is valid)
                return TranscribeResponse(
                    text = transcriptionResult,
                    status = "SUCCESS",
                    tokenUsage = null,
                    duration = processingTime / 1000.0,
                    cost = 0.0 // Local Whisper is free
                )

            } finally {
                // Clean up temp files (optional - files will be overwritten next time)
                // Uncomment to delete temp files after processing:
                // try {
                //     File(inputPath).delete()
                //     File(wavPath).delete()
                //     logger.debug("Cleaned up temp files")
                // } catch (e: Exception) {
                //     logger.warn("Failed to clean up temp files: ${e.message}")
                // }
            }

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Error during transcription", e)

            return TranscribeResponse(
                text = "",
                status = "ERROR",
                tokenUsage = null,
                duration = processingTime / 1000.0,
                cost = 0.0,
                error = "TRANSCRIPTION_ERROR: ${e.message}"
            )
        }
    }

    /**
     * Execute Whisper command and parse output
     *
     * @param audioPath Path to audio file (WAV format)
     * @return Transcribed text or null if failed
     */
    private fun executeWhisper(audioPath: String): String {
        val startTime = System.currentTimeMillis()

        // Detect device (GPU or CPU)
        val device = detectDevice()
        logger.debug("Using device: $device")

        // Build command
        val command = listOf(
            whisperConfig.command,
            audioPath,
            "--model", whisperConfig.model,
            "--language", whisperConfig.language,
            "--task", whisperConfig.task,
            "--output_format", whisperConfig.outputFormat,
            "--device", device
        )

        logger.debug("=== Whisper Command ===")
        logger.debug("Working directory: ${whisperConfig.tempDir}")
        logger.debug("Command: ${command.joinToString(" ")}")

        try {
            // Execute Whisper
            val process = ProcessBuilder(command)
                .directory(File(whisperConfig.tempDir))
                .redirectErrorStream(true) // Merge stderr with stdout
                .start()

            // Read output
            val stdout =
                process.inputStream.bufferedReader(Charset.forName(whisperConfig.encoding)).use { it.readText() }
            val exitCode = process.waitFor(whisperConfig.timeout.toLong(), TimeUnit.SECONDS)

            val executionTime = System.currentTimeMillis() - startTime

            logger.debug("=== Whisper Execution ===")
            logger.debug("Exit code: ${process.exitValue()}")
            logger.debug("Execution time: ${executionTime}ms")
            logger.debug("Stdout: ${stdout.take(500)}...")

            // Check for timeout
            if (!exitCode) {
                process.destroyForcibly()
                logger.error("Whisper execution timed out after ${whisperConfig.timeout} seconds")
                throw TimeoutException("Whisper execution timed out")
            }

            // Check exit code
            if (process.exitValue() != 0) {
                logger.error("Whisper failed with exit code ${process.exitValue()}")
                logger.error("Error output: $stdout")
                throw Exception("Whisper execution failed (exit code ${process.exitValue()})")
            }

            // Parse output
            val text = parseWhisperOutput(stdout)
            logger.debug("Parsed text: ${text.take(100)}...")

            return text

        } catch (e: TimeoutException) {
            logger.error("Whisper timeout", e)
            throw e
        } catch (e: Exception) {
            logger.error("Whisper execution error", e)
            throw e
        }
    }

    /**
     * Parse Whisper stdout to extract transcribed text
     *
     * Whisper stdout format (with timestamps):
     * [00:00:00.000 --> 00:00:02.500]  Привет, это тестовое сообщение
     * [00:00:02.500 --> 00:00:05.000]  Распознавание речи работает
     *
     * @param stdout Whisper stdout output
     * @return Transcribed text (without timestamps)
     */
    private fun parseWhisperOutput(stdout: String): String {
        // Try to parse timestamp format first
        val linesWithTimestamps = stdout.lines()
            .filter { it.contains(" --> ") }
            .map { line ->
                // Extract text after " --> HH:MM:SS.mmm] "
                line.substringAfter("] ").trim()
            }
            .filter { it.isNotEmpty() }

        if (linesWithTimestamps.isNotEmpty()) {
            return linesWithTimestamps.joinToString(" ")
        }

        // Fallback: return full output (for plain text format)
        return stdout.lines()
            .filter { it.trim().isNotEmpty() && !it.startsWith("[") }
            .joinToString(" ")
            .trim()
    }

    /**
     * Detect device (GPU or CPU) for Whisper
     *
     * @return "cuda" if CUDA is available, "cpu" otherwise
     */
    private fun detectDevice(): String {
        return when (whisperConfig.device.lowercase()) {
            "auto" -> {
                // Try to detect CUDA via nvidia-smi
                try {
                    val process = ProcessBuilder("nvidia-smi").start()
                    val exitCode = process.waitFor(5, TimeUnit.SECONDS)

                    if (exitCode && process.exitValue() == 0) {
                        logger.debug("CUDA detected via nvidia-smi")
                        "cuda"
                    } else {
                        logger.debug("CUDA not available, using CPU")
                        "cpu"
                    }
                } catch (e: Exception) {
                    logger.debug("CUDA detection failed, using CPU: ${e.message}")
                    "cpu"
                }
            }

            else -> whisperConfig.device
        }
    }

    /**
     * Check if Whisper is available in PATH
     *
     * @return true if Whisper is available, false otherwise
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(whisperConfig.command, "--help")
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor(5, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }

            if (exitCode && output.contains("whisper", ignoreCase = true)) {
                logger.info("Whisper health check passed")
                true
            } else {
                logger.warn("Whisper health check failed: exit code=${process.exitValue()}")
                false
            }
        } catch (e: Exception) {
            logger.error("Whisper health check error", e)
            false
        }
    }

    /**
     * Get Whisper version
     *
     * @return Version string or null if not available
     */
    suspend fun getVersion(): String? {
        return try {
            val process = ProcessBuilder(whisperConfig.command, "--help")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Extract version from help output
            output.lines()
                .find { it.contains("version", ignoreCase = true) }
                ?.trim()
        } catch (e: Exception) {
            logger.warn("Failed to get Whisper version", e)
            null
        }
    }

    /**
     * Clean up old temp files
     *
     * @param maxAgeMs Maximum age of files to keep (default: 24 hours)
     */
    fun cleanupOldFiles(maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        try {
            val tempDir = File(whisperConfig.tempDir)
            if (!tempDir.exists()) return

            val currentTime = System.currentTimeMillis()
            var deletedCount = 0

            tempDir.listFiles()?.forEach { file ->
                if (currentTime - file.lastModified() > maxAgeMs) {
                    if (file.delete()) {
                        deletedCount++
                        logger.debug("Deleted old temp file: ${file.name}")
                    }
                }
            }

            if (deletedCount > 0) {
                logger.info("Cleaned up $deletedCount old temp files")
            }
        } catch (e: Exception) {
            logger.warn("Failed to clean up old temp files", e)
        }
    }
}
