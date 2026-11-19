#pragma once

namespace dd7 {

// Subtle sine LFO for MODULATE mode chorus shimmer on delayed repeats
class ModLfo {
public:
    void prepare(double sampleRate, float rateHz = 0.85f, float depthMs = 2.4f);
    void reset() noexcept { phase_ = 0.0; }

    // Returns delay modulation offset in samples
    float nextOffsetSamples() noexcept;

private:
    double phase_ = 0.0;
    double phaseInc_ = 0.0;
    float depthSamples_ = 0.0f;
};

} // namespace dd7
