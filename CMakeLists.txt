cmake_minimum_required(VERSION 3.22.1)
project("dd7dsp" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)
find_library(log-lib log)

add_library(dd7dsp SHARED
    dsp/DelayLine.cpp
    dsp/AnalogFilter.cpp
    dsp/ModLfo.cpp
    dsp/PingPongMatrix.cpp
    dsp/SoftLimiter.cpp
    dsp/DelayEngine.cpp
    io/OboePlayer.cpp
    io/WavWriter.cpp
    io/Mp3Encoder.cpp
    io/OfflineRenderer.cpp
    jni/NativeBridge.cpp
)

target_include_directories(dd7dsp PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# LGPL Compliance: libmp3lame.so is loaded dynamically via dlopen() in Mp3Encoder.cpp
target_link_libraries(dd7dsp
    oboe::oboe
    ${log-lib}
    dl
)
