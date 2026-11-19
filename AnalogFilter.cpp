#include "dsp/AnalogFilter.h"
#include <cmath>

namespace dd7 {

namespace {
constexpr double kPi = 3.14159265358979323846;
}

void Biquad::setHighPass(double sampleRate, double freqHz, double q) {
    const double w0 = 2.0 * kPi * freqHz / sampleRate;
    const double cosW0 = std::cos(w0);
    const double alpha = std::sin(w0) / (2.0 * q);
    const double a0 = 1.0 + alpha;

    b0 = static_cast<float>(((1.0 + cosW0) * 0.5) / a0);
    b1 = static_cast<float>((-(1.0 + cosW0)) / a0);
    b2 = static_cast<float>(((1.0 + cosW0) * 0.5) / a0);
    a1 = static_cast<float>((-2.0 * cosW0) / a0);
    a2 = static_cast<float>((1.0 - alpha) / a0);
}

void Biquad::setLowPass(double sampleRate, double freqHz, double q) {
    const double w0 = 2.0 * kPi * freqHz / sampleRate;
    const double cosW0 = std::cos(w0);
    const double alpha = std::sin(w0) / (2.0 * q);
    const double a0 = 1.0 + alpha;

    b0 = static_cast<float>(((1.0 - cosW0) * 0.5) / a0);
    b1 = static_cast<float>((1.0 - cosW0) / a0);
    b2 = static_cast<float>(((1.0 - cosW0) * 0.5) / a0);
    a1 = static_cast<float>((-2.0 * cosW0) / a0);
    a2 = static_cast<float>((1.0 - alpha) / a0);
}

void Biquad::setNotch(double sampleRate, double freqHz, double q) {
    const double w0 = 2.0 * kPi * freqHz / sampleRate;
    const double cosW0 = std::cos(w0);
    const double alpha = std::sin(w0) / (2.0 * q);
    const double a0 = 1.0 + alpha;

    b0 = static_cast<float>(1.0 / a0);
    b1 = static_cast<float>((-2.0 * cosW0) / a0);
    b2 = static_cast<float>(1.0 / a0);
    a1 = static_cast<float>((-2.0 * cosW0) / a0);
    a2 = static_cast<float>((1.0 - alpha) / a0);
}

void AnalogFilter::prepare(double sampleRate) {
    hpf180_.setHighPass(sampleRate, 180.0, 0.7071);
    notch360_.setNotch(sampleRate, 360.0, 2.2);
    sibilanceLpf6k_.setLowPass(sampleRate, 6000.0, 0.7071);
    dm2Lpf2800_.setLowPass(sampleRate, 2800.0, 0.75);
    reset();
}

void AnalogFilter::reset() noexcept {
    hpf180_.reset();
    notch360_.reset();
    sibilanceLpf6k_.reset();
    dm2Lpf2800_.reset();
}

float AnalogFilter::processWetInput(float x, bool antiBoomEnabled) noexcept {
    if (!antiBoomEnabled) return x;
    return notch360_.process(hpf180_.process(x));
}

float AnalogFilter::processFeedback(float x, bool analogMode) noexcept {
    // Permanent 6 kHz high-frequency damping keeps "S/Sh" sibilance smooth on long Naat repeats
    float y = sibilanceLpf6k_.process(x);
    if (analogMode) {
        // BOSS DM-2 warm BBD roll-off + gentle soft-clip saturation
        y = dm2Lpf2800_.process(y);
        y = std::tanh(y * 1.35f) / 1.35f;
    }
    return y;
}

} // namespace dd7
