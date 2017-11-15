# Test setup for MariaDB / MaxScale /  mxs_adapter/ mcs(columnstore)

Instructions for setting up docker environment:

```
docker-compose build
```

Bring up your cluster

```
docker-compose up -d
```

When you're done and want to clean up:

```
docker-compose stop
docker-compose rm -v -f
```

## To do testing once all the instances are up

### (1)  Check the running instance like this

```
docker-compose ps

The output should look like
               Name                               Command                               State                                Ports
-------------------------------------------------------------------------------------------------------------------------------------------------
test_mariadb1_1              docker-entrypoint.sh --log ...       Up                                   0.0.0.0:14306->3306/tcp
test_mariadb2_1              docker-entrypoint.sh --log ...       Up                                   3306/tcp, 0.0.0.0:14307->3307/tcp
test_mariadb3_1              docker-entrypoint.sh --log ...       Up                                   3306/tcp, 0.0.0.0:14308->3308/tcp
test_maxscale_1              maxscale -d -l stdout                Up                                   0.0.0.0:13306->13306/tcp,
                                                                                                               0.0.0.0:13307->13307/tcp,
                                                                                                               0.0.0.0:4001->4001/tcp,
                                                                                                               0.0.0.0:8003->8003/tcp,
                                                                                                               0.0.0.0:9003->9003/tcp
test_mcs_1                   /usr/sbin/runit_bootstrap            Up                                   0.0.0.0:14309->3306/tcp, 8800/tcp
test_mxs_adapter_1           /usr/sbin/runit_bootstrap            Up                                   0.0.0.0:14310->3306/tcp, 80/tcp

```

### (2) Adding cdcuser to MaxScale

Get into MaxScale instance's bash shell like this and execute the follwing
command.

```
docker exec -it test_maxscale_1 bash
maxadmin call command cdc add_user avro-service cdcuser cdc
exit
```

### (3) Building mxs_adapter inside the container

Get into mxs_adapter instance's bash shell and execute the follwing commands.

```
docker exec -it test_mxs_adapter_1 bash
cd /install
```

#### mcs-api build

```
scl enable devtoolset-4 bash
cd mariadb-columnstore-api
cmake -DCMAKE_INSTALL_PREFIX=/usr .
make
make install
```

#### cdc-connector build

```
cd ../maxscale-cdc-connector
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr
make
make install
cd ../..
```

#### mxs-adapter build

```
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters/maxscale-cdc-adapter/ -DCMAKE_INSTALL_PREFIX=/usr
make
make install
```

### (4) Exit back out of mxs_adapter container

```
exit
```

### (5) Copy Columnstore.xml

```
cd mcs
docker cp Columnstore.xml test_mxs_adapter_1:/usr/local/mariadb/columnstore/etc/
```

### (6) Connecting to MASTER DB

Open separate tab. From the command line use following to connect to Master DB.

```
mariadb -h 127.0.0.1  -P 14306 -uappuser -papp-pass
```

When prompted for password provide `app-pass`. Now you can create your test
databse and test table here. Any data inserted here should arrive on columnstore
once mxs_adapter is started in later steps.

### (7) Connecting to MaxScale binlog service to check SLAVE STATUS

Open separate tab. From the command line user following to connect to MaxScale
binlog service

```
mariadb -h 127.0.0.1 -P 9003 -u repl -ppass
```

When Prompted for password provide `pass`. Now you can do SHOW SLAVE STATUS to
make sure that MaxScale is talking to Master as SLAVE

### (8) Connecting to ColumnStore DB

Open serpate tab
From the command line use following

```
mariadb -h 127.0.0.1 -P 14309
```

Here you can see the data arrivig the test table as the data is inserted in same
table on Master Databse, once mxs_adapter is started in later steps.

### (9) Run mxs_adapter

```
docker inspect test_maxscale_1 -f "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}"
```

Note down the IP address as indicated by the ping.

Now you are ready to run mxs_adapter

```
./mxs_adapter -h <MaxScale IP> -P 4001 -u cdcuser -p cdc -r 5 -n test t1
```

### (10) Inserting data on Master and Verifying on ColumnStore

Create database test and table t1 on Master and same table definition on
ColumnStore Insert rows on t1 on Master and then do `SELECT * FROM t1` on
ColumnStore side to verify data arriving.
