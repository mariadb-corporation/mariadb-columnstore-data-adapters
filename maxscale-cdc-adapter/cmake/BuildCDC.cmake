# Download and build the MaxScale CDC connector

if (NOT DEFINED CDC_REPO)
  message(FATAL_ERROR "Define path to the CDC connector repository with: -DCDC_REPO=/path/to/cdc_connector")
endif()

ExternalProject_Add(cdc_connector
 SOURCE_DIR ${CDC_REPO}
 CMAKE_ARGS -DCMAKE_INSTALL_PREFIX=${CMAKE_BINARY_DIR}/cdc_connector/install
 BINARY_DIR ${CMAKE_BINARY_DIR}/cdc_connector
 INSTALL_DIR ${CMAKE_BINARY_DIR}/cdc_connector/install
 UPDATE_COMMAND "")

set(CDC_FOUND TRUE CACHE INTERNAL "")
set(CDC_STATIC_FOUND TRUE CACHE INTERNAL "")
set(CDC_INCLUDE_DIR ${CMAKE_BINARY_DIR}/cdc_connector/install/include CACHE INTERNAL "")
set(CDC_STATIC_LIBRARIES ${CMAKE_BINARY_DIR}/cdc_connector/install/${CMAKE_INSTALL_LIBDIR}/libcdc_connector.a CACHE INTERNAL "")
set(CDC_LIBRARIES ${CDC_STATIC_LIBRARIES} CACHE INTERNAL "")
