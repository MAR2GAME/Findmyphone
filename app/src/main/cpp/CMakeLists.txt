cmake_minimum_required(VERSION 3.4.1)
project("FindMyPhone")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加 OpenSSL 静态库
if(EXISTS ${CMAKE_SOURCE_DIR}/openssl/lib/${ANDROID_ABI}/libcrypto.a)
    add_library(openssl STATIC IMPORTED)
    set_target_properties(openssl PROPERTIES IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/openssl/lib/${ANDROID_ABI}/libcrypto.a)
else()
    message(FATAL_ERROR "OpenSSL library not found for ABI ${ANDROID_ABI}")
endif()

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)

target_include_directories(native-lib PRIVATE ${CMAKE_SOURCE_DIR}/openssl/include)

target_link_libraries(native-lib openssl ${log-lib})

# 优化与安全编译选项
target_compile_options(native-lib PRIVATE -ffunction-sections -fdata-sections)
target_link_options(native-lib PRIVATE -Wl,--gc-sections)
target_link_options(native-lib PRIVATE -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)

# 隐藏符号，仅导出 JNI_OnLoad / JNI_OnUnload
target_compile_options(native-lib PRIVATE -fvisibility=hidden)
target_link_options(native-lib PRIVATE -Wl,--exclude-libs,ALL)

if(EXISTS ${CMAKE_SOURCE_DIR}/export.map)
    target_link_options(native-lib PRIVATE -Wl,--version-script=${CMAKE_SOURCE_DIR}/export.map)
else()
    message(FATAL_ERROR "export.map not found; refusing to build native-lib without an explicit JNI export list")
endif()

# Release 专属优化
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_definitions(-DNDEBUG)
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2")
    set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -flto -Wl,--strip-all -Wl,-z,relro,-z,now")
endif()
