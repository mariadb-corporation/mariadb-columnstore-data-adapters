#!/bin/bash
#
# This script starts the example application which generates data and then exits.

cd /app/ && mvn exec:java -Dexec.mainClass=com.example.app.App
