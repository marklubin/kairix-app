package org.kairix.kairix_app.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.*

@OptIn(ExperimentalForeignApi::class)
actual class AudioStream actual constructor(
    private val sampleRateIn: Int,
    private val sampleRateOut: Int,
) {
    private val audioEngine = AVAudioEngine()
    private val playerNode = AVAudioPlayerNode()
    private var isCapturing = false
    private var isPlaying = false

    // Format for capture (what we send to server)
    private val captureFormat = AVAudioFormat(
        standardFormatWithSampleRate = sampleRateIn.toDouble(),
        channels = 1u
    )

    // Format for playback (what server sends us)
    private val playbackFormat = AVAudioFormat(
        standardFormatWithSampleRate = sampleRateOut.toDouble(),
        channels = 1u
    )

    actual fun startCapture(onAudioChunk: (ByteArray) -> Unit) {
        if (isCapturing) return

        // 1. Configure audio session (policy layer)
        // This MUST happen before starting the engine
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeVoiceChat,  // Enables AEC
            options = AVAudioSessionCategoryOptionDefaultToSpeaker or
                    AVAudioSessionCategoryOptionAllowBluetooth,
            error = null
        )
        session.setActive(true, error = null)

        // 2. Get input node
        val inputNode = audioEngine.inputNode

        // 3. Install tap with our desired format - iOS will resample for us
        // Request 16kHz mono, iOS converts from hardware rate (usually 48kHz)
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 1024u,  // ~64ms at 16kHz
            format = captureFormat
        ) { buffer, _ ->
            buffer?.let {
                val bytes = pcmBufferToBytes(it)
                if (bytes.isNotEmpty()) {
                    onAudioChunk(bytes)
                }
            }
        }

        // 4. Set up player node for playback at server's sample rate
        audioEngine.attachNode(playerNode)
        // Connect player → mixer using playback format (22050Hz)
        // iOS will resample to hardware output rate
        audioEngine.connect(playerNode, audioEngine.mainMixerNode, playbackFormat)

        // 5. Start the engine
        audioEngine.prepare()
        audioEngine.startAndReturnError(null)
        isCapturing = true
    }

    actual fun stopCapture() {
        if (!isCapturing) return

        audioEngine.inputNode.removeTapOnBus(0u)
        audioEngine.stop()
        isCapturing = false

        // Deactivate audio session
        val session = AVAudioSession.sharedInstance()
        session.setActive(false, error = null)
    }

    actual fun playAudio(data: ByteArray) {
        if (!isCapturing) return  // Engine must be running

        val buffer = bytesToPcmBuffer(data) ?: return

        if (!isPlaying) {
            playerNode.play()
            isPlaying = true
        }

        playerNode.scheduleBuffer(buffer, completionHandler = null)
    }

    actual fun stopPlayback() {
        if (isPlaying) {
            playerNode.stop()
            isPlaying = false
        }
    }

    /**
     * Convert AVAudioPCMBuffer (float32) to ByteArray (int16 PCM).
     * iOS gives us float samples in range -1.0 to 1.0
     * Server expects Int16 samples in range -32768 to 32767
     */
    private fun pcmBufferToBytes(buffer: AVAudioPCMBuffer): ByteArray {
        val floatData = buffer.floatChannelData ?: return ByteArray(0)
        val frameCount = buffer.frameLength.toInt()

        if (frameCount == 0) return ByteArray(0)

        // Get pointer to first channel (mono)
        val channelData = floatData[0] ?: return ByteArray(0)

        // Convert float32 to int16 little-endian
        val bytes = ByteArray(frameCount * 2)
        for (i in 0 until frameCount) {
            val floatSample = channelData[i]
            // Clamp and convert to Int16
            val intSample = (floatSample * 32767f)
                .toInt()
                .coerceIn(-32768, 32767)
                .toShort()
            // Little-endian: low byte first
            bytes[i * 2] = (intSample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (intSample.toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Convert ByteArray (int16 PCM) to AVAudioPCMBuffer (float32).
     * Reverse of pcmBufferToBytes for playback.
     * Uses playbackFormat configured at construction time.
     */
    private fun bytesToPcmBuffer(data: ByteArray): AVAudioPCMBuffer? {
        if (data.isEmpty()) return null

        val frameCount = data.size / 2  // 2 bytes per sample

        val buffer = AVAudioPCMBuffer(playbackFormat!!, frameCapacity = frameCount.toUInt())
            ?: return null

        buffer.frameLength = frameCount.toUInt()

        val floatData = buffer.floatChannelData ?: return null
        val channelData = floatData[0] ?: return null

        // Convert int16 little-endian to float32
        for (i in 0 until frameCount) {
            val low = data[i * 2].toInt() and 0xFF
            val high = data[i * 2 + 1].toInt()
            val intSample = (high shl 8) or low
            // Convert to float in range -1.0 to 1.0
            channelData[i] = intSample.toFloat() / 32768f
        }

        return buffer
    }
}
