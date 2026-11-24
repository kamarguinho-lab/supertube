cmake_minimum_required(VERSION 3.22.1)
project(supertube_mp3 C)

# Codificador MP3 LAME (LGPL, código em lame/) + ponte JNI
file(GLOB LAME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame/*.c)

add_library(supertube_mp3 SHARED mp3encoder.c ${LAME_SOURCES})

target_include_directories(supertube_mp3 PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/lame/include
        ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame)

target_compile_definitions(supertube_mp3 PRIVATE
        STDC_HEADERS HAVE_STDINT_H ieee754_float32_t=float)

target_compile_options(supertube_mp3 PRIVATE -O2 -w)

target_link_libraries(supertube_mp3 m log)

# Páginas de 16 KB (exigido pelos Android 15+ novos)
target_link_options(supertube_mp3 PRIVATE "-Wl,-z,max-page-size=16384")
