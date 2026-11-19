#include "dsp/PingPongMatrix.h"
#include <algorithm>

namespace dd7 {

PingPongMatrix::StereoSample PingPongMatrix::computeDelayInputs(
    float wetInL,
    float wetInR,
    float fbTapL,
    float fbTapR,
    float feedbackGain,
    bool pingPongEnabled
) noexcept {
    if (pingPongEnabled) {
        // Mono sum injected into Left first -> bounces to Right -> crosses back to Left
        const float monoIn = 0.5f * (wetInL + wetInR);
        return {
            monoIn + fbTapR * feedbackGain,
            fbTapL * feedbackGain
        };
    }
    return {
        wetInL + fbTapL * feedbackGain,
        wetInR + fbTapR * feedbackGain
    };
}

PingPongMatrix::StereoSample PingPongMatrix::applyWidth(float wetL, float wetR, float width) noexcept {
    const float w = std::clamp(width, 0.0f, 1.0f);
    const float mid = 0.5f * (wetL + wetR);
    const float side = 0.5f * (wetL - wetR) * w;
    return { mid + side, mid - side };
}

} // namespace dd7
