#include "dsp/DelayEngine.h"
#include "io/OfflineRenderer.h"
#include <atomic>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <iostream>
#include <vector>

using namespace dd7;

namespace {

void testHermiteDelayLine() {
    DelayLine dl;
    dl.prepare(48000.0, 500.0f);
    // Push impulse at sample 0
    dl.push(1.0f);
    for (int i = 1; i < 100; ++i) dl.push(0.0f);

    // After 100 pushes, sample 0 is 100 samples behind writeIndex
    const float tap = dl.readHermite(100.0f);
    assert(std::fabs(tap - 1.0f) < 1e-4f);
    std::cout << "[PASS] testHermiteDelayLine (tap=" << tap << ")\n";
}

void testPingPongAlternation() {
    DelayEngine engine;
    engine.prepare(48000.0);

    DspParamSnapshot p;
    p.effectLevel = 0.85f;
    p.feedback = 0.6f;
    p.delayTimeMs = 100.0f; // 4800 frames
    p.mode = DelayMode::MS_200;
    p.pingPong = true;
    p.pingPongWidth = 1.0f;
    p.antiBoomCut = false;

    // 0.35s buffer = 16800 frames; short vocal burst in first 500 frames
    std::vector<float> buf(16800 * 2, 0.0f);
    for (size_t i = 0; i < 500; ++i) {
        const float s = 0.5f * std::sin(2.0f * 3.14159265f * 440.0f * static_cast<float>(i) / 48000.0f);
        buf[i * 2] = s;
        buf[i * 2 + 1] = s;
    }

    engine.processInterleaved(buf.data(), 16800, p);

    // Measure first repeat (~4800..5300) and second repeat (~9600..10100)
    float r1L = 0.0f, r1R = 0.0f, r2L = 0.0f, r2R = 0.0f;
    for (size_t i = 4800; i < 5300; ++i) {
        r1L += std::fabs(buf[i * 2]);
        r1R += std::fabs(buf[i * 2 + 1]);
    }
    for (size_t i = 9600; i < 10100; ++i) {
        r2L += std::fabs(buf[i * 2]);
        r2R += std::fabs(buf[i * 2 + 1]);
    }
    assert(r1L > r1R * 3.0f); // First echo clearly on Left
    assert(r2R > r2L * 3.0f); // Second echo bounces clearly to Right
    std::cout << "[PASS] testPingPongAlternation (R1 L=" << r1L << " R=" << r1R
              << " | R2 L=" << r2L << " R=" << r2R << ")\n";
}

void testSoftLimiterSafetyAtMaxFeedback() {
    DelayEngine engine;
    engine.prepare(48000.0);

    DspParamSnapshot p;
    p.effectLevel = 1.0f;
    p.feedback = 1.5f; // Intentionally abusive > 1.0 (engine must clamp to 0.95)
    p.delayTimeMs = 50.0f;
    p.mode = DelayMode::ANALOG;

    std::vector<float> buf(48000 * 2, 0.0f);
    for (size_t i = 0; i < 48000; ++i) {
        const float s = 0.95f * std::sin(2.0f * 3.14159265f * 1000.0f * static_cast<float>(i) / 48000.0f);
        buf[i * 2] = s;
        buf[i * 2 + 1] = s;
    }

    const float peak = engine.processInterleaved(buf.data(), 48000, p);
    assert(std::isfinite(peak));
    assert(peak <= 0.967f); // -0.3 dBFS ceiling
    std::cout << "[PASS] testSoftLimiterSafetyAtMaxFeedback (peak=" << peak << ")\n";
}

void testOfflineRendererWavAndTail() {
    const std::string inPath = "/tmp/dd7_test_input.pcm";
    const std::string outPath = "/tmp/dd7_test_output.wav";

    // Write 1.0 second of 440Hz stereo float PCM @ 48kHz
    std::vector<float> dry(48000 * 2, 0.0f);
    for (size_t i = 0; i < 48000; ++i) {
        const float env = (i < 24000) ? 0.6f : 0.0f;
        const float s = env * std::sin(2.0f * 3.14159265f * 440.0f * static_cast<float>(i) / 48000.0f);
        dry[i * 2] = s;
        dry[i * 2 + 1] = s;
    }
    std::FILE* f = std::fopen(inPath.c_str(), "wb");
    assert(f != nullptr);
    std::fwrite(dry.data(), sizeof(float), dry.size(), f);
    std::fclose(f);

    OfflineExportConfig cfg;
    cfg.inputPcmPath = inPath;
    cfg.outputFilePath = outPath;
    cfg.sampleRate = 48000;
    cfg.format = ExportFormat::WAV_16;
    cfg.maxDurationSec = -1;
    cfg.dspParams.effectLevel = 0.6f;
    cfg.dspParams.feedback = 0.45f;
    cfg.dspParams.delayTimeMs = 300.0f;
    cfg.dspParams.mode = DelayMode::MODULATE;
    cfg.dspParams.pingPong = true;

    std::atomic<bool> cancel{false};
    int lastPct = 0;
    const auto res = OfflineRenderer::render(cfg, cancel, [&](int32_t pct) { lastPct = pct; });

    assert(res.success);
    assert(lastPct == 100);
    assert(res.tailMs >= 500);
    assert(res.fileBytes > 44 + 48000 * 4); // Larger than dry 1s WAV due to preserved echo tail

    std::remove(inPath.c_str());
    std::remove(outPath.c_str());
    std::cout << "[PASS] testOfflineRendererWavAndTail (bytes=" << res.fileBytes
              << ", tailMs=" << res.tailMs << ")\n";
}

} // namespace

int main() {
    testHermiteDelayLine();
    testPingPongAlternation();
    testSoftLimiterSafetyAtMaxFeedback();
    testOfflineRendererWavAndTail();
    std::cout << "ALL C++20 DSP & OFFLINE RENDERER TESTS PASSED!\n";
    return 0;
}
