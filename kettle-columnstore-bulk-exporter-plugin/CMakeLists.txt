cmake_minimum_required(VERSION 2.8)

project(cs_pdi_plugin)
set(CMAKE_MODULE_PATH ${CMAKE_SOURCE_DIR}/cmake)

IF(UNIX)
  INCLUDE(Findmcsapi)
  get_filename_component(MCSAPI_LIB_PATH ${MCSAPI_LIBRARIES} PATH)
ENDIF(UNIX)
IF(WIN32)
  INCLUDE(Findmcsapi_windows)
  get_filename_component(JAVA_MCSAPI_LIBRARY_PATH ${JAVA_MCSAPI_LIBRARY} PATH)
ENDIF(WIN32)

if (NOT MCSAPI_FOUND)
    IF(UNIX)
        MESSAGE(FATAL_ERROR "Could not find mcsapi")
    ENDIF(UNIX)
    IF(WIN32)
        MESSAGE(FATAL_ERROR "Could not find mcsapi.\nYou can hint cmake by setting the environment variable MCSAPI_INSTALL_DIR")
    ENDIF(WIN32)
endif()

set(PDI_PLUGIN
  "${CMAKE_CURRENT_SOURCE_DIR}/build/distributions/mariadb-columnstore-kettle-bulk-exporter-plugin-${VERSION}.zip"
)

IF(UNIX)
  ADD_CUSTOM_COMMAND(OUTPUT ${PDI_PLUGIN} 
      COMMAND "${CMAKE_CURRENT_SOURCE_DIR}/gradlew" -p ${CMAKE_CURRENT_SOURCE_DIR} -Pversion=${VERSION} -PmcsapiLibPath=${MCSAPI_LIB_PATH} plugin
      COMMENT "building the pdi plugin"
  )
ENDIF(UNIX)
IF(WIN32)
  ADD_CUSTOM_COMMAND(OUTPUT ${PDI_PLUGIN} 
      COMMAND "${CMAKE_CURRENT_SOURCE_DIR}/gradlew.bat" -p ${CMAKE_CURRENT_SOURCE_DIR} -b "build_win.gradle" -Pversion=${VERSION} -PmcsapiRuntimeLibrary=${MCSAPI_RUNTIME_LIBRARY} -PmcsapiLibxml2RuntimeLibrary=${MCSAPI_LIBXML2_RUNTIME_LIBRARY} -PmcsapiLibiconvRuntimeLibrary=${MCSAPI_LIBICONV_RUNTIME_LIBRARY} -PmcsapiLibuvRuntimeLibrary=${MCSAPI_LIBUV_RUNTIME_LIBRARY} -PjavamcsapiLibraryPath=${JAVA_MCSAPI_LIBRARY_PATH} -PjavamcsapiRuntimeLibrary=${JAVA_MCSAPI_RUNTIME_LIBRARY} plugin
      COMMENT "building the pdi plugin"
  )
ENDIF(WIN32)

ADD_CUSTOM_TARGET(pdi-plugin ALL DEPENDS ${PDI_PLUGIN})

IF(DEB OR RPM)
  ADD_CUSTOM_COMMAND(
    TARGET pdi-plugin POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
    ${CMAKE_CURRENT_SOURCE_DIR}/build/distributions/mariadb-columnstore-kettle-bulk-exporter-plugin-${VERSION}.zip
    ${CMAKE_BINARY_DIR}/mariadb-columnstore-kettle-bulk-exporter-plugin-${VERSION}-${RELEASE}.zip
  )
ENDIF(DEB OR RPM)
IF(WIN32)
  ADD_CUSTOM_COMMAND(
    TARGET pdi-plugin POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
    ${CMAKE_CURRENT_SOURCE_DIR}/build/distributions/kettle-columnstore-bulk-exporter-plugin-${VERSION}.zip
    ${CMAKE_BINARY_DIR}/mariadb-columnstore-kettle-bulk-exporter-plugin-${VERSION}-${RELEASE}.zip
  )
ENDIF(WIN32)

IF(TEST_RUNNER)
  IF(UNIX)
    add_test(NAME PDI_plugin_test_suite COMMAND "${CMAKE_CURRENT_SOURCE_DIR}/test/test.sh")
  ENDIF(UNIX)
  IF(WIN32)
    add_test(NAME PDI_plugin_test_suite COMMAND powershell -File ${CMAKE_CURRENT_SOURCE_DIR}/test/test.ps1)
  ENDIF(WIN32)
ENDIF(TEST_RUNNER)
