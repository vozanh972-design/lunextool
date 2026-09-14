cmake_minimum_required(VERSION 3.22.1)

project("image_pipeline")

add_library(
    image_pipeline
    SHARED
    native_pipeline.cpp
)

find_library(
    log-lib
    log
)

target_compile_options(
    image_pipeline
    PRIVATE
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fomit-frame-pointer
    -fdata-sections
    -ffunction-sections
    -fno-rtti
    -fno-exceptions
)

target_link_options(
    image_pipeline
    PRIVATE
    -Wl,--gc-sections
    -Wl,--strip-all
    -Wl,--exclude-libs,ALL
)

target_link_libraries(
    image_pipeline
    ${log-lib}
)
