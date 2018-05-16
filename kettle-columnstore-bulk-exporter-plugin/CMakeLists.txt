cmake_minimum_required(VERSION 2.8)

include_directories(..)

set(PDI_PLUGIN
  "${CMAKE_CURRENT_SOURCE_DIR}/build/distributions/kettle-columnstore-bulk-exporter-plugin.zip"
)

ADD_CUSTOM_COMMAND(OUTPUT ${PDI_PLUGIN} 
	COMMAND "${CMAKE_CURRENT_SOURCE_DIR}/gradlew" -p ${CMAKE_CURRENT_SOURCE_DIR} plugin
	COMMENT "building the pdi plugin"
)

ADD_CUSTOM_TARGET(pdi-plugin ALL DEPENDS ${PDI_PLUGIN})

IF(TEST_RUNNER)
  add_test(NAME PDI_PLUGIN_TEST_SUITE COMMAND "${CMAKE_CURRENT_SOURCE_DIR}/test/test.sh")
ENDIF(TEST_RUNNER)
