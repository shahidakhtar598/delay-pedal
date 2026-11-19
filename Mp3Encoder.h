#pragma once

#include <cstdint>
#include <cstdio>
#include <string>

namespace dd7 {

// LGPL-compliant dynamic wrapper around libmp3lame.so (loaded via dlopen at runtime).
class Mp3Encoder {
public:
    Mp3Encoder() = default;
    ~Mp3Encoder();

    bool open(const std::string& path, int32_t sampleRate, int32_t bitrateKbps);
    bool writeFloatFrames(const float* interleaved, size_t frameCount);
    bool finalize();
    void closeAndDelete(const std::string& path);

    uint64_t totalBytesWritten() const noexcept { return totalBytes_; }
    const std::string& lastError() const noexcept { return lastError_; }

private:
    bool loadLameSymbols();
    void unloadLame();

    void* dlHandle_ = nullptr;
    void* lameFlags_ = nullptr;
    std::FILE* file_ = nullptr;
    uint64_t totalBytes_ = 0;
    std::string lastError_;

    // Function pointers resolved dynamically from libmp3lame.so
    using InitFn = void* (*)();
    using SetIntFn = int (*)(void*, int);
    using InitParamsFn = int (*)(void*);
    using EncodeInterleavedFn = int (*)(void*, short int*, int, unsigned char*, int);
    using FlushFn = int (*)(void*, unsigned char*, int);
    using CloseFn = int (*)(void*);

    InitFn fnInit_ = nullptr;
    SetIntFn fnSetNumChannels_ = nullptr;
    SetIntFn fnSetInSampleRate_ = nullptr;
    SetIntFn fnSetBrate_ = nullptr;
    SetIntFn fnSetQuality_ = nullptr;
    InitParamsFn fnInitParams_ = nullptr;
    EncodeInterleavedFn fnEncodeInterleaved_ = nullptr;
    FlushFn fnFlush_ = nullptr;
    CloseFn fnClose_ = nullptr;
};

} // namespace dd7
