#include "io/OfflineRenderer.h"
#include "io/WavWriter.h"
#include "io/Mp3Encoder.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

namespace dd7 {

int32_t OfflineRenderer::computeMaxTailMs(const DspParamSnapshot& params) noexcept {
    if (params.bypassed || params.effectLevel <= 0.001f) {
        return 0;
    }
    const float clampedMs = clampDelayForMode(params.delayTimeMs, params.mode);
    const float fb = std::clamp(params.feedback, 0.01f, 0.95f);
    // Number of repeats to drop by -60 dB: log(0.001) / log(fb)
    const float repeats = std::log(0.001f) / std::log(fb);
    const float estMs = std::max(2000.0f, clampedMs * std::min(repeats, 24.0f));
    return static_cast<int32_t>(std::clamp(estMs, 2000.0f, 12000.0f));
}

OfflineExportResult OfflineRenderer::render(
    const OfflineExportConfig& config,
    const std::atomic<bool>& cancelFlag,
    const std::function<void(int32_t)>& onProgressPercent
) {
    OfflineExportResult res;

    std::FILE* inFile = std::fopen(config.inputPcmPath.c_str(), "rb");
    if (!inFile) {
        res.errorCode = "INPUT_PCM_OPEN_FAILED";
        return res;
    }

    std::fseek(inFile, 0, SEEK_END);
    const long fileBytes = std::ftell(inFile);
    std::fseek(inFile, 0, SEEK_SET);

    if (fileBytes <= 0) {
        std::fclose(inFile);
        res.errorCode = "INPUT_PCM_EMPTY";
        return res;
    }

    const size_t totalSourceFrames = static_cast<size_t>(fileBytes) / (sizeof(float) * 2);
    size_t framesToRead = totalSourceFrames;
    if (config.maxDurationSec > 0) {
        const size_t capFrames = static_cast<size_t>(config.maxDurationSec) * static_cast<size_t>(config.sampleRate);
        framesToRead = std::min(framesToRead, capFrames);
    }

    const int32_t maxTailMs = computeMaxTailMs(config.dspParams);
    const size_t maxTailFrames = static_cast<size_t>((static_cast<int64_t>(maxTailMs) * config.sampleRate) / 1000);
    const size_t totalPlannedFrames = framesToRead + maxTailFrames;

    // Prepare identical DSP engine instance
    DelayEngine engine;
    engine.prepare(static_cast<double>(config.sampleRate));

    WavWriter wavWriter;
    Mp3Encoder mp3Encoder;
    const bool isWav = (config.format == ExportFormat::WAV_16);

    if (isWav) {
        if (!wavWriter.open(config.outputFilePath, config.sampleRate, 2)) {
            std::fclose(inFile);
            res.errorCode = "WAV_OPEN_FAILED";
            return res;
        }
    } else {
        const int32_t bitrate = (config.format == ExportFormat::MP3_320) ? 320 : 192;
        if (!mp3Encoder.open(config.outputFilePath, config.sampleRate, bitrate)) {
            std::fclose(inFile);
            res.errorCode = mp3Encoder.lastError().empty() ? "MP3_OPEN_FAILED" : mp3Encoder.lastError();
            return res;
        }
    }

    constexpr size_t kChunkFrames = 2048;
    std::vector<float> chunk(kChunkFrames * 2, 0.0f);

    size_t processedFrames = 0;
    int32_t lastReportedPct = -1;

    auto reportProgress = [&](size_t done) {
        if (!onProgressPercent || totalPlannedFrames == 0) return;
        const int32_t pct = static_cast<int32_t>(std::min<size_t>(99, (done * 100) / totalPlannedFrames));
        if (pct != lastReportedPct) {
            lastReportedPct = pct;
            onProgressPercent(pct);
        }
    };

    auto abortCleanup = [&](const std::string& code) {
        std::fclose(inFile);
        if (isWav) wavWriter.closeAndDelete(config.outputFilePath);
        else mp3Encoder.closeAndDelete(config.outputFilePath);
        res.success = false;
        res.errorCode = code;
        return res;
    };

    // Pass 1: Process dry vocal frames
    size_t remaining = framesToRead;
    while (remaining > 0) {
        if (cancelFlag.load(std::memory_order_relaxed)) {
            return abortCleanup("EXPORT_CANCELLED");
        }
        const size_t batch = std::min(remaining, kChunkFrames);
        const size_t floatsRead = std::fread(chunk.data(), sizeof(float), batch * 2, inFile);
        const size_t framesRead = floatsRead / 2;
        if (framesRead == 0) break;

        engine.processInterleaved(chunk.data(), framesRead, config.dspParams);

        const bool wrote = isWav
            ? wavWriter.writeFloatFrames(chunk.data(), framesRead)
            : mp3Encoder.writeFloatFrames(chunk.data(), framesRead);
        if (!wrote) {
            return abortCleanup("DISK_WRITE_FAILED");
        }

        remaining -= framesRead;
        processedFrames += framesRead;
        reportProgress(processedFrames);
    }

    std::fclose(inFile);

    // Pass 2: Append silence frames to capture complete echo tail until RMS < -60 dBFS (0.001)
    size_t tailFramesWritten = 0;
    int consecutiveSilentBlocks = 0;
    constexpr float kSilenceRmsThreshold = 0.001f; // -60 dBFS

    while (tailFramesWritten < maxTailFrames) {
        if (cancelFlag.load(std::memory_order_relaxed)) {
            if (isWav) wavWriter.closeAndDelete(config.outputFilePath);
            else mp3Encoder.closeAndDelete(config.outputFilePath);
            res.success = false;
            res.errorCode = "EXPORT_CANCELLED";
            return res;
        }

        const size_t batch = std::min(kChunkFrames, maxTailFrames - tailFramesWritten);
        std::fill(chunk.begin(), chunk.begin() + static_cast<std::ptrdiff_t>(batch * 2), 0.0f);

        engine.processInterleaved(chunk.data(), batch, config.dspParams);

        // Compute block RMS
        double sumSq = 0.0;
        for (size_t i = 0; i < batch * 2; ++i) {
            sumSq += static_cast<double>(chunk[i]) * static_cast<double>(chunk[i]);
        }
        const float rms = static_cast<float>(std::sqrt(sumSq / static_cast<double>(batch * 2)));

        const bool wrote = isWav
            ? wavWriter.writeFloatFrames(chunk.data(), batch)
            : mp3Encoder.writeFloatFrames(chunk.data(), batch);
        if (!wrote) {
            if (isWav) wavWriter.closeAndDelete(config.outputFilePath);
            else mp3Encoder.closeAndDelete(config.outputFilePath);
            res.success = false;
            res.errorCode = "DISK_WRITE_FAILED";
            return res;
        }

        tailFramesWritten += batch;
        processedFrames += batch;
        reportProgress(processedFrames);

        if (rms < kSilenceRmsThreshold) {
            ++consecutiveSilentBlocks;
            // Require ~200ms of continuous silence after minimum 1.0s tail check
            if (consecutiveSilentBlocks >= 5 && tailFramesWritten >= static_cast<size_t>(config.sampleRate / 2)) {
                break;
            }
        } else {
            consecutiveSilentBlocks = 0;
        }
    }

    const bool finalized = isWav ? wavWriter.finalize() : mp3Encoder.finalize();
    if (!finalized) {
        res.errorCode = "FINALIZE_FAILED";
        return res;
    }

    if (onProgressPercent) onProgressPercent(100);

    res.success = true;
    res.fileBytes = isWav ? wavWriter.totalBytesWritten() : mp3Encoder.totalBytesWritten();
    res.tailMs = static_cast<int32_t>((tailFramesWritten * 1000) / static_cast<size_t>(config.sampleRate));
    return res;
}

} // namespace dd7
