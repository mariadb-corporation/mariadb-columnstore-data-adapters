cmake_minimum_required(VERSION 2.8.2)

set(VERSION "1.2.0")
set(RELEASE "1")

option(RPM "Build an RPM" OFF)
option(TEST_RUNNER "Build the test suite" OFF)
option(KAFKA "Build the Kafka-Avro to ColumnStore Data Adatper" ON)
option(KETTLE "Build the Kettle / PDI ColumnStore Bulk Write Plugin" ON)
option(MAX_CDC "Build the MaxScale CDC to ColumnStore Data Adapter" ON)
option(MAX_KAFKA "Build the MaxScale Kafka+CDC to ColumnStore Data Adapter (deprecated)" OFF)
option(REMOTE_CPIMPORT "Build the remote cpimport bulk importer" ON)

IF(RPM)
    SET(CPACK_GENERATOR "RPM")
    SET(CPACK_RPM_PACKAGE_VERSION ${VERSION})
    SET(CPACK_RPM_PACKAGE_RELEASE ${RELEASE})
    SET(CPACK_RPM_PACKAGE_NAME "mariadb-columnstore-data-adapters")
    SET(ENGINE_ARCH "x86_64")
    SET(CPACK_PACKAGE_FILE_NAME "${CPACK_RPM_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}")
    SET(CPACK_RPM_COMPONENT_INSTALL ON)

    IF(KAFKA)
        list(APPEND CPACK_COMPONENTS_ALL avro_kafka_adapter)
    ENDIF(KAFKA)
    
    IF(MAX_CDC)
        list(APPEND CPACK_COMPONENTS_ALL mxs_adapter)
    ENDIF(MAX_CDC)
    
    IF(MAX_KAFKA)
        list(APPEND CPACK_COMPONENTS_ALL mcskafka)
    ENDIF(MAX_KAFKA)

    IF(REMOTE_CPIMPORT)
        list(APPEND CPACK_COMPONENTS_ALL mcsimport)
    ENDIF(REMOTE_CPIMPORT)

    SET(CPACK_RPM_AVRO_KAFKA_ADAPTER_PACKAGE_NAME "mariadb-columnstore-kafka-avro-adapters")
    SET(CPACK_RPM_AVRO_KAFKA_ADAPTER_FILE_NAME "${CPACK_RPM_AVRO_KAFKA_ADAPTER_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}.rpm")
    
    SET(CPACK_RPM_MCSKAFKA_PACKAGE_NAME "mariadb-columnstore-kafka-adapters")
    SET(CPACK_RPM_MCSKAFKA_FILE_NAME "${CPACK_RPM_MCSKAFKA_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}.rpm")

    SET(CPACK_RPM_MXS_ADAPTER_PACKAGE_NAME "mariadb-columnstore-maxscale-cdc-adapters")
    SET(CPACK_RPM_MXS_ADAPTER_FILE_NAME "${CPACK_RPM_MXS_ADAPTER_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}.rpm")

    SET(CPACK_RPM_MCSIMPORT_PACKAGE_NAME "mariadb-columnstore-remote-cpimport")
    SET(CPACK_RPM_MCSIMPORT_FILE_NAME "${CPACK_RPM_MCSIMPORT_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}.rpm")

    # rename packets manually if CMAKE < 3.6.3 (CentOS 7)
    if(${CMAKE_VERSION} VERSION_LESS "3.6.3")
        if(${CMAKE_VERSION} VERSION_GREATER "3.0.1")
            cmake_policy(SET CMP0037 OLD) 
        endif()

        SET( CPACK_OUTPUT_CONFIG_FILE "${CMAKE_BINARY_DIR}/RPMPackageConfig.cmake" )
        include(CPack)

        ADD_CUSTOM_TARGET ( package
            COMMAND "${CMAKE_CPACK_COMMAND}" 
                "--config" "${CMAKE_BINARY_DIR}/RPMPackageConfig.cmake"
            COMMENT "-- Running CPack..."
        )

        if(KAFKA)
            add_dependencies(package kafka_to_avro)
            add_custom_command(
                TARGET package
                POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E rename "*-avro_kafka_adapter.rpm" "${CPACK_RPM_AVRO_KAFKA_ADAPTER_FILE_NAME}"
                COMMENT "-- Renaming kafka_to_avro packet file name"
            )
        endif()
        if(MAX_CDC)
            add_dependencies(package mxs_adapter)
            add_custom_command(
                TARGET package
                POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E rename "*-mxs_adapter.rpm" "${CPACK_RPM_MXS_ADAPTER_FILE_NAME}"
                COMMENT "-- Renaming max_cdc packet file name"
            )
        endif()
        if(MAX_KAFKA)
            add_dependencies(package mcskafka)
            add_custom_command(
                TARGET package
                POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E rename "*-mcskafka.rpm" "${CPACK_RPM_MCSKAFKA_FILE_NAME}"
                COMMENT "-- Renaming max_kafka packet file name"
            )
        endif()
        if(REMOTE_CPIMPORT)
            add_dependencies(package mcsimport)
            add_custom_command(
                TARGET package
                POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E rename "*-mcsimport.rpm" "${CPACK_RPM_MCSIMPORT_FILE_NAME}"
                COMMENT "-- Renaming mcsimport packet file name"
            )
        endif()
    else()
        include(CPack)
    endif()
