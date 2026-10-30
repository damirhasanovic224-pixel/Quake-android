cmake_minimum_required(VERSION 3.22)
project(quake_android C CXX)

get_filename_component(TP "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party" ABSOLUTE)
set(SDL_DIR   "${TP}/SDL")
set(GL4ES_DIR "${TP}/gl4es")
set(QS_DIR    "${TP}/quakespasm/Quake")

# ---------------- SDL2 ----------------
set(SDL_SHARED ON  CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST   OFF CACHE BOOL "" FORCE)
add_subdirectory(${SDL_DIR} SDL2)

# ---------------- gl4es (desktop GL -> GLES2) ----------------
set(NOX11      ON  CACHE BOOL "" FORCE)
set(GLX_STUBS  ON  CACHE BOOL "" FORCE)
set(DEFAULT_ES 2   CACHE STRING "" FORCE)
add_compile_definitions(DEFAULT_ES=2)
add_subdirectory(${GL4ES_DIR} gl4es)

# Android only loads files named exactly lib*.so, but gl4es names itself
# libGL.so.1. Record its settings (for debugging), then force plain libGL.so.
if(TARGET GL)
    set(_dump "")
    foreach(_p TYPE OUTPUT_NAME PREFIX SUFFIX VERSION SOVERSION NO_SONAME
               LINK_FLAGS LINK_OPTIONS LIBRARY_OUTPUT_DIRECTORY)
        get_target_property(_v GL ${_p})
        string(APPEND _dump "${_p} = ${_v}\n")
    endforeach()
    file(WRITE "${CMAKE_BINARY_DIR}/gl_props_before.txt" "${_dump}")

    set_property(TARGET GL PROPERTY VERSION)
    set_property(TARGET GL PROPERTY SOVERSION)
    set_target_properties(GL PROPERTIES
        OUTPUT_NAME "GL"
        PREFIX "lib"
        SUFFIX ".so"
        NO_SONAME OFF)
    target_link_options(GL PRIVATE "-Wl,-soname,libGL.so")
else()
    file(WRITE "${CMAKE_BINARY_DIR}/gl_props_before.txt" "No CMake target named GL exists\n")
endif()

# ---------------- Ogg Vorbis (music tracks) ----------------
set(OGG_DIR    "${TP}/ogg")
set(VORBIS_DIR "${TP}/vorbis")
add_library(oggvorbis STATIC
    ${OGG_DIR}/src/bitwise.c
    ${OGG_DIR}/src/framing.c
    ${VORBIS_DIR}/lib/mdct.c
    ${VORBIS_DIR}/lib/smallft.c
    ${VORBIS_DIR}/lib/block.c
    ${VORBIS_DIR}/lib/envelope.c
    ${VORBIS_DIR}/lib/window.c
    ${VORBIS_DIR}/lib/lsp.c
    ${VORBIS_DIR}/lib/lpc.c
    ${VORBIS_DIR}/lib/analysis.c
    ${VORBIS_DIR}/lib/synthesis.c
    ${VORBIS_DIR}/lib/psy.c
    ${VORBIS_DIR}/lib/info.c
    ${VORBIS_DIR}/lib/floor1.c
    ${VORBIS_DIR}/lib/floor0.c
    ${VORBIS_DIR}/lib/res0.c
    ${VORBIS_DIR}/lib/mapping0.c
    ${VORBIS_DIR}/lib/registry.c
    ${VORBIS_DIR}/lib/codebook.c
    ${VORBIS_DIR}/lib/sharedbook.c
    ${VORBIS_DIR}/lib/lookup.c
    ${VORBIS_DIR}/lib/bitrate.c
    ${VORBIS_DIR}/lib/vorbisfile.c)
set_target_properties(oggvorbis PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(oggvorbis
    PUBLIC  ${CMAKE_CURRENT_SOURCE_DIR}/compat ${OGG_DIR}/include ${VORBIS_DIR}/include
    PRIVATE ${VORBIS_DIR}/lib)
target_compile_options(oggvorbis PRIVATE -w -Wno-implicit-function-declaration)

# ---------------- QuakeSpasm ----------------
file(GLOB QS_SOURCES CONFIGURE_DEPENDS "${QS_DIR}/*.c")

# drop the Windows / non-SDL variants (lodepng.c is already included by image.c)
list(FILTER QS_SOURCES EXCLUDE REGEX
     "/(sys_sdl_win|pl_win|net_win|net_wins|net_wipx|net_none|gl_vidnt|snd_null|cd_sdl|in_win|lodepng)\\.c$")

add_library(main SHARED ${QS_SOURCES} android_log.c)

target_compile_definitions(main PRIVATE
    USE_SDL2
    USE_CODEC_WAVE
    USE_CODEC_VORBIS
    USE_QS_CONLINES)

target_include_directories(main PRIVATE ${QS_DIR} ${SDL_DIR}/include)

target_compile_options(main PRIVATE
    -std=gnu11 -fcommon
    -Wno-implicit-function-declaration
    -Wno-int-conversion
    -Wno-incompatible-pointer-types)

if(TARGET SDL2::SDL2main)
    target_link_libraries(main PRIVATE SDL2::SDL2main)
endif()
target_link_libraries(main PRIVATE SDL2::SDL2 GL oggvorbis log m)
