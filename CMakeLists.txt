cmake_minimum_required(VERSION 2.8.2)

set(VERSION "1.1.8")
set(RELEASE "1")

include(cmake/gitversion.cmake)

option(RPM "Build an RPM" OFF)
option(TEST_RUNNER "Build the test suite" OFF)
option(KAFKA "Build the Kafka-Avro to ColumnStore Data Adatper" ON)
option(KETTLE "Build the Kettle / PDI ColumnStore Bulk Write Plugin" ON)
option(MAX_CDC "Build the MaxScale CDC to ColumnStore Data Adapter" ON)
option(MAX_KAFKA "Build the MaxScale Kafka+CDC to ColumnStore Data Adapter (deprecated)" OFF)

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
        list(APPEND CPACK_COMPONENTS_ALL maxscale-cdc-adapter)
    ENDIF(MAX_CDC)
    
    IF(MAX_KAFKA)
        list(APPEND CPACK_COMPONENTS_ALL mcskafka)
    ENDIF(MAX_KAFKA)

    SET(CPACK_RPM_AVRO_KAFKA_ADAPTER_PACKAGE_NAME "mariadb-columnstore-kafka-avro-adapters")
    SET(CPACK_RPM_AVRO_KAFKA_ADAPTER_FILE_NAME "${CPACK_RPM_AVRO_KAFKA_ADAPTER_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}.rpm")
    
    SET(CPACK_RPM_MCSKAFKA_PACKAGE_NAME "mariadb-columnstore-kafka-adapters")
    SET(CPACK_RPM_MCSKAFKA_FILE_NAME "${CPACK_RPM_MCSKAFKA_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}.rpm")

    SET(CPACK_RPM_MAXSCALE_CDC_ADAPTER_PACKAGE_NAME "mariadb-columnstore-maxscale-cdc-adapters")
    SET(CPACK_RPM_MAXSCALE_CDC_ADAPTER_FILE_NAME "${CPACK_RPM_MAXSCALE_CDC_ADAPTER_PACKAGE_NAME}-${CPACK_RPM_PACKAGE_VERSION}-${CPACK_RPM_PACKAGE_RELEASE}-${ENGINE_ARCH}-${RPM}.rpm")

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
            add_dependencies(package maxscale-cdc-adapter)
            add_custom_command(
                TARGET package
                POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E rename "*-maxscale-cdc-adapter.rpm" "${CPACK_RPM_MAXSCALE_CDC_ADAPTER_FILE_NAME}"
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
        list(APPEND CPACK_COMPONENTS_ALL maxscale-cdc-adapter)
    ENDIF(MAX_CDC)
    
    IF(MAX_KAFKA)
        list(APPEND CPACK_COMPONENTS_ALL mcskafka)
    ENDIF(MAX_KAFKA)

    SET(CPACK_DEBIAN_AVRO_KAFKA_ADAPTER_PACKAGE_NAME "mariadb-columnstore-kafka-avro-adapters")
    SET(CPACK_DEBIAN_AVRO_KAFKA_ADAPTER_FILE_NAME "${CPACK_DEBIAN_AVRO_KAFKA_ADAPTER_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}.deb")
    
    SET(CPACK_DEBIAN_MCSKAFKA_PACKAGE_NAME "mariadb-columnstore-kafka-adapters")
    SET(CPACK_DEBIAN_MCSKAFKA_FILE_NAME "${CPACK_DEBIAN_MCSKAFKA_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}.deb")

    SET(CPACK_DEBIAN_MAXSCALE-CDC-ADAPTER_PACKAGE_NAME "mariadb-columnstore-maxscale-cdc-adapters")
    SET(CPACK_DEBIAN_MAXSCALE-CDC-ADAPTER_FILE_NAME "${CPACK_DEBIAN_MAXSCALE-CDC-ADAPTER_PACKAGE_NAME}-${CPACK_DEBIAN_PACKAGE_VERSION}-${CPACK_DEBIAN_PACKAGE_RELEASE}-${ENGINE_ARCH}-${DEB}.deb")

    include(CPack)
ENDIF(DEB)

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
MESSAGE(STATUS "TEST_RUNNER = ${TEST_RUNNER}")
MESSAGE(STATUS "RPM = ${RPM}")
MESSAGE(STATUS "DEB = ${DEB}")
MESSAGE(STATUS "Change a values with: cmake -D<Variable>=<Value>")
MESSAGE(STATUS "------------------------------------------------")
MESSAGE(STATUS)
