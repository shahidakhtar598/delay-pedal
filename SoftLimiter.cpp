#include "dsp/SoftLimiter.h"
#include <algorithm>
#include <cmath>

namespace dd7 {

void SoftLimiter::prepare(double sampleRate) {
    ceiling_ = std::pow(10.0f, -0.3f / 20.0f); // ~0.96605 (-0.3 dBFS)
    attackCoeff_  = std::exp(-1.0f / static_cast<float>(sampleRate * 0.0005)); // 0.5ms fast lookahead-free attack
    releaseCoeff_ = std::exp(-1.0f / static_cast<float>(sampleRate * 0.060));  // 60ms smooth vocal release
    reset();
}

void SoftLimiter::reset() noexcept {
    envelope_ = 0.0f;
    clipGuardFrames_ = 0;
}

void SoftLimiter::processStereo(float& left, float& right) noexcept {
    const float peak = std::max(std::fabs(left), std::fabs(right));
    if (peak > envelope_) {
        envelope_ = attackCoeff_ * envelope_ + (1.0f - attackCoeff_) * peak;
    } else {
        envelope_ = releaseCoeff_ * envelope_ + (1.0f - releaseCoeff_) * peak;
    }

    float gain = 1.0f;
    if (envelope_ > ceiling_) {
        gain = ceiling_ / envelope_;
        clipGuardFrames_ = 2400; // ~50ms visual blink hold
    } else if (clipGuardFrames_ > 0) {
        --clipGuardFrames_;
    }

    // Soft-knee saturation safeguard guarantees output never exceeds ceiling
    auto softClip = [this](float s) noexcept -> float {
        const float scaled = s;
        if (std::fabs(scaled) <= ceiling_) return scaled;
        return ceiling_ * std::tanh(scaled / ceiling_);
    };

    left  = softClip(left * gain);
    right = softClip(right * gain);
}

} // namespace dd7
