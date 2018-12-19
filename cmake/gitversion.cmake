exec_program("git"
    ${CMAKE_CURRENT_SOURCE_DIR}
    ARGS "describe --match=NeVeRmAtCh --always --dirty"
    OUTPUT_VARIABLE GIT_VERSION)
    
# releasenum is used by external scripts for various tasks. Leave it alone.
CONFIGURE_FILE(${CMAKE_CURRENT_SOURCE_DIR}/cmake/gitversionDataAdapters.in ${CMAKE_CURRENT_BINARY_DIR}/gitversionDataAdapters IMMEDIATE)