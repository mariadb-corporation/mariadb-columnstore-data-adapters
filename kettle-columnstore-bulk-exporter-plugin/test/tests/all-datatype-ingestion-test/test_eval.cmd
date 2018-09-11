fc jdbc-innodb.csv jdbc-columnstore.csv
if %ERRORLEVEL% neq 0 ( echo "csv exports JDBC innodb and JDBC columnstore don't match - FAILURE" && exit /b )
fc jdbc-innodb.csv api-columnstore.csv
if %ERRORLEVEL% neq 0 ( echo "csv exports JDBC innodb and API columnstore don't match - FAILURE" && exit /b )
fc api-columnstore.csv jdbc-columnstore.csv
if %ERRORLEVEL% neq 0 ( echo "csv exports API columnstore and JDBC columnstore don't match - FAILURE" && exit /b )
exit 0