cmake_minimum_required(VERSION 2.8)
project(mxs_adapter)
set(VERSION "1.0.0")

include(GNUInstallDirs)
include(CheckCXXCompilerFlag)
include(ExternalProject)
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${CMAKE_SOURCE_DIR}/cmake")

# First we need to check if C++11 is supported
CHECK_CXX_COMPILER_FLAG("-std=c++11" COMPILER_SUPPORTS_CXX11)
if(NOT COMPILER_SUPPORTS_CXX11)
  message(FATAL_ERROR "The compiler ${CMAKE_CXX_COMPILER} has no C++11 support. Please use a different C++ compiler.")
endif()

find_package(LibXml2)
find_package(LibUV)
find_package(Snappy)

# Some defaults
set(CMAKE_BUILD_TYPE "RelWithDebInfo" CACHE STRING
        "Choose the type of build, options are: None(CMAKE_CXX_FLAGS or CMAKE_C_FLAGS used) Debug Release RelWithDebInfo MinSizeRel")

# Build Jansson
include(cmake/BuildJansson.cmake)
include_directories(${JANSSON_INCLUDE_DIR})

# Build ColumnStore API bindings
include(cmake/BuildMCSAPI.cmake)
include_directories(${MCSAPI_INCLUDE_DIR})

# Build MaxScale CDC connector
include(cmake/BuildCDC.cmake)
include_directories(${CDC_INCLUDE_DIR})
add_dependencies(cdc-connector jansson)

# Compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Wshadow -Werror")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -ggdb")

# Then finally build the adapter itself
add_executable(mxs_adapter mxs_adapter.cpp)
add_dependencies(mxs_adapter jansson cdc-connector mcsapi)
target_link_libraries(mxs_adapter ${CDC_LIBRARIES}
  ${MCSAPI_LIBRARIES} ${LIBUV_LIBRARY} ${LIBXML2_LIBRARIES}
  ${JANSSON_STATIC_LIBRARIES} crypto ${SNAPPY_LIBRARIES})
install(TARGETS mxs_adapter DESTINATION ${CMAKE_INSTALL_BINDIR})
