# Standard FIND_PACKAGE module for rdkafka++, sets the following variables:
#   - RDKAFKAPP_FOUND
#   - RDKAFKAPP_INCLUDE_DIRS (only if RDKAFKAPP_FOUND)
#   - RDKAFKAPP_LIBRARIES (only if RDKAFKAPP_FOUND)

# Try to find the header
FIND_PATH(RDKAFKAPP_INCLUDE_DIR NAMES rdkafkacpp.h PATH_SUFFIXES librdkafka)

# Try to find the library
FIND_LIBRARY(RDKAFKAPP_LIBRARY NAMES rdkafka++)

# Handle the QUIETLY/REQUIRED arguments, set RDKAFKAPP_FOUND if all variables are
# found
INCLUDE(FindPackageHandleStandardArgs)
FIND_PACKAGE_HANDLE_STANDARD_ARGS(RDKAFKAPP
                                  REQUIRED_VARS
                                  RDKAFKAPP_LIBRARY
                                  RDKAFKAPP_INCLUDE_DIR)

# Hide internal variables
MARK_AS_ADVANCED(RDKAFKAPP_INCLUDE_DIR RDKAFKAPP_LIBRARY)

# Set standard variables
IF(RDKAFKAPP_FOUND)
    SET(RDKAFKAPP_INCLUDE_DIRS "${RDKAFKAPP_INCLUDE_DIR}")
    SET(RDKAFKAPP_LIBRARIES "${RDKAFKAPP_LIBRARY}")
ENDIF()