ENDIF(RPM)

option(DEB "Build a DEB" OFF)
IF(DEB)
    SET(CPACK_GENERATOR "DEB")
    SET(CPACK_DEBIAN_PACKAGE_VERSION ${VERSION})
    SET(CPACK_DEBIAN_PACKAGE_RELEASE ${RELEASE})
    SET(CPACK_DEBIAN_PACKAGE_NAME "mariadb-columnstore-data-adapters")
    SET(CPACK_PACKAGE_CONTACT "MariaDB Corporation")
    SET(ENGINE_ARCH "x86_64")
    SET(CPACK_DEBIAN_PACKAGE_SHLIBDEPS ON)
    SET(CPACK_PACKAGE_FILE_NAME "${CPACK_DEBIAN_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}")
    SET(CPACK_DEB_COMPONENT_INSTALL ON)

    IF(KAFKA)
        list(APPEND CPACK_COMPONENTS_ALL avro_kafka_adapter)
    ENDIF(KAFKA)
    
    IF(MAX_CDC)
        list(APPEND CPACK_COMPONENTS_ALL mxs_adapter)
    ENDIF(MAX_CDC)
    
    IF(MAX_KAFKA)
        list(APPEND CPACK_COMPONENTS_ALL mcskafka)
    ENDIF(MAX_KAFKA)

    IF(REMOTE_CPIMPORT)
        list(APPEND CPACK_COMPONENTS_ALL mcsimport)
    ENDIF(REMOTE_CPIMPORT)

    SET(CPACK_DEBIAN_AVRO_KAFKA_ADAPTER_PACKAGE_NAME "mariadb-columnstore-kafka-avro-adapters")
    SET(CPACK_DEBIAN_AVRO_KAFKA_ADAPTER_FILE_NAME "${CPACK_DEBIAN_AVRO_KAFKA_ADAPTER_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}.deb")
    
    SET(CPACK_DEBIAN_MCSKAFKA_PACKAGE_NAME "mariadb-columnstore-kafka-adapters")
    SET(CPACK_DEBIAN_MCSKAFKA_FILE_NAME "${CPACK_DEBIAN_MCSKAFKA_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}.deb")

    SET(CPACK_DEBIAN_MXS_ADAPTER_PACKAGE_NAME "mariadb-columnstore-maxscale-cdc-adapters")
    SET(CPACK_DEBIAN_MXS_ADAPTER_FILE_NAME "${CPACK_DEBIAN_MXS_ADAPTER_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}.deb")

    SET(CPACK_DEBIAN_MCSIMPORT_PACKAGE_NAME "mariadb-columnstore-remote-cpimport")
    SET(CPACK_DEBIAN_MCSIMPORT_FILE_NAME "${CPACK_DEBIAN_MCSIMPORT_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}.deb")

    include(CPack)
ENDIF(DEB)

