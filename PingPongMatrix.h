#pragma once

namespace dd7 {

// Routes stereo feedback either straight (L->L, R->R) or cross-coupled Ping-Pong (L->R->L)
// with adjustable stereo spread width.
class PingPongMatrix {
public:
    struct StereoSample {
        float left;
        float right;
    };

    // Compute what enters the Left and Right delay lines
    static StereoSample computeDelayInputs(
        float wetInL,
        float wetInR,
        float fbTapL,
        float fbTapR,
        float feedbackGain,
        bool pingPongEnabled
    ) noexcept;

    // Shape stereo width of wet output
    static StereoSample applyWidth(float wetL, float wetR, float width) noexcept;
};

} // namespace dd7
