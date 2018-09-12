cmake_minimum_required(VERSION 2.8)
project(mcsimport)
set(CMAKE_MODULE_PATH ${CMAKE_SOURCE_DIR}/cmake)

# Provides things like lib64 dir (and Debian equiv)
include(GNUInstallDirs)

include(CheckCXXCompilerFlag)
CHECK_CXX_COMPILER_FLAG("-std=gnu++11" COMPILER_SUPPORTS_CXX11)
if(NOT COMPILER_SUPPORTS_CXX11)
    message(FATAL_ERROR "The compiler ${CMAKE_CXX_COMPILER} has no GNU++11 support. Please use a different C++ compiler.")
endif()

IF(UNIX)
  INCLUDE(Findmcsapi)
ENDIF(UNIX)
IF(WIN32)
  INCLUDE(Findmcsapi_windows)
ENDIF(WIN32)

if (NOT MCSAPI_FOUND)
    IF(UNIX)
        MESSAGE(FATAL_ERROR "Could not find mcsapi")
    ENDIF(UNIX)
    IF(WIN32)
        MESSAGE(FATAL_ERROR "Could not find mcsapi.\nYou can hint cmake by setting the environment variable MCSAPI_INSTALL_DIR")
    ENDIF(WIN32)
endif()
MESSAGE(STATUS "Using MCSAPI include dir: ${MCSAPI_INCLUDE_DIRS}")
include_directories(${MCSAPI_INCLUDE_DIRS})

IF(UNIX)
  # Disable no-deprecated-declarations
  set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -DDEBUG -Wall -Wunused -Wwrite-strings -Wno-strict-aliasing -Wextra -Wshadow -Werror -Wno-deprecated-declarations")

  # Disable format-truncation since this triggers in mcsapi_types for something that isn't a problem
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11 -Wformat -Wformat-security -fstack-protector-all -fstack-check -Wno-format-truncation -Wno-unused-variable -Wno-unused-parameter -fno-omit-frame-pointer -g")
ENDIF(UNIX)

SET(MCSIMPORT_FILES
	mcsimport.cpp
)

add_executable(mcsimport ${MCSIMPORT_FILES})

target_link_libraries(mcsimport ${MCSAPI_LIBRARIES})

INSTALL(TARGETS mcsimport RUNTIME DESTINATION "${CMAKE_INSTALL_BINDIR}" COMPONENT mcsimport)

# Install the mcsapi DLLs with the mcsimport binary
IF(WIN32)
    INSTALL(FILES ${MCSAPI_RUNTIME_LIBRARY} DESTINATION "${CMAKE_INSTALL_BINDIR}" COMPONENT mcsimport)
    INSTALL(FILES ${MCSAPI_LIBXML2_RUNTIME_LIBRARY} DESTINATION "${CMAKE_INSTALL_BINDIR}" COMPONENT mcsimport)
    INSTALL(FILES ${MCSAPI_LIBICONV_RUNTIME_LIBRARY} DESTINATION "${CMAKE_INSTALL_BINDIR}" COMPONENT mcsimport)
    INSTALL(FILES ${MCSAPI_LIBUV_RUNTIME_LIBRARY} DESTINATION "${CMAKE_INSTALL_BINDIR}" COMPONENT mcsimport)
ENDIF(WIN32)
