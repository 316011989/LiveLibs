# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_VERBOSE_MAKEFILE on)
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
set(BUILD_LIB_NAME fanplayer_jni)
set(libs "${CMAKE_SOURCE_DIR}/libs")

include_directories(
        avkcpdemuxer
        ffrdpdemuxer
        lib
        src
        include
        utils
)

link_directories(
        ${libs}
)

find_library(log-lib
        log)

find_library(GLESv2-lib
        GLESv2)

set(LINK_LIB_LIST
        avformat
        avcodec
        avdevice
        avfilter
        swresample
        swscale
        avutil
        ssl
        crypto
        soundtouch
        stlport_static
        )

set(LINK_LOCAL_LIB_LIST
        ${log-lib}
        z
        android
        GLESv2
        )

add_library( # Sets the name of the library.
        ${BUILD_LIB_NAME}

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/ffplayer.c
        src/ffrender.c
        src/pktqueue.c
        src/snapshot.c
        src/recorder.c
        src/datarate.c
        src/vdev-cmn.c
        src/adev-android.cpp
        src/vdev-android.cpp
        src/fanplayer_jni.cpp
        utils/GLUtils.cpp
        avkcpdemuxer/ikcp.c
        avkcpdemuxer/ringbuf.c
        avkcpdemuxer/avkcpc.c
        avkcpdemuxer/avkcpd.c
        ffrdpdemuxer/ffrdp.c
        ffrdpdemuxer/ffrdpc.c
        ffrdpdemuxer/ffrdpd.c
        )




target_link_libraries(
        ${BUILD_LIB_NAME}
        # Links the target library to the log library
        # included in the NDK.
        ${LINK_LIB_LIST}
        ${LINK_LOCAL_LIB_LIST}
        )

