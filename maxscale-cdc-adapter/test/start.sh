#!/bin/bash

# Extra arguments are passed to `docker-compose build`
cd docker
docker-compose build $@
docker-compose up -d
cd ..

# Build the adapter
./build.sh

# Create the CDC user in MaxScale
docker exec -it maxscale maxadmin call command cdc add_user avro-service cdcuser cdc
