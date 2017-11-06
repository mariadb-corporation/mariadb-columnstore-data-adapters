cmake_minimum_required(VERSION 2.8)
project(mcskafka)
set(CMAKE_MODULE_PATH ${CMAKE_SOURCE_DIR}/cmake)

# Provides things like lib64 dir (and Debian equiv)
include(GNUInstallDirs)

IF(NOT CMAKE_BUILD_TYPE)
    SET(CMAKE_BUILD_TYPE RELWITHDEBINFO CACHE STRING
        "Choose the type of build, options are: None(CMAKE_CXX_FLAGS or CMAKE_C_FLAGS used) Debug Release RelWithDebInfo MinSizeRel" FORCE)
ENDIF(NOT CMAKE_BUILD_TYPE)

include(CheckCXXCompilerFlag)
CHECK_CXX_COMPILER_FLAG("-std=gnu++11" COMPILER_SUPPORTS_CXX11)
if(NOT COMPILER_SUPPORTS_CXX11)
    message(FATAL_ERROR "The compiler ${CMAKE_CXX_COMPILER} has no GNU++11 support. Please use a different C++ compiler.")
endif()

INCLUDE(Findmcsapi)
if (NOT MCSAPI_FOUND)
    MESSAGE(FATAL_ERROR "Could not find mcsapi")
endif()

INCLUDE(Findjansson)
if (NOT JANSSON_FOUND)
    MESSAGE(FATAL_ERROR "Could not find jansson. Please install jansson-devel on RPM distributions or libjansson-dev on DEB distributions")
endif()

INCLUDE(Findkafkapp)
if (NOT RDKAFKAPP_FOUND)
    MESSAGE(FATAL_ERROR "Could not find librdkafka++. Please install librdkafka-devel on RPM distributions or librdkafka-dev on DEB distributions")
endif()

# Disable no-deprecated-declarations
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -DDEBUG -Wall -Wunused -Wwrite-strings -Wno-strict-aliasing -Wextra -Wshadow -Werror -Wno-deprecated-declarations")

# FORTIFY_SOURCE requires > -O0
string(TOLOWER ${CMAKE_BUILD_TYPE} LOWERCASE_CMAKE_BUILD_TYPE)
if (NOT LOWERCASE_CMAKE_BUILD_TYPE STREQUAL "debug")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D_FORTIFY_SOURCE=2")
endif()

# Disable format-truncation since this triggers in mcsapi_types for something that isn't a problem
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11 -Wformat -Wformat-security -fstack-protector-all -fstack-check -Wno-format-truncation")

add_subdirectory(src)

install(FILES "README.md"
    "LICENSE"
    DESTINATION "${CMAKE_INSTALL_DOCDIR}"
    )

option(RPM "Build an RPM" OFF)

if (RPM)
    SET(CPACK_GENERATOR "RPM")
    SET(CPACK_RPM_PACKAGE_VERSION ${VERSION_SHORT})
    SET(CPACK_RPM_PACKAGE_RELEASE "1")
    SET(CPACK_RPM_PACKAGE_NAME "mariadb-columnstore-kafka")
    SET(ENGINE_ARCH "x86_64")
    SET(CPACK_PACKAGE_FILE_NAME "${CPACK_RPM_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}")

    include(CPack)
endif()

option(DEB "Build a DEB" OFF)

if (DEB)
    SET(CPACK_GENERATOR "DEB")
    SET(CPACK_DEBIAN_PACKAGE_VERSION ${VERSION_SHORT})
    SET(CPACK_DEBIAN_PACKAGE_RELEASE "1")
    SET(CPACK_DEBIAN_PACKAGE_NAME "mariadb-columnstore-kafka")
    SET(CPACK_PACKAGE_CONTACT "MariaDB Corporation")
    SET(ENGINE_ARCH "x86_64")
    SET(CPACK_PACKAGE_FILE_NAME "${CPACK_DEBIAN_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}")
    include(CPack)
endif()

# uninstall target
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/cmake_uninstall.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/cmake_uninstall.cmake"
    IMMEDIATE @ONLY)

add_custom_target(uninstall
    COMMAND ${CMAKE_COMMAND} -P ${CMAKE_CURRENT_BINARY_DIR}/cmake_uninstall.cmake)
