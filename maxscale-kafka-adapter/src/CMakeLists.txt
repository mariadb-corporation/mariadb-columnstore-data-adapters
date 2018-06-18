set(KAFKA_FILES
    mcskafka.cpp
    cdc_consumer.cpp
    cdc_parser.cpp
)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D__FILENAME__='\"$(subst ${CMAKE_SOURCE_DIR}/,,$(abspath $<))\"'")

add_executable(mcskafka ${KAFKA_FILES})

target_link_libraries(mcskafka ${MCSAPI_LIBRARIES} ${JANSSON_LIBRARIES} ${RDKAFKAPP_LIBRARIES})

INSTALL(TARGETS mcskafka
    RUNTIME DESTINATION "${CMAKE_INSTALL_BINDIR}" COMPONENT mcskafka
)
