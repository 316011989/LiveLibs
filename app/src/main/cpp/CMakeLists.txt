# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22.1)

# Declares and names the project.

project("livelibs")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
include_directories(include/)
#
#
#add_library(avcodec
#        STATIC
#        IMPORTED )
#set_target_properties( avcodec
#        PROPERTIES IMPORTED_LOCATION
#        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavcodec.a )
#
#add_library(avdevice
#        STATIC
#        IMPORTED )
#set_target_properties(avdevice
#        PROPERTIES IMPORTED_LOCATION
#        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavdevice.a )
#
#add_library(avfilter
#        STATIC
#        IMPORTED )
#set_target_properties(avfilter
#        PROPERTIES IMPORTED_LOCATION
#        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavfilter.a )
#
#
#add_library(avformat
#        STATIC
#        IMPORTED )
#set_target_properties(avformat
#        PROPERTIES IMPORTED_LOCATION
#        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavformat.a )
#
#add_library(avresample
#        STATIC
#        IMPORTED )
#set_target_properties(avresample
#        PROPERTIES IMPORTED_LOCATION
#        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavresample.a )
#
#add_library(avutil
#        STATIC
#        IMPORTED )
#set_target_properties(avutil
#        PROPERTIES IMPORTED_LOCATION
#        ../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libavutil.a )
#


add_library(live STATIC native-lib.cpp)

add_library(yuv STATIC IMPORTED)
set_target_properties(yuv PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libyuv.a)

find_library(log-lib log)

target_link_libraries(live yuv ${log-lib})


#cmake_minimum_required(VERSION 3.22.1)
#set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../../libs)
#
#add_library(yuv
#        STATIC
#        IMPORTED)
#set_target_properties(yuv
#        PROPERTIES IMPORTED_LOCATION
#        ../../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libyuv.a)
#
#add_library(faac
#        STATIC
#        IMPORTED)
#set_target_properties(faac
#        PROPERTIES IMPORTED_LOCATION
#        ../../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libfdk-aac.a)
#
#
#add_library(x264
#        STATIC
#        IMPORTED)
#set_target_properties(x264
#        PROPERTIES IMPORTED_LOCATION
#        ../../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libx264.a)
#
#
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")
#
#include_directories(src/main/cpp/include)
#
#add_library(live
#        SHARED
#        src/main/cpp/native-lib.cpp)
#
#find_library( log-lib
#        log )
#
#target_link_libraries(live x264 faac
#        -landroid
#        -ljnigraphics
#        ${log-lib} )