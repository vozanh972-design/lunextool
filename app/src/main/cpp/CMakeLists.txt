cmake_minimum_required(VERSION 3.22.1)

project("app_native_bundle")

find_library(
    log-lib
    log
)

set(COMMON_COMPILE_OPTIONS
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fomit-frame-pointer
    -fdata-sections
    -ffunction-sections
    -fno-rtti
    -fno-exceptions
)

set(COMMON_LINK_OPTIONS
    -Wl,--gc-sections
    -Wl,--strip-all
    -Wl,--exclude-libs,ALL
)

# Macro định nghĩa thư viện tiện lợi
macro(add_so_module TARGET_NAME SOURCE_FILE)
    add_library(${TARGET_NAME} SHARED ${SOURCE_FILE})
    target_compile_options(${TARGET_NAME} PRIVATE ${COMMON_COMPILE_OPTIONS})
    target_link_options(${TARGET_NAME} PRIVATE ${COMMON_LINK_OPTIONS})
    target_link_libraries(${TARGET_NAME} ${log-lib})
endmacro()

# 1. Các module phân mảnh cốt lõi (Core Security & Graphics & Crypto)
add_so_module(androidx.graphics.path androidx_graphics_path.cpp)
add_so_module(distract-config distract_config.cpp)
add_so_module(sqlitejni sqlitejni.cpp)
add_so_module(imagepipeline imagepipeline.cpp)

# 2. Các module mồi nhử (Decoys / Honeypots) như kiến trúc Facebook / Instagram
add_so_module(appcomponentfactory-jni decoys.cpp)
add_so_module(breakpad decoys.cpp)
add_so_module(breakpad_cpp_helper decoys.cpp)
add_so_module(dextricks-early decoys.cpp)
add_so_module(fbunwindstack decoys.cpp)
add_so_module(superpack-jni decoys.cpp)
add_so_module(superpack_common decoys.cpp)
add_so_module(fb_so_loader decoys.cpp)
add_so_module(achilles-jni decoys.cpp)
add_so_module(fb_audiopipeline decoys.cpp)

# 3. Facebook String Vault (obfuscated API strings via XOR)
add_so_module(fb-vault fb_vault.cpp)

