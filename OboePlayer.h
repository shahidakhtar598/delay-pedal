#pragma once

#include "dsp/DelayEngine.h"
#include "dsp/DspParams.h"
#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace dd7 {

// Real-time Google Oboe audio playback engine.
// Audio callback onAudioReady() is strictly lock-free, allocation-free, and JNI-free.
class OboePlayer : public oboe::AudioStreamDataCallback {
public:
    OboePlayer();
    ~OboePlayer() override;

    bool loadPcmFile(const std::string& pcmPath, int32_t sampleRate);
    void setKnobParam(ParamId id, float value) noexcept;
    void setPlaybackState(bool playing, int64_t seekPositionMs);
    void setLoopRange(bool enabled, int64_t startMs, int64_t endMs) noexcept;

    int64_t getPlaybackPositionMs() const noexcept;
    float getOutputMeterLevel() const noexcept { return meterPeak_.load(std::memory_order_relaxed); }
    bool isLimiting() const noexcept { return limitingFlag_.load(std::memory_order_relaxed); }
    DspParamSnapshot getParamsSnapshot() const noexcept { return params_.loadSnapshot(); }

    void stopAndClose();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames
    ) override;

private:
    bool openStream(int32_t sampleRate);

    std::shared_ptr<oboe::AudioStream> stream_;
    DelayEngine engine_;
    AtomicDspParams params_;

    // Decoded stereo float buffer swapped safely while stream is paused
    std::vector<float> pcmSamples_;
    std::atomic<size_t> totalFrames_{0};
    std::atomic<size_t> playheadFrame_{0};
    std::atomic<int32_t> sampleRate_{48000};
    std::atomic<bool> isPlaying_{false};

    // Optional A/B verse loop
    std::atomic<bool> loopEnabled_{false};
    std::atomic<size_t> loopStartFrame_{0};
    std::atomic<size_t> loopEndFrame_{0};

    // Telemetry polled at 30fps by Compose UI
    std::atomic<float> meterPeak_{0.0f};
    std::atomic<bool> limitingFlag_{false};
};

} // namespace dd7
