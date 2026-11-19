#pragma once

#include "dsp/DspParams.h"
#include "dsp/DelayLine.h"
#include "dsp/AnalogFilter.h"
#include "dsp/ModLfo.h"
#include "dsp/PingPongMatrix.h"
#include "dsp/SoftLimiter.h"
#include <cstddef>

namespace dd7 {

// Master Parallel Dry/Wet DD-7 Vocal Delay Engine.
// Identical instance runs in both Google Oboe real-time playback and OfflineRenderer export.
class DelayEngine {
public:
    void prepare(double sampleRate);
    void reset() noexcept;

    // Process interleaved stereo buffer in-place (frameCount * 2 floats)
    // Returns peak output level (0.0 .. 1.0) for UI telemetry metering.
    float processInterleaved(float* stereoBuffer, size_t frameCount, const DspParamSnapshot& params) noexcept;

    bool isLimiting() const noexcept { return limiter_.didLimitRecently(); }
    double sampleRate() const noexcept { return sampleRate_; }

private:
    double sampleRate_ = 48000.0;

    DelayLine delayL_;
    DelayLine delayR_;
    AnalogFilter filterL_;
    AnalogFilter filterR_;
    ModLfo modLfo_;
    SoftLimiter limiter_;

    // 20ms exponential parameter smoothers (prevents clicks & zipper noise)
    float smoothCoeff_ = 0.001f;
    float smoothEffectGain_ = 0.0f;
    float smoothFeedback_ = 0.35f;
    float smoothDelaySamples_ = 20160.0f;
    float smoothWetSend_ = 1.0f; // Drops to 0 on bypass so existing trails decay naturally
};

} // namespace dd7
