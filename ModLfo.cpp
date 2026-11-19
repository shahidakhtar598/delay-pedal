#include "dsp/ModLfo.h"
#include <cmath>

namespace dd7 {

namespace {
constexpr double kTwoPi = 6.28318530717958647692;
}

void ModLfo::prepare(double sampleRate, float rateHz, float depthMs) {
    phase_ = 0.0;
    phaseInc_ = kTwoPi * static_cast<double>(rateHz) / sampleRate;
    depthSamples_ = static_cast<float>(sampleRate * (depthMs / 1000.0));
}

float ModLfo::nextOffsetSamples() noexcept {
    const float mod = static_cast<float>(std::sin(phase_)) * depthSamples_;
    phase_ += phaseInc_;
    if (phase_ >= kTwoPi) {
        phase_ -= kTwoPi;
    }
    return mod;
}

} // namespace dd7
