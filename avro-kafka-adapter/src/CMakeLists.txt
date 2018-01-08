add_executable(kafka_to_avro kafka_to_avro.cpp common.cpp http_request.cpp)
target_link_libraries(kafka_to_avro ${MCSAPI_LIBRARIES} ${JANSSON_LIBRARIES} ${RDKAFKAPP_LIBRARIES} avro curl)
install(TARGETS kafka_to_avro DESTINATION "${CMAKE_INSTALL_BINDIR}")

add_executable(avro_to_cs avro_to_cs.cpp common.cpp http_request.cpp)
target_link_libraries(avro_to_cs ${MCSAPI_LIBRARIES} ${JANSSON_LIBRARIES} ${RDKAFKAPP_LIBRARIES} avro curl)
install(TARGETS avro_to_cs DESTINATION "${CMAKE_INSTALL_BINDIR}")

if (WITH_TESTS)
  add_subdirectory(test)
endif()
