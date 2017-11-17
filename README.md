# mariadb-columnstore-data-adapters

See individual README files for more information.

* [maxscale-cdc-adapter](maxscale-cdc-adapter/README.md)
* [maxscale-kafka-adapter](maxscale-kafka-adapter/README.md)

## Packaging

Both adapters can be packaged as RPM and DEB packages. To enable packaging, add
`-DRPM=<suffix>` for RPM packages or `-DDEB=<suffix>` for DEB packages. The
`<suffix>` will be appended as the last component of the package name. This is
used to label the OS of the package.

For example, `-DRPM=centos7` would produce an RPM package of the following form
for the MaxScale CDC to ColumnStore adapter.

```
mariadb-columnstore-maxscale-cdc-adapter-1.1.2-1-x86_64-centos7.rpm
```
