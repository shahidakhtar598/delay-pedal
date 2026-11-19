#include "dsp/DelayLine.h"
#include <algorithm>
#include <cmath>

namespace dd7 {

void DelayLine::prepare(double sampleRate, float maxDelayMs) {
    const size_t needed = static_cast<size_t>(std::ceil(sampleRate * (maxDelayMs / 1000.0))) + 16;
    size_t powerOfTwo = 1;
    while (powerOfTwo < needed) {
        powerOfTwo <<= 1;
    }
    buffer_.assign(powerOfTwo, 0.0f);
    mask_ = powerOfTwo - 1;
    writeIndex_ = 0;
    maxSamples_ = powerOfTwo - 4;
}

void DelayLine::reset() noexcept {
    std::fill(buffer_.begin(), buffer_.end(), 0.0f);
    writeIndex_ = 0;
}

float DelayLine::readHermite(float delaySamples) const noexcept {
    if (buffer_.empty()) return 0.0f;

    const float clamped = std::clamp(delaySamples, 1.0f, static_cast<float>(maxSamples_));
    const float readPos = static_cast<float>(writeIndex_) - clamped;
    const float floored = std::floor(readPos);
    const float frac = readPos - floored;

    // Wrap integer index safely into power-of-two ring buffer
    const int64_t base = static_cast<int64_t>(floored);
    const size_t i0 = static_cast<size_t>(base - 1) & mask_;
    const size_t i1 = static_cast<size_t>(base)     & mask_;
    const size_t i2 = static_cast<size_t>(base + 1) & mask_;
    const size_t i3 = static_cast<size_t>(base + 2) & mask_;

    const float y0 = buffer_[i0];
    const float y1 = buffer_[i1];
    const float y2 = buffer_[i2];
    const float y3 = buffer_[i3];

    // 4-point, 3rd-order Hermite polynomial interpolation
    const float c0 = y1;
    const float c1 = 0.5f * (y2 - y0);
    const float c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3;
    const float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);

    return ((c3 * frac + c2) * frac + c1) * frac + c0;
}

} // namespace dd7
