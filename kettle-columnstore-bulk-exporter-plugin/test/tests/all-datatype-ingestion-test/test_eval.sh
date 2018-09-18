failures=0

diff jdbc-innodb.csv jdbc-columnstore.csv
if [ $? -eq 0 ]; then
echo "csv exports JDBC innodb and JDBC columnstore match - OK"
else
echo "csv exports JDBC innodb and JDBC columnstore don't match - FAILURE"
failures=$((failures+1))
fi

diff jdbc-innodb.csv api-columnstore.csv
if [ $? -eq 0 ]; then
echo "csv exports JDBC innodb and API columnstore match - OK"
else
echo "csv exports JDBC innodb and API columnstore don't match - FAILURE"
failures=$((failures+1))
fi

diff api-columnstore.csv jdbc-columnstore.csv
if [ $? -eq 0 ]; then
echo "csv exports API columnstore and JDBC columnstore match - OK"
else
echo "csv exports API columnstore and JDBC columnstore don't match - FAILURE"
failures=$((failures+1))
fi

if [ $failures -eq 0 ]; then
echo "test succeeded"
exit 0
else
echo "test failed"
exit 1
fi