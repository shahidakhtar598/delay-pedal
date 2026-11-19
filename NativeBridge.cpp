#include <jni.h>
#include <atomic>
#include <memory>
#include <string>
#include "io/OboePlayer.h"
#include "io/OfflineRenderer.h"

namespace {
std::unique_ptr<dd7::OboePlayer> gPlayer;
std::atomic<bool> gExportCancel{false};

std::string jstringToStd(JNIEnv* env, jstring jstr) {
    if (!jstr) return {};
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(jstr, chars);
    return out;
}
} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_nativeInit(JNIEnv*, jobject) {
    if (!gPlayer) {
        gPlayer = std::make_unique<dd7::OboePlayer>();
    }
}

JNIEXPORT void JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_nativeRelease(JNIEnv*, jobject) {
    if (gPlayer) {
        gPlayer->stopAndClose();
        gPlayer.reset();
    }
}

JNIEXPORT jboolean JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_loadDecodedPcm(
    JNIEnv* env, jobject, jstring pcmPath, jint sampleRate
) {
    if (!gPlayer) gPlayer = std::make_unique<dd7::OboePlayer>();
    return gPlayer->loadPcmFile(jstringToStd(env, pcmPath), sampleRate) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_setKnobParam(
    JNIEnv*, jobject, jint paramId, jfloat value
) {
    if (!gPlayer) return;
    gPlayer->setKnobParam(static_cast<dd7::ParamId>(paramId), value);
}

JNIEXPORT void JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_setPlaybackState(
    JNIEnv*, jobject, jboolean playing, jlong seekPositionMs
) {
    if (!gPlayer) return;
    gPlayer->setPlaybackState(playing == JNI_TRUE, static_cast<int64_t>(seekPositionMs));
}

JNIEXPORT void JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_setLoopRange(
    JNIEnv*, jobject, jboolean enabled, jlong startMs, jlong endMs
) {
    if (!gPlayer) return;
    gPlayer->setLoopRange(enabled == JNI_TRUE, static_cast<int64_t>(startMs), static_cast<int64_t>(endMs));
}

JNIEXPORT jlong JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_getPlaybackPositionMs(JNIEnv*, jobject) {
    return gPlayer ? static_cast<jlong>(gPlayer->getPlaybackPositionMs()) : 0L;
}

JNIEXPORT jfloat JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_getOutputMeterLevel(JNIEnv*, jobject) {
    return gPlayer ? gPlayer->getOutputMeterLevel() : 0.0f;
}

JNIEXPORT jboolean JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_isLimiterActive(JNIEnv*, jobject) {
    return (gPlayer && gPlayer->isLimiting()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_cancelOfflineExport(JNIEnv*, jobject) {
    gExportCancel.store(true, std::memory_order_release);
}

JNIEXPORT jobject JNICALL
Java_com_naatdelay_dd7_audio_NativeAudioBridge_renderOfflineExport(
    JNIEnv* env,
    jobject,
    jstring inputPcmPath,
    jstring outputFilePath,
    jint sampleRate,
    jint formatCode,
    jfloat effectLevel,
    jfloat feedback,
    jfloat delayTimeMs,
    jint modeOrdinal,
    jboolean pingPong,
    jfloat pingPongWidth,
    jboolean antiBoomCut,
    jboolean bypassed,
    jint maxDurationSec,
    jobject progressCallback
) {
    gExportCancel.store(false, std::memory_order_release);

    dd7::OfflineExportConfig cfg;
    cfg.inputPcmPath = jstringToStd(env, inputPcmPath);
    cfg.outputFilePath = jstringToStd(env, outputFilePath);
    cfg.sampleRate = sampleRate;
    cfg.format = static_cast<dd7::ExportFormat>(formatCode);
    cfg.maxDurationSec = maxDurationSec;
    cfg.dspParams.effectLevel = effectLevel;
    cfg.dspParams.feedback = feedback;
    cfg.dspParams.delayTimeMs = delayTimeMs;
    cfg.dspParams.mode = static_cast<dd7::DelayMode>(modeOrdinal);
    cfg.dspParams.pingPong = (pingPong == JNI_TRUE);
    cfg.dspParams.pingPongWidth = pingPongWidth;
    cfg.dspParams.antiBoomCut = (antiBoomCut == JNI_TRUE);
    cfg.dspParams.bypassed = (bypassed == JNI_TRUE);

    jmethodID onProgMethod = nullptr;
    if (progressCallback) {
        jclass cbCls = env->GetObjectClass(progressCallback);
        onProgMethod = env->GetMethodID(cbCls, "onProgress", "(I)V");
    }

    const auto result = dd7::OfflineRenderer::render(
        cfg,
        gExportCancel,
        [&](int32_t pct) {
            if (progressCallback && onProgMethod) {
                env->CallVoidMethod(progressCallback, onProgMethod, static_cast<jint>(pct));
            }
        }
    );

    jclass resCls = env->FindClass("com/naatdelay/dd7/audio/ExportResult");
    jmethodID ctor = env->GetMethodID(resCls, "<init>", "(ZJILjava/lang/String;)V");
    jstring errStr = env->NewStringUTF(result.errorCode.c_str());

    return env->NewObject(
        resCls,
        ctor,
        result.success ? JNI_TRUE : JNI_FALSE,
        static_cast<jlong>(result.fileBytes),
        static_cast<jint>(result.tailMs),
        errStr
    );
}

} // extern "C"
