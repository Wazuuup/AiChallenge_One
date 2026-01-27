package ru.sber.cb.aichallenge_one.audio

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.js.json

/**
 * Actual implementation of AudioRecorderFactory for JS platform.
 */
actual object AudioRecorderFactory {
    actual fun create(callbacks: AudioRecorderCallbacks): AudioRecorder {
        return AudioRecorderWeb(callbacks)
    }
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
                    onFulfilled = { stream ->
                        stream.asDynamic().getTracks().asDynamic()
                            .forEach { track -> track.asDynamic().stop() }
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
                    onFulfilled = { stream ->
                        mediaStream = stream
                        startTime = kotlin.js.Date.now()

                        val mimeType = if (isMimeTypeSupported(DEFAULT_MIME_TYPE)) {
                            DEFAULT_MIME_TYPE
                        } else {
                            "audio/webm"
                        }

                        json("mimeType" to mimeType)
                        mediaRecorder = js("new MediaRecorder(stream, options)")

                        val chunks = mutableListOf<dynamic>()

                        mediaRecorder!!.ondataavailable = { event: dynamic ->
                            val data = event.asDynamic().data
                            if (data != null && data.asDynamic().size > 0) {
                                chunks.add(data)
                            }
                        }

                        mediaRecorder!!.onstop = {
                            val duration = (kotlin.js.Date.now() - startTime).toLong()

                            json("type" to mimeType)
                            chunks.toTypedArray()
                            val blob = js("new Blob(chunksArray, blobOptions)")

                            val reader = js("new FileReader()")
                            reader.onload = { evt: dynamic ->
                                val result = evt.asDynamic().target.result as String
                                val base64 = result.substringAfter(",")
                                stopCallback?.invoke(base64, "webm", duration)
                            }
                            reader.onerror = { _: dynamic ->
                                errorCallback?.invoke("Failed to encode audio data")
                            }
                            reader.readAsDataURL(blob)

                            stream.asDynamic().getTracks().asDynamic()
                                .forEach { track: dynamic -> track.asDynamic().stop() }
                        }

                        mediaRecorder!!.onerror = { event: dynamic ->
                            cleanup()
                            val error = event.asDynamic().message as? String ?: "Unknown error"
                            errorCallback?.invoke("Recording error: $error")
                        }

                        mediaRecorder!!.start(100)
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
            if (mediaRecorder != null && mediaRecorder!!.asDynamic().state == "recording") {
                mediaRecorder!!.stop()
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
        return if (mediaRecorder != null && mediaRecorder!!.asDynamic().state == "recording") {
            (kotlin.js.Date.now() - startTime).toLong()
        } else {
            0L
        }
    }

    private fun cleanup() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder!!.asDynamic().ondataavailable = null
                mediaRecorder!!.asDynamic().onstop = null
                mediaRecorder!!.asDynamic().onerror = null
            }
            if (mediaStream != null) {
                mediaStream!!.asDynamic().getTracks().asDynamic()
                    .forEach { track ->
                        try {
                            track.asDynamic().stop()
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
