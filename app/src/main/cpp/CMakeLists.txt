cmake_minimum_required(VERSION 3.22.1)

project("security_guard")

add_library(
    security_guard
    SHARED
    security_guard.cpp
)

find_library(
    log-lib
    log
)

target_compile_options(
    security_guard
    PRIVATE
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fomit-frame-pointer
    -fdata-sections
    -ffunction-sections
)

target_link_options(
    security_guard
    PRIVATE
    -Wl,--gc-sections
    -Wl,--strip-all
)

target_link_libraries(
    security_guard
    ${log-lib}
)
