# mariadb-columnstore-data-adapters

See individual README files for more information.

* [maxscale-cdc-adapter](maxscale-cdc-adapter/README.md)
* [maxscale-kafka-adapter](maxscale-kafka-adapter/README.md)
* [kafka-avro-adapter](kafka-avro-adapter/README.md)
* [kettle-columnstore-plugin](kettle-columnstore-bulk-exporter-plugin/README.md)

## Packaging

The first three adapters can be packaged as RPM and DEB packages, the last one as zip file.
 To enable packaging, add
`-DRPM=<suffix>` for RPM packages or `-DDEB=<suffix>` for DEB packages. The
`<suffix>` will be appended as the last component of the package name. This is
used to label the OS of the package.

Example installation, test and package build:
```shell
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters.git
cd mariadb-columnstore-data-adapters
mkdir build && cd build
cmake -DCMAKE_INSTALL_PREFIX=/usr -DTEST_RUNNER=ON ..
make
sudo make install
make test
cmake -DRPM=centos7 ..
make package
```
