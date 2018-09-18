#!/usr/bin/python

import os, sys, subprocess

if len(sys.argv) != 2:
    print sys.argv[0], "mcsimport_executable"
    sys.exit(666)

test_path = os.path.dirname(os.path.realpath(__file__))
mcsimport_executable = sys.argv[1]

#check if the provided mcsimport_executable is valid
try:
    subprocess.call([mcsimport_executable])
except Exception as e:
    print "mcsimport couldn't be executed\n", e
    sys.exit(1)

# set up necessary variables

