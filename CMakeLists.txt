cmake_minimum_required(VERSION 2.8)

set(CPACK_ARCHIVE_COMPONENT_INSTALL ON)

option(KAFKA "Build the Kafka-Avro to ColumnStore Data Adatper" ON)

IF(KAFKA)
	add_subdirectory(kafka-avro-adapter)
ENDIF(KAFKA)

option(KETTLE "Build the Kettle / PDI ColumnStore Bulk Write Plugin" ON)

IF(KETTLE)
	add_subdirectory(kettle-columnstore-bulk-exporter-plugin)
ENDIF(KETTLE)

option(MAX_CDC "Build the MaxScale CDC to ColumnStore Data Adapter" OFF)

IF(MAX_CDC)
	add_subdirectory(maxscale-cdc-adapter)
ENDIF(MAX_CDC)

option(MAX_KAFKA "Build the MaxScale Kafka+CDC to ColumnStore Data Adapter" ON)

IF(MAX_KAFKA)
	add_subdirectory(maxscale-kafka-adapter)
ENDIF(MAX_KAFKA)

option(TEST_RUNNER "Build the test suite" OFF)

if (TEST_RUNNER)
  include(CTest)
#  find_package(GTest REQUIRED)
endif (TEST_RUNNER)

option(DEB "Build a DEB" OFF)
option(RPM "Build an RPM" OFF)

MESSAGE(STATUS "-----------------------------------------------")
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
