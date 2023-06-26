cmake_minimum_required(VERSION 3.22.1)

project("livelibs")

include_directories(include/)


add_library(live SHARED native-lib.cpp)

add_library(yuv STATIC IMPORTED)
set_target_properties(yuv PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libyuv.a)
#
#add_library(rmtp STATIC IMPORTED)
#set_target_properties(rmtp PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/librmtp.a)
#
#add_library(faac STATIC IMPORTED)
#set_target_properties(faac PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libfdk-aac.a)
#
#add_library(x264 STATIC IMPORTED)
#set_target_properties(x264 PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libx264.a)


#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

find_library(log-lib log)

target_link_libraries(live yuv
#        -landroid
#        -ljnigraphics
        ${log-lib})
