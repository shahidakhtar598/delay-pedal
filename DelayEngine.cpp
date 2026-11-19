#include "dsp/DelayEngine.h"
#include <algorithm>
#include <cmath>

namespace dd7 {

void DelayEngine::prepare(double sampleRate) {
    sampleRate_ = (sampleRate > 8000.0) ? sampleRate : 48000.0;
    delayL_.prepare(sampleRate_, 3300.0f);
    delayR_.prepare(sampleRate_, 3300.0f);
    filterL_.prepare(sampleRate_);
    filterR_.prepare(sampleRate_);
    modLfo_.prepare(sampleRate_, 0.85f, 2.2f);
    limiter_.prepare(sampleRate_);

    // ~20ms exponential smoothing time constant
    smoothCoeff_ = 1.0f - std::exp(-1.0f / static_cast<float>(sampleRate_ * 0.020));
    reset();
}

void DelayEngine::reset() noexcept {
    delayL_.reset();
    delayR_.reset();
    filterL_.reset();
    filterR_.reset();
    modLfo_.reset();
    limiter_.reset();
    smoothEffectGain_ = 0.0f;
    smoothFeedback_ = 0.35f;
    smoothDelaySamples_ = static_cast<float>(sampleRate_ * 0.420);
    smoothWetSend_ = 1.0f;
}

float DelayEngine::processInterleaved(
    float* stereoBuffer,
    size_t frameCount,
    const DspParamSnapshot& params
) noexcept {
    if (!stereoBuffer || frameCount == 0) return 0.0f;

    const float targetEffectGain = hardwareEffectLevelGain(params.effectLevel);
    const float targetFeedback   = std::clamp(params.feedback, 0.0f, 0.95f);
    const float clampedMs        = clampDelayForMode(params.delayTimeMs, params.mode);
    const float targetDelaySamp  = static_cast<float>(sampleRate_ * (clampedMs / 1000.0));
    // Trails bypass: mute wet send input when bypassed so active echo tails decay smoothly
    const float targetWetSend    = params.bypassed ? 0.0f : 1.0f;

    const bool isAnalog   = (params.mode == DelayMode::ANALOG);
    const bool isModulate = (params.mode == DelayMode::MODULATE);

    float blockPeak = 0.0f;

    for (size_t i = 0; i < frameCount; ++i) {
        // Exponential smoothing per sample
        smoothEffectGain_   += smoothCoeff_ * (targetEffectGain - smoothEffectGain_);
        smoothFeedback_     += smoothCoeff_ * (targetFeedback - smoothFeedback_);
        smoothDelaySamples_ += smoothCoeff_ * (targetDelaySamp - smoothDelaySamples_);
        smoothWetSend_      += smoothCoeff_ * (targetWetSend - smoothWetSend_);

        const float dryL = stereoBuffer[i * 2];
        const float dryR = stereoBuffer[i * 2 + 1];

        // 1. Wet send conditioning (180 Hz HPF + 360 Hz anti-boom notch)
        const float sendL = filterL_.processWetInput(dryL * smoothWetSend_, params.antiBoomCut);
        const float sendR = filterR_.processWetInput(dryR * smoothWetSend_, params.antiBoomCut);

        // 2. Compute modulated fractional read delay
        const float lfoOffset = isModulate ? modLfo_.nextOffsetSamples() : 0.0f;
        const float readDelayL = std::max(1.0f, smoothDelaySamples_ + lfoOffset);
        const float readDelayR = std::max(1.0f, smoothDelaySamples_ - lfoOffset * 0.75f);

        // 3. Read current delayed taps with Hermite interpolation
        const float tapL = delayL_.readHermite(readDelayL);
        const float tapR = delayR_.readHermite(readDelayR);

        // 4. Apply feedback-loop filtering (6 kHz sibilance damping + DM-2 analog warmth)
        const float fbL = filterL_.processFeedback(tapL, isAnalog);
        const float fbR = filterR_.processFeedback(tapR, isAnalog);

        // 5. Stereo / Ping-Pong cross-feedback matrix
        const auto nextIn = PingPongMatrix::computeDelayInputs(
            sendL, sendR, fbL, fbR, smoothFeedback_, params.pingPong
        );
        delayL_.push(nextIn.left);
        delayR_.push(nextIn.right);

        // 6. Stereo width & parallel dry/wet summing (Dry vocal stays 100% untouched!)
        const auto wetStereo = PingPongMatrix::applyWidth(tapL, tapR, params.pingPong ? params.pingPongWidth : 1.0f);
        float outL = dryL + wetStereo.left  * smoothEffectGain_;
        float outR = dryR + wetStereo.right * smoothEffectGain_;

        // 7. Master -0.3 dBFS soft-ceiling limiter
        limiter_.processStereo(outL, outR);

        stereoBuffer[i * 2]     = outL;
        stereoBuffer[i * 2 + 1] = outR;

        const float framePeak = std::max(std::fabs(outL), std::fabs(outR));
        if (framePeak > blockPeak) blockPeak = framePeak;
    }

    return blockPeak;
}

} // namespace dd7
