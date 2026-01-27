package ru.sber.cb.aichallenge_one.audio

/**
 * Callback interface for audio recording events.
 */
interface AudioRecorderCallbacks {
    fun onRecordingStarted()
    fun onRecordingStopped(result: AudioRecordingResult)
    fun onError(error: String)
}

/**
 * Result of audio recording.
 */
data class AudioRecordingResult(
    val audioData: String,  // base64 encoded audio
    val format: String,     // e.g., "webm", "wav"
    val duration: Long = 0L // milliseconds
)

/**
 * Interface for audio recording functionality.
 * Implementations will provide platform-specific audio recording.
 */
interface AudioRecorder {
    suspend fun checkPermission(): Boolean
    fun startRecording(): Boolean
    fun stopRecording()
    fun cancelRecording()
    fun getCurrentDuration(): Long
}

/**
 * Platform-specific factory provider - implemented separately for JS and Wasm.
 */
internal expect fun createAudioRecorder(callbacks: AudioRecorderCallbacks): AudioRecorder

/**
 * Factory for creating AudioRecorder instances.
 */
object AudioRecorderFactory {
    fun create(callbacks: AudioRecorderCallbacks): AudioRecorder {
        return createAudioRecorder(callbacks)
    }
}
