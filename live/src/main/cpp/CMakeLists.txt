cmake_minimum_required(VERSION 3.22.1)

project("livelibs")

include_directories(include)


add_library(live SHARED
        native-lib.cpp
        AudioStream.cpp
        VideoStream.cpp
        RtmpPusher.cpp
        SrtPush.cpp
        )

add_library(yuv STATIC IMPORTED)
set_target_properties(yuv PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libyuv.a)

#add_library(pssl SHARED IMPORTED)
#set_target_properties(pssl PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libpolarssl.a)

add_subdirectory(${CMAKE_SOURCE_DIR}/rtmp)
#add_library(rtmp SHARED IMPORTED)
#set_target_properties(rtmp PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/librtmp.a)


add_library(faac STATIC IMPORTED)
set_target_properties(faac PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libfaac.a)

add_library(x264 STATIC IMPORTED)
set_target_properties(x264 PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libx264.a)

add_library(libsrt STATIC IMPORTED)
set_target_properties(libsrt PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libsrt.a)

add_library(libcrypto STATIC IMPORTED)
set_target_properties(libcrypto PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libcrypto.a)

#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

find_library(log-lib log)

target_link_libraries(live yuv faac x264 rtmp libsrt libcrypto
        #        -landroid
        #        -ljnigraphics
        ${log-lib})
