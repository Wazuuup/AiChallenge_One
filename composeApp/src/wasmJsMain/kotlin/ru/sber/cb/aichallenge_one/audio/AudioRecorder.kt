package ru.sber.cb.aichallenge_one.audio

/**
 * Actual factory function for Wasm platform.
 */
internal actual fun createAudioRecorder(callbacks: AudioRecorderCallbacks): AudioRecorder {
    return AudioRecorderWebStub(callbacks)
}

/**
 * Stub implementation for Wasm - audio recording not supported in Wasm yet.
 */
class AudioRecorderWebStub(
    private val callbacks: AudioRecorderCallbacks
) : AudioRecorder {

    override suspend fun checkPermission(): Boolean = false

    override fun startRecording(): Boolean {
        callbacks.onError("Voice input is not supported in Wasm environment. Please use the JS version.")
        return false
    }

    override fun stopRecording() {
        // No-op
    }

    override fun cancelRecording() {
        // No-op
    }

    override fun getCurrentDuration(): Long = 0L
}
