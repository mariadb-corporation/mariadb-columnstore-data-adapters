# Standard FIND_PACKAGE module for the MaxScale CDC Connector, sets the following variables:
#   - CDC_FOUND
#   - CDC_INCLUDE_DIRS (only if CDC_FOUND)
#   - CDC_LIBRARIES (only if CDC_FOUND)

# Try to find the header
FIND_PATH(CDC_INCLUDE_DIR NAMES cdc_connector.h)

# Try to find the library
FIND_LIBRARY(CDC_LIBRARY NAMES cdc_connector)

# Handle the QUIETLY/REQUIRED arguments, set MCSAPI_FOUND if all variables are
# found
INCLUDE(FindPackageHandleStandardArgs)
FIND_PACKAGE_HANDLE_STANDARD_ARGS(CDC
                                  REQUIRED_VARS
                                  CDC_LIBRARY
                                  CDC_INCLUDE_DIR)

# Hide internal variables
MARK_AS_ADVANCED(CDC_INCLUDE_DIR CDC_LIBRARY)

# Set standard variables
IF(CDC_FOUND)
    SET(CDC_INCLUDE_DIRS "${CDC_INCLUDE_DIR}")
    SET(CDC_LIBRARIES "${CDC_LIBRARY}")
ENDIF()
