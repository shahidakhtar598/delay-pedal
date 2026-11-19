#include "io/OboePlayer.h"
#include <algorithm>
#include <cstdio>
#include <cstring>

namespace dd7 {

OboePlayer::OboePlayer() {
    engine_.prepare(48000.0);
}

OboePlayer::~OboePlayer() {
    stopAndClose();
}

bool OboePlayer::openStream(int32_t sampleRate) {
    stopAndClose();

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Shared)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setSampleRate(sampleRate)
           ->setDataCallback(this);

    const oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK || !stream_) {
        return false;
    }

    const int32_t actualSr = stream_->getSampleRate();
    sampleRate_.store(actualSr, std::memory_order_relaxed);
    engine_.prepare(static_cast<double>(actualSr));
    return stream_->requestStart() == oboe::Result::OK;
}

bool OboePlayer::loadPcmFile(const std::string& pcmPath, int32_t sampleRate) {
    isPlaying_.store(false, std::memory_order_release);
    stopAndClose();

    std::FILE* f = std::fopen(pcmPath.c_str(), "rb");
    if (!f) return false;

    std::fseek(f, 0, SEEK_END);
    const long bytes = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);
    if (bytes <= 0) {
        std::fclose(f);
        return false;
    }

    // Cap in-memory preview buffer to ~120 MB (~10.5 min stereo float @ 48kHz);
    // longer recordings still export in full via chunked OfflineRenderer.
    constexpr size_t kMaxPreviewFloats = 30 * 1024 * 1024;
    const size_t fileFloats = static_cast<size_t>(bytes) / sizeof(float);
    const size_t floatsToLoad = std::min(fileFloats, kMaxPreviewFloats);

    pcmSamples_.resize(floatsToLoad);
    const size_t readFloats = std::fread(pcmSamples_.data(), sizeof(float), floatsToLoad, f);
    std::fclose(f);

    if (readFloats < 2) {
        pcmSamples_.clear();
        totalFrames_.store(0, std::memory_order_release);
        return false;
    }

    totalFrames_.store(readFloats / 2, std::memory_order_release);
    playheadFrame_.store(0, std::memory_order_release);
    loopStartFrame_.store(0, std::memory_order_relaxed);
    loopEndFrame_.store(readFloats / 2, std::memory_order_relaxed);

    return openStream(sampleRate > 0 ? sampleRate : 48000);
}

void OboePlayer::setKnobParam(ParamId id, float value) noexcept {
    params_.setParam(id, value);
}

void OboePlayer::setPlaybackState(bool playing, int64_t seekPositionMs) {
    if (seekPositionMs >= 0) {
        const int32_t sr = sampleRate_.load(std::memory_order_relaxed);
        const size_t total = totalFrames_.load(std::memory_order_relaxed);
        const size_t targetFrame = std::min<size_t>(
            total,
            static_cast<size_t>((seekPositionMs * sr) / 1000)
        );
        playheadFrame_.store(targetFrame, std::memory_order_release);
    }
    isPlaying_.store(playing, std::memory_order_release);
}

void OboePlayer::setLoopRange(bool enabled, int64_t startMs, int64_t endMs) noexcept {
    const int32_t sr = sampleRate_.load(std::memory_order_relaxed);
    const size_t total = totalFrames_.load(std::memory_order_relaxed);
    const size_t s = std::min<size_t>(total, static_cast<size_t>((std::max<int64_t>(0, startMs) * sr) / 1000));
    const size_t e = std::min<size_t>(total, static_cast<size_t>((std::max<int64_t>(0, endMs) * sr) / 1000));
    loopStartFrame_.store(s, std::memory_order_relaxed);
    loopEndFrame_.store(e > s ? e : total, std::memory_order_relaxed);
    loopEnabled_.store(enabled, std::memory_order_release);
}

int64_t OboePlayer::getPlaybackPositionMs() const noexcept {
    const int32_t sr = sampleRate_.load(std::memory_order_relaxed);
    if (sr <= 0) return 0;
    const size_t frame = playheadFrame_.load(std::memory_order_relaxed);
    return static_cast<int64_t>((frame * 1000ULL) / static_cast<uint64_t>(sr));
}

void OboePlayer::stopAndClose() {
    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }
}

oboe::DataCallbackResult OboePlayer::onAudioReady(
    oboe::AudioStream* /*audioStream*/,
    void* audioData,
    int32_t numFrames
) {
    auto* out = static_cast<float*>(audioData);
    const size_t frameCount = static_cast<size_t>(numFrames);

    // Fill dry input buffer (or silence when paused so active echo trails still ring out naturally!)
    std::memset(out, 0, frameCount * 2 * sizeof(float));

    const bool playing = isPlaying_.load(std::memory_order_acquire);
    const size_t total = totalFrames_.load(std::memory_order_acquire);

    if (playing && total > 0 && !pcmSamples_.empty()) {
        size_t pos = playheadFrame_.load(std::memory_order_relaxed);
        const bool looping = loopEnabled_.load(std::memory_order_relaxed);
        const size_t loopStart = loopStartFrame_.load(std::memory_order_relaxed);
        const size_t loopEnd = std::min(total, loopEndFrame_.load(std::memory_order_relaxed));
        const size_t activeEnd = (looping && loopEnd > loopStart) ? loopEnd : total;

        for (size_t i = 0; i < frameCount; ++i) {
            if (pos >= activeEnd) {
                if (looping && loopEnd > loopStart) {
                    pos = loopStart;
                } else {
                    isPlaying_.store(false, std::memory_order_relaxed);
                    break;
                }
            }
            out[i * 2]     = pcmSamples_[pos * 2];
            out[i * 2 + 1] = pcmSamples_[pos * 2 + 1];
            ++pos;
        }
        playheadFrame_.store(pos, std::memory_order_relaxed);
    }

    const DspParamSnapshot snap = params_.loadSnapshot();
    const float peak = engine_.processInterleaved(out, frameCount, snap);
    meterPeak_.store(peak, std::memory_order_relaxed);
    limitingFlag_.store(engine_.isLimiting(), std::memory_order_relaxed);

    return oboe::DataCallbackResult::Continue;
}

} // namespace dd7
