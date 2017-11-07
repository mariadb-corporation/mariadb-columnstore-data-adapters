# Standard FIND_PACKAGE module for mcsapi, sets the following variables:
#   - MCSAPI_FOUND
#   - MCSAPI_INCLUDE_DIRS (only if MCSAPI_FOUND)
#   - MCSAPI_LIBRARIES (only if MCSAPI_FOUND)

# Try to find the header
FIND_PATH(MCSAPI_INCLUDE_DIR NAMES mcsapi.h PATH_SUFFIXES libmcsapi)

# Try to find the library
FIND_LIBRARY(MCSAPI_LIBRARY NAMES mcsapi)

# Handle the QUIETLY/REQUIRED arguments, set MCSAPI_FOUND if all variables are
# found
INCLUDE(FindPackageHandleStandardArgs)
FIND_PACKAGE_HANDLE_STANDARD_ARGS(MCSAPI
                                  REQUIRED_VARS
                                  MCSAPI_LIBRARY
                                  MCSAPI_INCLUDE_DIR)

# Hide internal variables
MARK_AS_ADVANCED(MCSAPI_INCLUDE_DIR MCSAPI_LIBRARY)

# Set standard variables
IF(MCSAPI_FOUND)
    SET(MCSAPI_INCLUDE_DIRS "${MCSAPI_INCLUDE_DIR}")
    SET(MCSAPI_LIBRARIES "${MCSAPI_LIBRARY}")
ENDIF()

