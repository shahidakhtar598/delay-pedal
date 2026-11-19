#pragma once

namespace dd7 {

// Biquad IIR filter (Direct Form II Transposed) for real-time audio safety
struct Biquad {
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float a1 = 0.0f, a2 = 0.0f;
    float z1 = 0.0f, z2 = 0.0f;

    void reset() noexcept { z1 = 0.0f; z2 = 0.0f; }

    inline float process(float x) noexcept {
        const float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    void setHighPass(double sampleRate, double freqHz, double q = 0.7071);
    void setLowPass(double sampleRate, double freqHz, double q = 0.7071);
    void setNotch(double sampleRate, double freqHz, double q = 2.0);
};

// Wet-bus vocal conditioning + DM-2 warm analog feedback modeling
class AnalogFilter {
public:
    void prepare(double sampleRate);
    void reset() noexcept;

    // Input conditioning on wet send: 180 Hz HPF + 360 Hz Anti-Boom Notch
    float processWetInput(float x, bool antiBoomEnabled) noexcept;

    // Feedback-loop filter: permanent 6 kHz sibilance damping; plus DM-2 2.8 kHz LPF + warm saturation in ANALOG mode
    float processFeedback(float x, bool analogMode) noexcept;

private:
    Biquad hpf180_;
    Biquad notch360_;
    Biquad sibilanceLpf6k_;
    Biquad dm2Lpf2800_;
};

} // namespace dd7
