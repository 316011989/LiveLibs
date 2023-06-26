# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22.1)
project("livelibs")

include_directories(../../../src/main/cpp/include/${CMAKE_ANDROID_ARCH_ABI}/)


add_library(avcodec
        STATIC
        IMPORTED )
set_target_properties( avcodec
        PROPERTIES IMPORTED_LOCATION
        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavcodec.a )

add_library(avdevice
        STATIC
        IMPORTED )
set_target_properties(avdevice
        PROPERTIES IMPORTED_LOCATION
        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavdevice.a )

add_library(avfilter
        STATIC
        IMPORTED )
set_target_properties(avfilter
        PROPERTIES IMPORTED_LOCATION
        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavfilter.a )

add_library( # Sets the name of the library.
        test-lib

        # Sets the library as a shared library.
        STATIC

        # Provides a relative path to your source file(s).
        native-lib.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        test-lib
        avcodec
        avdevice
        avfilter
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})