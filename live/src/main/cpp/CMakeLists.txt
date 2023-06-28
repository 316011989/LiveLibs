cmake_minimum_required(VERSION 3.22.1)

project("livelibs")

include_directories(include/${CMAKE_ANDROID_ARCH_ABI}/)
include_directories(include/${CMAKE_ANDROID_ARCH_ABI}/fdk-aac/)
include_directories(include/faac/fdk-aac/)
#include_directories(include/${CMAKE_ANDROID_ARCH_ABI}/polarssl/)
#include_directories(include/${CMAKE_ANDROID_ARCH_ABI}/librtmp/)


add_library(live SHARED
        native-lib.cpp
        AudioStream.cpp
        VideoStream.cpp
        RtmpPusher.cpp)

add_library(yuv STATIC IMPORTED)
set_target_properties(yuv PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libyuv.a)

#add_library(pssl SHARED IMPORTED)
#set_target_properties(pssl PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libpolarssl.a)

add_subdirectory(${CMAKE_SOURCE_DIR}/rtmp)
#add_library(rtmp SHARED IMPORTED)
#set_target_properties(rtmp PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/librtmp.a)

#add_library(faac STATIC IMPORTED)
#set_target_properties(faac PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libfdk-aac.a)

add_library(faac STATIC IMPORTED)
set_target_properties(faac PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libfaac.a)

add_library(x264 STATIC IMPORTED)
set_target_properties(x264 PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libx264.a)


#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

find_library(log-lib log)

target_link_libraries(live yuv faac x264 rtmp
#        -landroid
#        -ljnigraphics
        ${log-lib})
