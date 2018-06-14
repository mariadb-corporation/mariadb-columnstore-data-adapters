cmake_minimum_required(VERSION 2.8)

project(cs_pdi_plugin)
set(CMAKE_MODULE_PATH ${CMAKE_SOURCE_DIR}/cmake)

INCLUDE(Findmcsapi)
if (NOT MCSAPI_FOUND)
    MESSAGE(FATAL_ERROR "Could not find mcsapi")
endif()
get_filename_component(MCSAPI_LIB_PATH ${MCSAPI_LIBRARIES} PATH)

set(PDI_PLUGIN
  "${CMAKE_CURRENT_SOURCE_DIR}/build/distributions/mariadb-columnstore-kettle-bulk-exporter-plugin-${VERSION}.zip"
)

ADD_CUSTOM_COMMAND(OUTPUT ${PDI_PLUGIN} 
	COMMAND "${CMAKE_CURRENT_SOURCE_DIR}/gradlew" -p ${CMAKE_CURRENT_SOURCE_DIR} -Pversion=${VERSION} -PmcsapiLibPath=${MCSAPI_LIB_PATH} plugin
	COMMENT "building the pdi plugin"
)

ADD_CUSTOM_TARGET(pdi-plugin ALL DEPENDS ${PDI_PLUGIN})

IF(DEB OR RPM)
  ADD_CUSTOM_COMMAND(
    TARGET pdi-plugin POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
    ${CMAKE_CURRENT_SOURCE_DIR}/build/distributions/mariadb-columnstore-kettle-bulk-exporter-plugin-${VERSION}.zip
    ${CMAKE_BINARY_DIR}/mariadb-columnstore-kettle-bulk-exporter-plugin-${VERSION}.zip
  )
ENDIF(DEB OR RPM)

IF(TEST_RUNNER)
  add_test(NAME PDI_plugin_test_suite COMMAND "${CMAKE_CURRENT_SOURCE_DIR}/test/test.sh")
ENDIF(TEST_RUNNER)
