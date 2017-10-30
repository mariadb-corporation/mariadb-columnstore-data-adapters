# Download and build the MaxScale CDC connector

set(CDC_REPO "TODO: Add this" CACHE INTERNAL "MaxScale CDC connector")

ExternalProject_Add(cdc-connector
 GIT_REPOSITORY ${CDC_REPO}
 CMAKE_ARGS -DCMAKE_INSTALL_PREFIX=${CMAKE_BINARY_DIR}/cdc-connector/install
 BINARY_DIR ${CMAKE_BINARY_DIR}/cdc-connector
 INSTALL_DIR ${CMAKE_BINARY_DIR}/cdc-connector/install
 UPDATE_COMMAND "")

set(CDC_FOUND TRUE CACHE INTERNAL "")
set(CDC_STATIC_FOUND TRUE CACHE INTERNAL "")
set(CDC_INCLUDE_DIR ${CMAKE_BINARY_DIR}/cdc-connector/install/include CACHE INTERNAL "")
set(CDC_STATIC_LIBRARIES ${CMAKE_BINARY_DIR}/cdc-connector/install/lib/libcdc-connector.a CACHE INTERNAL "")
set(CDC_LIBRARIES ${CDC_STATIC_LIBRARIES} CACHE INTERNAL "")
