#include "io/Mp3Encoder.h"
#include <algorithm>
#include <cmath>
#include <dlfcn.h>
#include <vector>

namespace dd7 {

Mp3Encoder::~Mp3Encoder() {
    if (file_) {
        std::fclose(file_);
        file_ = nullptr;
    }
    unloadLame();
}

bool Mp3Encoder::loadLameSymbols() {
    if (dlHandle_) return true;
    dlHandle_ = dlopen("libmp3lame.so", RTLD_NOW);
    if (!dlHandle_) {
        lastError_ = "MP3_ENCODER_LIBRARY_MISSING";
        return false;
    }
    fnInit_              = reinterpret_cast<InitFn>(dlsym(dlHandle_, "lame_init"));
    fnSetNumChannels_    = reinterpret_cast<SetIntFn>(dlsym(dlHandle_, "lame_set_num_channels"));
    fnSetInSampleRate_   = reinterpret_cast<SetIntFn>(dlsym(dlHandle_, "lame_set_in_samplerate"));
    fnSetBrate_          = reinterpret_cast<SetIntFn>(dlsym(dlHandle_, "lame_set_brate"));
    fnSetQuality_        = reinterpret_cast<SetIntFn>(dlsym(dlHandle_, "lame_set_quality"));
    fnInitParams_        = reinterpret_cast<InitParamsFn>(dlsym(dlHandle_, "lame_init_params"));
    fnEncodeInterleaved_ = reinterpret_cast<EncodeInterleavedFn>(dlsym(dlHandle_, "lame_encode_buffer_interleaved"));
    fnFlush_             = reinterpret_cast<FlushFn>(dlsym(dlHandle_, "lame_encode_flush"));
    fnClose_             = reinterpret_cast<CloseFn>(dlsym(dlHandle_, "lame_close"));

    if (!fnInit_ || !fnSetNumChannels_ || !fnSetInSampleRate_ || !fnSetBrate_ ||
        !fnSetQuality_ || !fnInitParams_ || !fnEncodeInterleaved_ || !fnFlush_ || !fnClose_) {
        lastError_ = "MP3_ENCODER_SYMBOL_MISMATCH";
        unloadLame();
        return false;
    }
    return true;
}

void Mp3Encoder::unloadLame() {
    if (lameFlags_ && fnClose_) {
        fnClose_(lameFlags_);
        lameFlags_ = nullptr;
    }
    if (dlHandle_) {
        dlclose(dlHandle_);
        dlHandle_ = nullptr;
    }
}

bool Mp3Encoder::open(const std::string& path, int32_t sampleRate, int32_t bitrateKbps) {
    totalBytes_ = 0;
    lastError_.clear();

    if (!loadLameSymbols()) return false;

    lameFlags_ = fnInit_();
    if (!lameFlags_) {
        lastError_ = "LAME_INIT_FAILED";
        return false;
    }

    fnSetNumChannels_(lameFlags_, 2);
    fnSetInSampleRate_(lameFlags_, sampleRate);
    fnSetBrate_(lameFlags_, bitrateKbps);
    fnSetQuality_(lameFlags_, 2); // High quality, fast encoding

    if (fnInitParams_(lameFlags_) < 0) {
        lastError_ = "LAME_PARAMS_FAILED";
        unloadLame();
        return false;
    }

    file_ = std::fopen(path.c_str(), "wb");
    if (!file_) {
        lastError_ = "OUTPUT_OPEN_FAILED";
        unloadLame();
        return false;
    }
    return true;
}

bool Mp3Encoder::writeFloatFrames(const float* interleaved, size_t frameCount) {
    if (!file_ || !lameFlags_ || !interleaved || frameCount == 0) return false;

    std::vector<short int> pcm(frameCount * 2);
    for (size_t i = 0; i < frameCount * 2; ++i) {
        const float clamped = std::clamp(interleaved[i], -1.0f, 1.0f);
        pcm[i] = static_cast<short int>(std::lrintf(clamped * 32767.0f));
    }

    const int mp3BufSize = static_cast<int>(1.25 * static_cast<double>(frameCount) + 7200);
    std::vector<unsigned char> mp3Buf(static_cast<size_t>(mp3BufSize));

    const int encoded = fnEncodeInterleaved_(
        lameFlags_, pcm.data(), static_cast<int>(frameCount), mp3Buf.data(), mp3BufSize
    );
    if (encoded < 0) {
        lastError_ = "LAME_ENCODE_FAILED";
        return false;
    }
    if (encoded > 0) {
        if (std::fwrite(mp3Buf.data(), 1, static_cast<size_t>(encoded), file_) != static_cast<size_t>(encoded)) {
            lastError_ = "DISK_WRITE_FAILED";
            return false;
        }
        totalBytes_ += static_cast<uint64_t>(encoded);
    }
    return true;
}

bool Mp3Encoder::finalize() {
    if (!file_ || !lameFlags_) return false;
    unsigned char flushBuf[7200];
    const int flushed = fnFlush_(lameFlags_, flushBuf, sizeof(flushBuf));
    if (flushed > 0) {
        std::fwrite(flushBuf, 1, static_cast<size_t>(flushed), file_);
        totalBytes_ += static_cast<uint64_t>(flushed);
    }
    std::fflush(file_);
    std::fclose(file_);
    file_ = nullptr;
    unloadLame();
    return true;
}

void Mp3Encoder::closeAndDelete(const std::string& path) {
    if (file_) {
        std::fclose(file_);
        file_ = nullptr;
    }
    unloadLame();
    std::remove(path.c_str());
}

} // namespace dd7
