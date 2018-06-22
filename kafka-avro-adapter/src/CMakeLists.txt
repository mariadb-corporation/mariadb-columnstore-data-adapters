add_executable(kafka_to_avro kafka_to_avro.cpp common.cpp http_request.cpp controller.cpp kafka_consumer.cpp cs_producer.cpp)
target_link_libraries(kafka_to_avro ${MCSAPI_LIBRARIES} ${JANSSON_LIBRARIES} ${RDKAFKAPP_LIBRARIES} ${AVRO_LIBRARIES} curl pthread)
install(TARGETS kafka_to_avro DESTINATION "${CMAKE_INSTALL_BINDIR}" COMPONENT avro_kafka_adapter)

if (TEST_RUNNER)
  add_subdirectory(test)
endif()
