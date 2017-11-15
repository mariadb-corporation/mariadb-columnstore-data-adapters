# Test setup

These are the instructions for setting up the testing environment for the
MaxScale to ColumnStore data adapter.

The test suite depends heavily on Docker containers and the current user must be
able to execute the `docker` command. See
[this page](https://docs.docker.com/engine/installation/linux/linux-postinstall/#manage-docker-as-a-non-root-user)
for instructions on how to do it.

## TL;DR

Here's how you run the test suite.

```
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters.git
cd mariadb-columnstore-data-adapters/maxscale-cdc-adapter/test/
./start.sh
./run_test.sh
```

For more details, read the following sections.

## Preparing the environment

Run the following command to prepare the test environment.

```
./start.sh
```

This builds and starts the `docker-compose` setup, install the current code for
the adapter and prepares the testing setup. Any extra arguments to the
`start.sh` script are passed to `docker build`. Add the `--no-cache` parameter
to force a rebuild of all images.

## Running tests

To run the test, run the following command.

```
./run_test.sh
```

It will output the results of each test which will also be written into the
`test.log` file.

If you need to build the adapter again, run the following command.

```
./build.sh
```

## Cleaning up

When you're done and want to clean up, run the follwing command.

```
./stop.sh
```

This will stop the running containers and remove their volumes.

If you want to remove the built images, remove them with `docker image rm`. You
can see the images with `docker image ls`.

## Setup details

The setup has three MariaDB containers, `mariadb1`, `mariadb2` and
`mariadb3`. `mariadb1` is the master for `maridb2` and `mariadb3`. The
`maxscale` container contains MaxScale with a CDC setup replicating from
`mariadb1`. The `mcs` container has MariaDB ColumnStore and the `mxs_adapter`
container has the adapter itself. The tests are run from the `tester` container.
