# Download and build the MaxScale CDC connector

ExternalProject_Add(cdc-connector
 GIT_REPOSITORY "https://github.com/mariadb-corporation/maxscale-cdc-connector"
 CMAKE_ARGS -DCMAKE_INSTALL_PREFIX=${CMAKE_BINARY_DIR}/cdc-connector/install -DJANSSON_INCLUDE_DIR=${JANSSON_INCLUDE_DIR} -DCMAKE_INSTALL_LIBDIR=${CMAKE_INSTALL_LIBDIR}
 BINARY_DIR ${CMAKE_BINARY_DIR}/cdc-connector
 INSTALL_DIR ${CMAKE_BINARY_DIR}/cdc-connector/install
 UPDATE_COMMAND "")

set(CDC_FOUND TRUE CACHE INTERNAL "")
set(CDC_STATIC_FOUND TRUE CACHE INTERNAL "")
set(CDC_INCLUDE_DIR ${CMAKE_BINARY_DIR}/cdc-connector/install/include CACHE INTERNAL "")
set(CDC_STATIC_LIBRARIES ${CMAKE_BINARY_DIR}/cdc-connector/install/${CMAKE_INSTALL_LIBDIR}/libcdc_connector.a CACHE INTERNAL "")
set(CDC_LIBRARIES ${CDC_STATIC_LIBRARIES} CACHE INTERNAL "")
