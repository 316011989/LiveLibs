cmake_minimum_required(VERSION 3.22.1)

project("livelibs")

include_directories(
        include
        player
        avkcpdemuxer
        ffrdpdemuxer
        )


add_library(live STATIC
        native-lib.cpp
        AudioStream.cpp
        VideoStream.cpp
        RtmpPusher.cpp)

add_library(yuv STATIC IMPORTED)
set_target_properties(yuv PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libyuv.a)


add_subdirectory(${CMAKE_SOURCE_DIR}/rtmp)
#add_library(rtmp SHARED IMPORTED)
#set_target_properties(rtmp PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/librtmp.a)


add_library(faac STATIC IMPORTED)
set_target_properties(faac PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libfaac.a)

add_library(x264 STATIC IMPORTED)
set_target_properties(x264 PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libx264.a)

add_library(fanplayer STATIC
        player/ffplayer.c
        player/ffrender.c
        player/pktqueue.c
        player/snapshot.c
        player/recorder.c
        player/datarate.c
        player/vdev-cmn.c
        player/adev-android.cpp
        player/vdev-android.cpp
        player/fanplayer_jni.cpp
        utils/GLUtils.cpp
        avkcpdemuxer/ikcp.c
        avkcpdemuxer/ringbuf.c
        avkcpdemuxer/avkcpc.c
        avkcpdemuxer/avkcpd.c
        ffrdpdemuxer/ffrdp.c
        ffrdpdemuxer/ffrdpc.c
        ffrdpdemuxer/ffrdpd.c
        )
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

find_library(log-lib log)

target_link_libraries(live fanplayer yuv faac x264 rtmp
#        -landroid
#        -ljnigraphics
        ${log-lib})
