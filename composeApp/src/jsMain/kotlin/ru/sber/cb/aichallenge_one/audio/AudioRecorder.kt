package ru.sber.cb.aichallenge_one.audio

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.js.json

/**
 * Actual factory function for JS platform.
 */
internal actual fun createAudioRecorder(callbacks: AudioRecorderCallbacks): AudioRecorder {
    return AudioRecorderWeb(callbacks)
}

/**
 * Web implementation of AudioRecorder using MediaRecorder API.
 */
class AudioRecorderWeb(
    private val callbacks: AudioRecorderCallbacks
) : AudioRecorder {

    private var mediaRecorder: dynamic = null
    private var mediaStream: dynamic = null
    private var startTime: Double = 0.0
    private var stopCallback: ((String, String, Long) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    companion object {
        const val DEFAULT_MIME_TYPE = "audio/webm;codecs=opus"
        const val DEFAULT_SAMPLE_RATE = 16000
    }

    override suspend fun checkPermission(): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val constraints = json("audio" to true)
            window.navigator.asDynamic().mediaDevices
                .getUserMedia(constraints)
                .then(
                    onFulfilled = { stream: dynamic ->
                        stream.getTracks()
                            .forEach { track: dynamic -> track.stop() }
                        cont.resume(true)
                    },
                    onRejected = { cont.resume(false) }
                )
        } catch (e: Exception) {
            cont.resume(false)
        }
    }

    override fun startRecording(): Boolean {
        try {
            stopCallback = { audioData, format, duration ->
                callbacks.onRecordingStopped(AudioRecordingResult(audioData, format, duration))
            }
            errorCallback = { error -> callbacks.onError(error) }

            val constraints = json(
                "audio" to json(
                    "sampleRate" to DEFAULT_SAMPLE_RATE,
                    "channelCount" to 1
                )
            )

            window.navigator.asDynamic().mediaDevices
                .getUserMedia(constraints)
                .then(
                    onFulfilled = { stream: dynamic ->
                        mediaStream = stream
                        startTime = kotlin.js.Date.now()

                        val mimeType = if (isMimeTypeSupported(DEFAULT_MIME_TYPE)) {
                            DEFAULT_MIME_TYPE
                        } else {
                            "audio/webm"
                        }

                        val options = json("mimeType" to mimeType)
                        mediaRecorder = js("new MediaRecorder(stream, options)")

                        val chunks = mutableListOf<dynamic>()

                        mediaRecorder.ondataavailable = { event: dynamic ->
                            val data = event.data
                            if (data != null && data.size > 0) {
                                chunks.add(data)
                            }
                        }

                        mediaRecorder.onstop = {
                            val duration = (kotlin.js.Date.now() - startTime).toLong()

                            val blobOptions = json("type" to mimeType)
                            val chunksArray = chunks.toTypedArray()
                            val blob = js("new Blob(chunksArray, blobOptions)")

                            val reader = js("new FileReader()")
                            reader.onload = { evt: dynamic ->
                                val result = evt.target.result as String
                                val base64 = result.substringAfter(",")
                                stopCallback?.invoke(base64, "webm", duration)
                            }
                            reader.onerror = { _: dynamic ->
                                errorCallback?.invoke("Failed to encode audio data")
                            }
                            reader.readAsDataURL(blob)

                            mediaStream.getTracks()
                                .forEach { track: dynamic -> track.stop() }
                        }

                        mediaRecorder.onerror = { event: dynamic ->
                            cleanup()
                            val error = event.message as? String ?: "Unknown error"
                            errorCallback?.invoke("Recording error: $error")
                        }

                        mediaRecorder.start(100)
                        callbacks.onRecordingStarted()
                    },
                    onRejected = { error: Any ->
                        callbacks.onError("Microphone access denied: $error")
                    }
                )

            return true
        } catch (e: Exception) {
            callbacks.onError("Failed to start recording: ${e.message}")
            cleanup()
            return false
        }
    }

    override fun stopRecording() {
        try {
            if (mediaRecorder != null && mediaRecorder.state == "recording") {
                mediaRecorder.stop()
            }
        } catch (e: Exception) {
            cleanup()
            callbacks.onError("Failed to stop recording: ${e.message}")
        }
    }

    override fun cancelRecording() {
        cleanup()
    }

    override fun getCurrentDuration(): Long {
        return if (mediaRecorder != null && mediaRecorder.state == "recording") {
            (kotlin.js.Date.now() - startTime).toLong()
        } else {
            0L
        }
    }

    private fun cleanup() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.ondataavailable = null
                mediaRecorder.onstop = null
                mediaRecorder.onerror = null
            }
            if (mediaStream != null) {
                mediaStream.getTracks()
                    .forEach { track: dynamic ->
                        try {
                            track.stop()
                        } catch (e: Exception) {
                        }
                    }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        } finally {
            mediaRecorder = null
            mediaStream = null
            stopCallback = null
            errorCallback = null
        }
    }

    private fun isMimeTypeSupported(mimeType: String): Boolean {
        @Suppress("UnsafeCastFromDynamic")
        return js(
            """
            (function(m) {
                try {
                    return MediaRecorder.isTypeSupported(m);
                } catch(e) {
                    return false;
                }
            })
            """
        ).unsafeCast<(String) -> Boolean>()(mimeType)
    }
}
