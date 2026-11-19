#pragma once

namespace dd7 {

// Master -0.3 dBFS soft-ceiling anti-clipping limiter.
// Prevents digital clipping and speaker damage even at maximum E.LEVEL and F.BACK.
class SoftLimiter {
public:
    void prepare(double sampleRate);
    void reset() noexcept;

    void processStereo(float& left, float& right) noexcept;
    bool didLimitRecently() const noexcept { return clipGuardFrames_ > 0; }

private:
    float ceiling_ = 0.96605f; // -0.3 dBFS
    float envelope_ = 0.0f;
    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;
    int clipGuardFrames_ = 0;
};

} // namespace dd7
