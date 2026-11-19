# DD-7X Vocal Delay Pedal (Naat Khwan Edition)

Offline-first native Android vocal delay stompbox inspired by the classic 4-knob hardware digital delay workflow, purpose-tuned for Naat Sharif, Hamd, and Manqabat recitations.

## Architecture Overview
- **Android Client (`android-app/`)**: Kotlin + Jetpack Compose (Material 3), Hilt DI, Room SQLite, Android MediaCodec decoder, Foreground Export Service, and Keystore-backed Ed25519 offline license verification.
- **Native DSP Engine (`android-app/app/src/main/cpp/`)**: C++20 real-time audio engine (`libdd7dsp.so`) with Google Oboe playback, 4-point Hermite fractional delay, true stereo Ping-Pong cross-feedback, DM-2 warm analog saturation, chorus modulation LFO, 180 Hz HPF + 360 Hz anti-boom notch + 6 kHz sibilance damping, -0.3 dBFS soft-ceiling master limiter, 16-bit WAV writer, and LGPL-compliant dynamic `libmp3lame.so` MP3 encoder.
- **Commercial License Backend (`license-backend/`)**: Supabase PostgreSQL schema with strict Default-Deny RLS + Deno Edge Functions (`activate-license`, `generate-key`, `revoke-license`).
- **Offline Keygen CLI (`admin-tools/offline-keygen/`)**: Zero-dependency Node.js Ed25519 keypair generator and buyer token signer for WhatsApp sales.

## Quick Start

### 1. Generate Ed25519 Signing Keys
```bash
node admin-tools/offline-keygen/keygen.mjs init
```
Copy the generated `LICENSE_PUBLIC_KEY_ED25519` into `android-app/local.properties` and keep `LICENSE_PRIVATE_KEY_ED25519` strictly on your server/Supabase Secrets.

### 2. Configure Android Build
Copy `android-app/local.properties.example` to `android-app/local.properties` and fill in your SDK path, release keystore credentials, `LICENSE_PUBLIC_KEY_ED25519`, `LICENSE_API_BASE_URL`, `SUPABASE_ANON_KEY`, and `FOUNDER_WHATSAPP_NUMBER`.

### 3. Build Release APK
```bash
cd android-app
./gradlew assembleRelease
```

### 4. Run C++20 DSP Unit Tests (Desktop Linux/macOS)
```bash
g++ -std=c++20 -O2 -Iandroid-app/app/src/main/cpp \
  android-app/app/src/main/cpp/dsp/*.cpp \
  android-app/app/src/main/cpp/io/WavWriter.cpp \
  android-app/app/src/main/cpp/io/Mp3Encoder.cpp \
  android-app/app/src/main/cpp/io/OfflineRenderer.cpp \
  android-app/app/src/main/cpp/tests/dsp_unit_tests.cpp \
  -ldl -o /tmp/dd7_dsp_tests && /tmp/dd7_dsp_tests
```

### 5. Deploy Supabase License Backend
```bash
cd license-backend
supabase db push
supabase secrets set LICENSE_PRIVATE_KEY_ED25519=... ADMIN_API_SECRET=...
supabase functions deploy activate-license generate-key revoke-license
```