if (WIN32) #Needs to be changed to accomodate more than one Windows adapter once we are at that point
    IF(REMOTE_CPIMPORT)
        # On Windows find the Microsoft Visual Studio 2017 C++ Redistributable merge module (x64) that is packaged with mcsapi
        SET(MSM_PATHS)
        FOREACH(VISUAL_STUDIO_REDIST_VERSION 14.15.26706 14.14.26405 14.14.25008)
            LIST(APPEND MSM_PATHS "$ENV{ProgramFiles}/Microsoft Visual Studio/2017/Community/VC/Redist/MSVC/${VISUAL_STUDIO_REDIST_VERSION}/MergeModules")
            LIST(APPEND MSM_PATHS "$ENV{ProgramFiles} (x86)/Microsoft Visual Studio/2017/Community/VC/Redist/MSVC/${VISUAL_STUDIO_REDIST_VERSION}/MergeModules")
            LIST(APPEND MSM_PATHS "$ENV{ProgramFiles}/Microsoft Visual Studio/2017/Enterprise/VC/Redist/MSVC/${VISUAL_STUDIO_REDIST_VERSION}/MergeModules")
            LIST(APPEND MSM_PATHS "$ENV{ProgramFiles} (x86)/Microsoft Visual Studio/2017/Enterprise/VC/Redist/MSVC/${VISUAL_STUDIO_REDIST_VERSION}/MergeModules")
            LIST(APPEND MSM_PATHS "$ENV{ProgramFiles}/Microsoft Visual Studio/2017/Professional/VC/Redist/MSVC/${VISUAL_STUDIO_REDIST_VERSION}/MergeModules")
            LIST(APPEND MSM_PATHS "$ENV{ProgramFiles} (x86)/Microsoft Visual Studio/2017/Professional/VC/Redist/MSVC/${VISUAL_STUDIO_REDIST_VERSION}/MergeModules")
        ENDFOREACH()
        find_file(
            VS_2017_REDIST_MSM
            NAMES Microsoft_VC141_CRT_x64.msm Microsoft_VC140_CRT_x64.msm
            HINTS $ENV{MSM_DIRECTORY}
            PATHS ${MSM_PATHS}
        )
        if (${VS_2017_REDIST_MSM} STREQUAL "VS_2017_REDIST_MSM-NOTFOUND")
            MESSAGE(FATAL_ERROR "Could not find a usable Visual Studio 2015/17 C++ Redistributable merge module Microsoft_VC141_CRT_x64.msm!\nYou can hint cmake by setting the environment variable MSM_DIRECTORY.")
        ENDIF()
        MESSAGE(STATUS "Found Visual Studio 2015/17 C++ Redistributable merge module: ${VS_2017_REDIST_MSM}")
        
        SET(CPACK_GENERATOR "WIX")
        SET(CPACK_RESOURCE_FILE_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/mcsimport/LICENSE.txt")
        SET(CPACK_WIX_UI_BANNER "${CMAKE_CURRENT_SOURCE_DIR}/resources/WixUIBannerBmp.jpg")
        SET(CPACK_WIX_UI_DIALOG "${CMAKE_CURRENT_SOURCE_DIR}/resources/WixUIDialogBmp.jpg")
        SET(CPACK_WIX_PRODUCT_ICON "${CMAKE_CURRENT_SOURCE_DIR}/resources/mariadb.ico")
        SET(WIX_PATCH_FILES)
        configure_file(
            "${CMAKE_CURRENT_SOURCE_DIR}/cmake/cpack_windows/redist.patch.in"
            "${CMAKE_CURRENT_SOURCE_DIR}/cmake/cpack_windows/redist.patch"
            IMMEDIATE @ONLY)
        LIST(APPEND WIX_PATCH_FILES "${CMAKE_CURRENT_SOURCE_DIR}/cmake/cpack_windows/redist.patch")
        SET(CPACK_WIX_PATCH_FILE ${WIX_PATCH_FILES})
        SET(CPACK_PACKAGE_NAME "MariaDB ColumnStore Remote Import")
        set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "MariaDB ColumnStore Remote Import")
        set(CPACK_PACKAGE_VENDOR "MariaDB Corporation Ab")
        SET(CPACK_PACKAGE_CONTACT "MariaDB Corporation Ab")
        SET(ENGINE_ARCH "x64")
        SET(CPACK_PACKAGE_VERSION ${VERSION})
        set(CPACK_PACKAGE_VERSION_RELEASE ${RELEASE})
        set(CPACK_PACKAGE_FILE_NAME "${CPACK_PACKAGE_NAME}-${CPACK_PACKAGE_VERSION}-${CPACK_PACKAGE_VERSION_RELEASE}-${ENGINE_ARCH}")
        set(CPACK_WIX_UPGRADE_GUID "62d22168-cbc0-47f7-8875-e4e3ac80552c")
        set(CPACK_WIX_CMAKE_PACKAGE_REGISTRY ON)
        set(CPACK_PACKAGE_INSTALL_DIRECTORY "MariaDB/ColumnStore Remote Import")
        include(CPack)
    ENDIF(REMOTE_CPIMPORT)
endif(WIN32)

if (TEST_RUNNER)
  enable_testing()
endif (TEST_RUNNER)

IF(KAFKA)
    add_subdirectory(kafka-avro-adapter)
ENDIF(KAFKA)

IF(KETTLE)
    add_subdirectory(kettle-columnstore-bulk-exporter-plugin)
ENDIF(KETTLE)

IF(MAX_CDC)
    add_subdirectory(maxscale-cdc-adapter)
ENDIF(MAX_CDC)

IF(MAX_KAFKA)
    add_subdirectory(maxscale-kafka-adapter)
ENDIF(MAX_KAFKA)

IF(REMOTE_CPIMPORT)
    add_subdirectory(mcsimport)
ENDIF (REMOTE_CPIMPORT)

if (TEST_RUNNER)
  include(CTest)
endif (TEST_RUNNER)

add_custom_target(uninstall
    COMMAND ${CMAKE_COMMAND} -P ${CMAKE_CURRENT_BINARY_DIR}/cmake_uninstall.cmake)

MESSAGE(STATUS "-----------------------------------------------")
MESSAGE(STATUS "CMAKE_INSTALL_PREFIX = ${CMAKE_INSTALL_PREFIX}")
MESSAGE(STATUS "CMAKE_BUILD_TYPE = ${CMAKE_BUILD_TYPE}")
MESSAGE(STATUS "KAFKA = ${KAFKA}")
MESSAGE(STATUS "KETTLE = ${KETTLE}")
MESSAGE(STATUS "MAX_CDC = ${MAX_CDC}")
MESSAGE(STATUS "MAX_KAFKA = ${MAX_KAFKA}")
MESSAGE(STATUS "REMOTE_CPIMPORT = ${REMOTE_CPIMPORT}")
MESSAGE(STATUS "TEST_RUNNER = ${TEST_RUNNER}")
MESSAGE(STATUS "RPM = ${RPM}")
MESSAGE(STATUS "DEB = ${DEB}")
MESSAGE(STATUS "Change a values with: cmake -D<Variable>=<Value>")
MESSAGE(STATUS "------------------------------------------------")
MESSAGE(STATUS)

