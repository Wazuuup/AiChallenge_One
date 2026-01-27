package ru.sber.cb.aichallenge_one.client

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.config.FfmpegConfig
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Audio Converter using ffmpeg
 *
 * This class provides audio conversion functionality using ffmpeg via ProcessBuilder.
 * It's primarily used to convert WebM audio from browsers to WAV format required by Whisper.
 *
 * @property ffmpegConfig FFmpeg configuration
 */
class AudioConverter(
    private val ffmpegConfig: FfmpegConfig
) {
    private val logger = LoggerFactory.getLogger(AudioConverter::class.java)

    init {
        ffmpegConfig.validate()
        logger.info(
            "AudioConverter initialized with: command=${ffmpegConfig.command}, " +
                    "sampleRate=${ffmpegConfig.sampleRate}, channels=${ffmpegConfig.channels}"
        )
    }

    /**
     * Converts audio file from input format to output format
     *
     * @param inputPath Path to input audio file
     * @param outputPath Path to output audio file
     * @return true if conversion succeeded, false otherwise
     */
    fun convert(inputPath: String, outputPath: String): AudioConversionResult {
        val startTime = System.currentTimeMillis()
        val inputFile = File(inputPath)

        logger.debug("=== Audio Conversion Started ===")
        logger.debug("Input: $inputPath (${inputFile.length()} bytes)")
        logger.debug("Output: $outputPath")
        logger.debug("Sample rate: ${ffmpegConfig.sampleRate} Hz")
        logger.debug("Channels: ${ffmpegConfig.channels} (mono)")
        logger.debug("Format: ${ffmpegConfig.inputFormat} -> ${ffmpegConfig.outputFormat}")

        // Validate input file exists
        if (!inputFile.exists()) {
            logger.error("Input file does not exist: $inputPath")
            return AudioConversionResult(
                success = false,
                outputPath = outputPath,
                executionTimeMs = 0,
                error = "Input file not found: $inputPath"
            )
        }

        // Build command
        val command = ffmpegConfig.buildCommand(inputPath, outputPath)
        logger.debug("Command: ${command.joinToString(" ")}")

        try {
            // Create output directory if it doesn't exist
            File(outputPath).parentFile?.mkdirs()

            // Execute ffmpeg
            val process = ProcessBuilder(command)
                .redirectErrorStream(true) // Merge stderr with stdout
                .start()

            // Read output
            val stdout = process.inputStream.bufferedReader(Charset.forName("UTF-8")).use { it.readText() }
            val exitCode = process.waitFor(ffmpegConfig.timeout.toLong(), TimeUnit.SECONDS)

            val executionTime = System.currentTimeMillis() - startTime

            logger.debug("Exit code: $exitCode")
            logger.debug("Execution time: ${executionTime}ms")

            // Check for timeout
            if (!exitCode) {
                process.destroyForcibly()
                logger.error("FFmpeg conversion timed out after ${ffmpegConfig.timeout} seconds")
                return AudioConversionResult(
                    success = false,
                    outputPath = outputPath,
                    executionTimeMs = executionTime,
                    error = "Conversion timed out after ${ffmpegConfig.timeout} seconds"
                )
            }

            // Check exit code
            if (process.exitValue() != 0) {
                logger.error("FFmpeg conversion failed with exit code ${process.exitValue()}")
                logger.error("FFmpeg output: $stdout")
                return AudioConversionResult(
                    success = false,
                    outputPath = outputPath,
                    executionTimeMs = executionTime,
                    error = "Conversion failed (exit code ${process.exitValue()}): $stdout"
                )
            }

            // Validate output file exists
            val outputFile = File(outputPath)
            if (!outputFile.exists()) {
                logger.error("Output file was not created: $outputPath")
                return AudioConversionResult(
                    success = false,
                    outputPath = outputPath,
                    executionTimeMs = executionTime,
                    error = "Output file was not created"
                )
            }

            logger.info("Audio conversion successful: ${outputFile.length()} bytes in ${executionTime}ms")
            logger.debug("FFmpeg output: $stdout")
            logger.debug("=== Audio Conversion Completed ===")

            return AudioConversionResult(
                success = true,
                outputPath = outputPath,
                executionTimeMs = executionTime,
                outputSizeBytes = outputFile.length()
            )

        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            logger.error("Error during audio conversion", e)
            return AudioConversionResult(
                success = false,
                outputPath = outputPath,
                executionTimeMs = executionTime,
                error = "Conversion error: ${e.message}"
            )
        }
    }

    /**
     * Converts audio bytes from input format to output format
     *
     * @param inputBytes Input audio data as bytes
     * @param inputExtension Input file extension (e.g., "webm")
     * @param outputExtension Output file extension (e.g., "wav")
     * @return AudioConversionResult with output bytes or error
     */
    fun convertBytes(
        inputBytes: ByteArray,
        inputExtension: String,
        outputExtension: String
    ): AudioConversionResult {
        val timestamp = System.currentTimeMillis()
        val inputPath = "${ffmpegConfig.command}_temp_input_$timestamp.$inputExtension"
        val outputPath = "${ffmpegConfig.command}_temp_output_$timestamp.$outputExtension"

        try {
            // Write input bytes to temp file
            File(inputPath).writeBytes(inputBytes)

            // Convert
            val result = convert(inputPath, outputPath)

            // Read output bytes if successful
            if (result.success) {
                val outputBytes = File(outputPath).readBytes()
                return result.copy(outputBytes = outputBytes)
            }

            return result

        } finally {
            // Clean up temp files
            try {
                File(inputPath).delete()
                File(outputPath).delete()
            } catch (e: Exception) {
                logger.warn("Failed to clean up temp files: ${e.message}")
            }
        }
    }

    /**
     * Checks if ffmpeg is available in PATH
     *
     * @return true if ffmpeg is available, false otherwise
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(ffmpegConfig.command, "-version")
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor(5, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }

            if (exitCode && output.contains("ffmpeg", ignoreCase = true)) {
                logger.info("FFmpeg health check passed")
                // Extract version
                val version = output.lines()
                    .find { it.contains("version", ignoreCase = true) }
                    ?.trim()
                logger.info("FFmpeg version: $version")
                true
            } else {
                logger.warn("FFmpeg health check failed: exit code=$process.exitValue()")
                false
            }
        } catch (e: Exception) {
            logger.error("FFmpeg health check error", e)
            false
        }
    }

    /**
     * Result of audio conversion
     */
    data class AudioConversionResult(
        val success: Boolean,
        val outputPath: String,
        val executionTimeMs: Long,
        val outputSizeBytes: Long? = null,
        val outputBytes: ByteArray? = null,
        val error: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioConversionResult

            if (success != other.success) return false
            if (outputPath != other.outputPath) return false
            if (executionTimeMs != other.executionTimeMs) return false
            if (outputSizeBytes != other.outputSizeBytes) return false
            if (outputBytes != null) {
                if (other.outputBytes == null) return false
                if (!outputBytes.contentEquals(other.outputBytes)) return false
            } else if (other.outputBytes != null) return false
            if (error != other.error) return false

            return true
        }

        override fun hashCode(): Int {
            var result = success.hashCode()
            result = 31 * result + outputPath.hashCode()
            result = 31 * result + executionTimeMs.hashCode()
            result = 31 * result + (outputSizeBytes?.hashCode() ?: 0)
            result = 31 * result + (outputBytes?.contentHashCode() ?: 0)
            result = 31 * result + (error?.hashCode() ?: 0)
            return result
        }
    }
}
