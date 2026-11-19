#pragma once

#include "dsp/DelayEngine.h"
#include "dsp/DspParams.h"
#include <atomic>
#include <cstdint>
#include <functional>
#include <string>

namespace dd7 {

enum class ExportFormat : int32_t {
    WAV_16  = 0,
    MP3_192 = 1,
    MP3_320 = 2
};

struct OfflineExportConfig {
    std::string inputPcmPath;       // Raw 32-bit float interleaved stereo PCM
    std::string outputFilePath;     // Target WAV or MP3 path
    int32_t sampleRate = 48000;
    ExportFormat format = ExportFormat::WAV_16;
    DspParamSnapshot dspParams;
    int32_t maxDurationSec = -1;    // 30 in Trial mode, -1 in Pro (unlimited)
};

struct OfflineExportResult {
    bool success = false;
    uint64_t fileBytes = 0;
    int32_t tailMs = 0;
    std::string errorCode;
};

class OfflineRenderer {
public:
    // Streams input PCM in 2048-frame chunks (low RAM footprint for 45-60 min Mehfil recordings)
    // and appends automatic delay tail until wet RMS drops below -60 dBFS (capped at 12s).
    static OfflineExportResult render(
        const OfflineExportConfig& config,
        const std::atomic<bool>& cancelFlag,
        const std::function<void(int32_t)>& onProgressPercent
    );

    // Helper: estimate maximum tail duration based on delay time & feedback
    static int32_t computeMaxTailMs(const DspParamSnapshot& params) noexcept;
};

} // namespace dd7
