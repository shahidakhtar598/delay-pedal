#pragma once

#include <cstddef>
#include <vector>

namespace dd7 {

// Lock-free circular delay line with 4-point Hermite fractional interpolation.
// Pre-allocated once during prepare(); zero allocations during audio processing.
class DelayLine {
public:
    void prepare(double sampleRate, float maxDelayMs = 3300.0f);
    void reset() noexcept;

    // Write a single sample into the circular buffer
    inline void push(float sample) noexcept {
        buffer_[writeIndex_] = sample;
        writeIndex_ = (writeIndex_ + 1) & mask_;
    }

    // Read a fractionally delayed sample (in samples) using 4-point Hermite interpolation
    float readHermite(float delaySamples) const noexcept;

    size_t maxDelaySamples() const noexcept { return maxSamples_; }

private:
    std::vector<float> buffer_;
    size_t mask_ = 0;
    size_t writeIndex_ = 0;
    size_t maxSamples_ = 0;
};

} // namespace dd7
