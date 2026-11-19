#pragma once

#include <atomic>
#include <algorithm>
#include <cmath>
#include <cstdint>

namespace dd7 {

enum class DelayMode : int32_t {
    MS_50   = 0,
    MS_200  = 1,
    MS_800  = 2,
    MS_3200 = 3,
    ANALOG  = 4,
    MODULATE = 5
};

// Parameter IDs matching Kotlin NativeAudioBridge constants
enum class ParamId : int32_t {
    E_LEVEL         = 0,
    F_BACK          = 1,
    D_TIME_MS       = 2,
    MODE            = 3,
    PING_PONG       = 4,
    BYPASS          = 5,
    PING_PONG_WIDTH = 6,
    ANTI_BOOM_CUT   = 7
};

struct DspParamSnapshot {
    float effectLevel   = 0.45f;   // 0.0 .. 1.0 (logarithmic hardware taper applied in engine)
    float feedback      = 0.35f;   // 0.0 .. 0.95 (hard safety cap)
    float delayTimeMs   = 420.0f;  // 1.0 .. 3200.0 ms
    DelayMode mode      = DelayMode::ANALOG;
    bool pingPong       = false;
    bool bypassed       = false;
    float pingPongWidth = 0.85f;   // 0.0 .. 1.0
    bool antiBoomCut    = true;    // 180 Hz HPF + 360 Hz notch on wet bus
};

// Lock-free atomic container shared between UI thread (JNI) and Oboe real-time audio thread
struct AtomicDspParams {
    std::atomic<float> effectLevel{0.45f};
    std::atomic<float> feedback{0.35f};
    std::atomic<float> delayTimeMs{420.0f};
    std::atomic<int32_t> mode{static_cast<int32_t>(DelayMode::ANALOG)};
    std::atomic<bool> pingPong{false};
    std::atomic<bool> bypassed{false};
    std::atomic<float> pingPongWidth{0.85f};
    std::atomic<bool> antiBoomCut{true};

    void setParam(ParamId id, float value) noexcept {
        switch (id) {
            case ParamId::E_LEVEL:
                effectLevel.store(std::clamp(value, 0.0f, 1.0f), std::memory_order_relaxed);
                break;
            case ParamId::F_BACK:
                // Safety rule: cap internal feedback at 0.95 to prevent runaway self-oscillation
                feedback.store(std::clamp(value, 0.0f, 0.95f), std::memory_order_relaxed);
                break;
            case ParamId::D_TIME_MS:
                delayTimeMs.store(std::clamp(value, 1.0f, 3200.0f), std::memory_order_relaxed);
                break;
            case ParamId::MODE:
                mode.store(std::clamp(static_cast<int32_t>(std::lround(value)), 0, 5), std::memory_order_relaxed);
                break;
            case ParamId::PING_PONG:
                pingPong.store(value >= 0.5f, std::memory_order_relaxed);
                break;
            case ParamId::BYPASS:
                bypassed.store(value >= 0.5f, std::memory_order_relaxed);
                break;
            case ParamId::PING_PONG_WIDTH:
                pingPongWidth.store(std::clamp(value, 0.0f, 1.0f), std::memory_order_relaxed);
                break;
            case ParamId::ANTI_BOOM_CUT:
                antiBoomCut.store(value >= 0.5f, std::memory_order_relaxed);
                break;
        }
    }

    DspParamSnapshot loadSnapshot() const noexcept {
        DspParamSnapshot s;
        s.effectLevel   = effectLevel.load(std::memory_order_relaxed);
        s.feedback      = feedback.load(std::memory_order_relaxed);
        s.delayTimeMs   = delayTimeMs.load(std::memory_order_relaxed);
        s.mode          = static_cast<DelayMode>(mode.load(std::memory_order_relaxed));
        s.pingPong      = pingPong.load(std::memory_order_relaxed);
        s.bypassed      = bypassed.load(std::memory_order_relaxed);
        s.pingPongWidth = pingPongWidth.load(std::memory_order_relaxed);
        s.antiBoomCut   = antiBoomCut.load(std::memory_order_relaxed);
        return s;
    }
};

// Mode-clamped delay time bounds matching BOSS DD-7 Owner's Manual
inline float clampDelayForMode(float requestedMs, DelayMode mode) noexcept {
    switch (mode) {
        case DelayMode::MS_50:    return std::clamp(requestedMs, 1.0f, 50.0f);
        case DelayMode::MS_200:   return std::clamp(requestedMs, 1.0f, 200.0f);
        case DelayMode::MS_800:   return std::clamp(requestedMs, 1.0f, 800.0f);
        case DelayMode::MS_3200:  return std::clamp(requestedMs, 1.0f, 3200.0f);
        case DelayMode::ANALOG:   return std::clamp(requestedMs, 1.0f, 800.0f);
        case DelayMode::MODULATE: return std::clamp(requestedMs, 1.0f, 800.0f);
    }
    return std::clamp(requestedMs, 1.0f, 3200.0f);
}

// Logarithmic hardware taper for E.LEVEL knob (smooth musical feel at low settings)
inline float hardwareEffectLevelGain(float normalized) noexcept {
    const float x = std::clamp(normalized, 0.0f, 1.0f);
    return x * x * 1.15f; // up to ~+1.2 dB wet boost at max, protected by soft limiter
}

} // namespace dd7
