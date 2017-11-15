#!/bin/bash

# Extra arguments are passed to `docker-compose build`
docker-compose build $@
docker-compose -p test up -d

# Build the adapter
./build.sh

docker cp mcs/Columnstore.xml test_mxs_adapter_1:/usr/local/mariadb/columnstore/etc/
docker exec -it test_maxscale_1 maxadmin call command cdc add_user avro-service cdcuser cdc
