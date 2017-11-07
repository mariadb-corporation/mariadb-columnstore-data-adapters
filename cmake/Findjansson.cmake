# Standard FIND_PACKAGE module for jansson, sets the following variables:
#   - JANSSON_FOUND
#   - JANSSON_INCLUDE_DIRS (only if JANSSON_FOUND)
#   - JANSSON_LIBRARIES (only if JANSSON_FOUND)

# Try to find the header
FIND_PATH(JANSSON_INCLUDE_DIR NAMES jansson.h)

# Try to find the library
FIND_LIBRARY(JANSSON_LIBRARY NAMES jansson)

# Handle the QUIETLY/REQUIRED arguments, set MCSAPI_FOUND if all variables are
# found
INCLUDE(FindPackageHandleStandardArgs)
FIND_PACKAGE_HANDLE_STANDARD_ARGS(JANSSON
                                  REQUIRED_VARS
                                  JANSSON_LIBRARY
                                  JANSSON_INCLUDE_DIR)

# Hide internal variables
MARK_AS_ADVANCED(JANSSON_INCLUDE_DIR JANSSON_LIBRARY)

# Set standard variables
IF(JANSSON_FOUND)
    SET(JANSSON_INCLUDE_DIRS "${JANSSON_INCLUDE_DIR}")
    SET(JANSSON_LIBRARIES "${JANSSON_LIBRARY}")
ENDIF()

