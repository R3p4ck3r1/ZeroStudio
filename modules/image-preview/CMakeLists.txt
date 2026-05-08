cmake_minimum_required(VERSION 3.22.1)

project("image-preview")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 编译器优化标志
# -O3: 最高级别优化
# -fvisibility=hidden: 隐藏内部符号，减小动态库体积，只暴露 JNI 接口
# -ffunction-sections -fdata-sections: 允许链接器剔除未使用的代码
# -fexceptions -frtti: 启用异常和运行时类型识别 (部分第三方库可能需要)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fvisibility=hidden -ffunction-sections -fdata-sections -fexceptions -frtti")

# 链接器标志：剔除死代码，减小体积
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

add_definitions(
    -DTVG_BUILD             # 标识正在构建 ThorVG
    -DTVG_EXPORT=""         # 静态链接时不需要导出符号
    
    # 启用加载器 (Loaders)
    -DTVG_ENABLE_LOTTIE     # 启用 Lottie 动画支持
    -DTVG_ENABLE_SVG        # 启用 SVG 矢量图支持
    -DTVG_ENABLE_PNG        # 启用 PNG 支持 (依赖内部 lodepng)
    -DTVG_ENABLE_JPG        # 启用 JPG 支持 (依赖内部 jpgd)
    -DTVG_ENABLE_WEBP       # 启用 WebP 支持
    -DTVG_ENABLE_TTF        # 启用 TrueType 字体支持
    -DTVG_ENABLE_RAW        # 启用 Raw 数据支持
    
    # 启用保存器 (Savers)
    -DTVG_ENABLE_GIF        # 启用 GIF 导出/保存支持
    
    # JerryScript 配置 (Lottie 表达式需要)
    -DJERRY_CPOINTER_32_BIT=0 # 64位系统兼容
    -DJERRY_ERROR_MESSAGES=1
    -DJERRY_PARSER=1
    -DJERRY_LINE_INFO=1
    -DJERRY_ES2015=1
)

set(CPP_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp)
set(THORVG_ROOT ${CPP_ROOT}/thorvg)

include_directories(
    # ThorVG 公共头文件
    ${THORVG_ROOT}/inc
    
    # 核心组件
    ${THORVG_ROOT}/src/common
    ${THORVG_ROOT}/src/renderer
    ${THORVG_ROOT}/src/renderer/sw_engine
    
    # 加载器头文件路径
    ${THORVG_ROOT}/src/loaders/svg
    ${THORVG_ROOT}/src/loaders/lottie
    ${THORVG_ROOT}/src/loaders/png
    ${THORVG_ROOT}/src/loaders/jpg
    ${THORVG_ROOT}/src/loaders/webp
    ${THORVG_ROOT}/src/loaders/raw
    ${THORVG_ROOT}/src/loaders/ttf
    
    # 第三方依赖路径 (Lottie 依赖)
    ${THORVG_ROOT}/src/loaders/lottie/rapidjson
    ${THORVG_ROOT}/src/loaders/lottie/jerryscript/jerry-core/include
)

file(GLOB_RECURSE THORVG_SOURCES "${THORVG_ROOT}/src/*.cpp")

list(FILTER THORVG_SOURCES EXCLUDE REGEX "/gl_engine/")
list(FILTER THORVG_SOURCES EXCLUDE REGEX "/wg_engine/")

list(FILTER THORVG_SOURCES EXCLUDE REGEX "/test/")

list(FILTER THORVG_SOURCES EXCLUDE REGEX "/examples/")

list(FILTER THORVG_SOURCES EXCLUDE REGEX "/bindings/") 

message(STATUS "ThorVG Sources Count: " ${THORVG_SOURCES})

add_library(image-preview SHARED
    ${CPP_ROOT}/thorvg-jni.cpp
    
    # ThorVG 源码
    ${THORVG_SOURCES}
)

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(image-preview
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)