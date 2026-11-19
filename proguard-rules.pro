# Keep JNI native bridge signatures intact
-keep class com.naatdelay.dd7.audio.NativeAudioBridge { *; }
-keep class com.naatdelay.dd7.audio.ExportResult { *; }
-keep class com.naatdelay.dd7.audio.ExportProgressCallback { *; }

# Keep Room entities & generated classes
-keep class com.naatdelay.dd7.data.local.entity.** { *; }

# Keep BouncyCastle Ed25519 classes used by offline verifier
-keep class org.bouncycastle.crypto.params.Ed25519PublicKeyParameters { *; }
-keep class org.bouncycastle.crypto.signers.Ed25519Signer { *; }

# Strip debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
