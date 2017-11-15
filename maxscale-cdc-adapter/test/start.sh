#!/bin/bash

# Extra arguments are passed to `docker-compose build`
docker-compose build $@
docker-compose -p test up -d

# Build the adapter
./build.sh

docker cp mcs/Columnstore.xml mxs_adapter:/usr/local/mariadb/columnstore/etc/
docker exec -it maxscale maxadmin call command cdc add_user avro-service cdcuser cdc
