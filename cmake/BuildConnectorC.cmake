# Build the MariaDB Connector-C from source

ExternalProject_Add(connector-c
  GIT_REPOSITORY "https://github.com/MariaDB/mariadb-connector-c.git"
  GIT_TAG "v3.0.6"
  CMAKE_ARGS -DWITH_CURL=N -DCMAKE_INSTALL_PREFIX=${CMAKE_CURRENT_BINARY_DIR})

set(CONNECTOR_C_INCLUDE_DIRS ${CMAKE_CURRENT_BINARY_DIR}/include/mariadb CACHE INTERNAL "")
set(CONNECTOR_C_LIBRARIES ${CMAKE_CURRENT_BINARY_DIR}/lib/mariadb/libmariadbclient.a CACHE INTERNAL "")
