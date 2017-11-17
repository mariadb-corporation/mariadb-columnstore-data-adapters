import core
import time

class StartupTest(core.MyTestCase):

    def test_startup_no_table(self):
        # The first error should be about missing Avro file for this table
        self.startAdapter(['test', 't1'])
        output, errs = self.adapter.communicate(timeout=15)
        self.assertNotEqual(self.stopAdapter(), 0, msg=output)
        self.assertTrue('test.t1' in output, msg="Output should have 'test.t1' in it: %s" % output)

        # Create a table in master and insert some data
        with self.master.cursor() as a:
            values = range(1, 10)
            a.execute('CREATE TABLE test.t1 (id int)')
            for i in values:
                a.execute('INSERT INTO test.t1 VALUES (%s)', i)
            a.execute('SELECT * FROM test.t1')
            self.assertEqual(len(a.fetchall()), len(values))

        # Start adapter, expect error
        self.startAdapter(['tesẗ́', 't1'])
        output, errs = self.adapter.communicate(timeout=15)
        self.assertNotEqual(self.stopAdapter(), 0, msg=output)
        self.assertTrue('Table not found, create with' in output, msg=output)
        self.assertTrue('CREATE TABLE' in output, msg=output)

        # Create the table in ColumnStore by using the CREATE statement from the adapter
        with self.cs.cursor() as b:
            create_stmt = [x for x in output.split('\n') if 'CREATE TABLE' in x]
            self.assertEqual(len(create_stmt), 1)
            b.execute(create_stmt[0])

        # Start the adapter with a five second timeout, expect data before it
        self.startAdapter(['tesẗ́', 't1'])
        time.sleep(5)
        self.assertEqual(self.stopAdapter(), 0)
        output, errs = self.adapter.communicate()
        self.assertTrue(len(output) > 0, msg="Output:" + output)
        self.assertTrue('GTID' in output, msg="Output:" + output)
