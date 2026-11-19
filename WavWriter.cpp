#include "io/WavWriter.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

namespace dd7 {

namespace {
void putLe16(uint8_t* dst, uint16_t v) {
    dst[0] = static_cast<uint8_t>(v & 0xFF);
    dst[1] = static_cast<uint8_t>((v >> 8) & 0xFF);
}
void putLe32(uint8_t* dst, uint32_t v) {
    dst[0] = static_cast<uint8_t>(v & 0xFF);
    dst[1] = static_cast<uint8_t>((v >> 8) & 0xFF);
    dst[2] = static_cast<uint8_t>((v >> 16) & 0xFF);
    dst[3] = static_cast<uint8_t>((v >> 24) & 0xFF);
}
} // namespace

WavWriter::~WavWriter() {
    if (file_) {
        std::fclose(file_);
        file_ = nullptr;
    }
}

bool WavWriter::open(const std::string& path, int32_t sampleRate, int32_t channels) {
    if (file_) {
        std::fclose(file_);
        file_ = nullptr;
    }
    sampleRate_ = sampleRate;
    channels_ = channels;
    dataBytes_ = 0;

    file_ = std::fopen(path.c_str(), "wb");
    if (!file_) return false;
    return writeHeader();
}

bool WavWriter::writeHeader() {
    if (!file_) return false;
    uint8_t hdr[44];
    std::memcpy(hdr + 0,  "RIFF", 4);
    putLe32(hdr + 4, 36 + dataBytes_);
    std::memcpy(hdr + 8,  "WAVE", 4);
    std::memcpy(hdr + 12, "fmt ", 4);
    putLe32(hdr + 16, 16); // PCM chunk size
    putLe16(hdr + 20, 1);  // AudioFormat = 1 (PCM)
    putLe16(hdr + 22, static_cast<uint16_t>(channels_));
    putLe32(hdr + 24, static_cast<uint32_t>(sampleRate_));
    const uint32_t byteRate = static_cast<uint32_t>(sampleRate_ * channels_ * 2);
    putLe32(hdr + 28, byteRate);
    putLe16(hdr + 32, static_cast<uint16_t>(channels_ * 2)); // BlockAlign
    putLe16(hdr + 34, 16); // BitsPerSample
    std::memcpy(hdr + 36, "data", 4);
    putLe32(hdr + 40, dataBytes_);

    if (std::fseek(file_, 0, SEEK_SET) != 0) return false;
    return std::fwrite(hdr, 1, 44, file_) == 44;
}

bool WavWriter::writeFloatFrames(const float* interleaved, size_t frameCount) {
    if (!file_ || !interleaved || frameCount == 0) return false;

    const size_t sampleCount = frameCount * static_cast<size_t>(channels_);
    std::vector<int16_t> pcm(sampleCount);
    for (size_t i = 0; i < sampleCount; ++i) {
        const float clamped = std::clamp(interleaved[i], -1.0f, 1.0f);
        pcm[i] = static_cast<int16_t>(std::lrintf(clamped * 32767.0f));
    }

    const size_t written = std::fwrite(pcm.data(), sizeof(int16_t), sampleCount, file_);
    if (written != sampleCount) return false;
    dataBytes_ += static_cast<uint32_t>(sampleCount * sizeof(int16_t));
    return true;
}

bool WavWriter::finalize() {
    if (!file_) return false;
    const bool ok = writeHeader();
    std::fflush(file_);
    std::fclose(file_);
    file_ = nullptr;
    return ok;
}

void WavWriter::closeAndDelete(const std::string& path) {
    if (file_) {
        std::fclose(file_);
        file_ = nullptr;
    }
    std::remove(path.c_str());
}

} // namespace dd7
