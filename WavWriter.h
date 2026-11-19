#pragma once

#include <cstdint>
#include <cstdio>
#include <string>

namespace dd7 {

// Zero-dependency 16-bit PCM stereo WAV writer
class WavWriter {
public:
    WavWriter() = default;
    ~WavWriter();

    bool open(const std::string& path, int32_t sampleRate, int32_t channels = 2);
    bool writeFloatFrames(const float* interleaved, size_t frameCount);
    bool finalize();
    void closeAndDelete(const std::string& path);

    uint64_t totalBytesWritten() const noexcept { return 44 + dataBytes_; }

private:
    bool writeHeader();

    std::FILE* file_ = nullptr;
    int32_t sampleRate_ = 48000;
    int32_t channels_ = 2;
    uint32_t dataBytes_ = 0;
};

} // namespace dd7
