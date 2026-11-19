package com.naatdelay.dd7.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

data class DecodedAudioResult(
    val displayName: String,
    val cachedPcmPath: String,
    val sampleRate: Int,
    val durationMs: Long,
    val waveformPeaks: List<Float>
)

@Singleton
class AudioImportDecoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Decodes MP3, WAV, M4A/AAC, OGG/OPUS (WhatsApp voice notes) via native OS MediaCodec
     * into a normalized 32-bit float interleaved stereo PCM cache file.
     */
    suspend fun decodeToStereoFloatPcm(uri: Uri): Result<DecodedAudioResult> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val outFile = File(context.cacheDir, "pcm_${UUID.randomUUID()}.raw")

        try {
            val displayName = resolveDisplayName(uri)
            extractor.setDataSource(context, uri, null)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) {
                return@withContext Result.failure(IllegalArgumentException("UNSUPPORTED_AUDIO_FORMAT"))
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var totalStereoFrames = 0L
            val blockPeaks = ArrayList<Float>(1024)

            BufferedOutputStream(FileOutputStream(outFile), 65536).use { out ->
                val floatOutBytes = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN)

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = codec.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val sampleCount = shortBuf.remaining()
                                val frames = sampleCount / channelCount.coerceAtLeast(1)

                                var blockPeak = 0f
                                floatOutBytes.clear()

                                for (f in 0 until frames) {
                                    val l = shortBuf.get().toFloat() / 32768f
                                    val r = if (channelCount >= 2) {
                                        val rv = shortBuf.get().toFloat() / 32768f
                                        for (extra in 2 until channelCount) shortBuf.get()
                                        rv
                                    } else {
                                        l
                                    }
                                    blockPeak = max(blockPeak, max(abs(l), abs(r)))

                                    if (floatOutBytes.remaining() < 8) {
                                        out.write(floatOutBytes.array(), 0, floatOutBytes.position())
                                        floatOutBytes.clear()
                                    }
                                    floatOutBytes.putFloat(l)
                                    floatOutBytes.putFloat(r)
                                }
                                if (floatOutBytes.position() > 0) {
                                    out.write(floatOutBytes.array(), 0, floatOutBytes.position())
                                }
                                totalStereoFrames += frames
                                blockPeaks.add(blockPeak)
                            }

                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true
                            }
                        }
                    }
                }
            }

            if (totalStereoFrames == 0L) {
                outFile.delete()
                return@withContext Result.failure(IllegalStateException("EMPTY_AUDIO_TRACK"))
            }

            val durationMs = (totalStereoFrames * 1000L) / sampleRate.coerceAtLeast(1)
            val peaks = WaveformGenerator.downsamplePeaks(blockPeaks, WaveformGenerator.DEFAULT_BAR_COUNT)

            Result.success(
                DecodedAudioResult(
                    displayName = displayName,
                    cachedPcmPath = outFile.absolutePath,
                    sampleRate = sampleRate,
                    durationMs = durationMs,
                    waveformPeaks = peaks
                )
            )
        } catch (t: Throwable) {
            outFile.delete()
            Result.failure(t)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: "Imported_Naat.audio"
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported_Naat.audio"
    }
}
