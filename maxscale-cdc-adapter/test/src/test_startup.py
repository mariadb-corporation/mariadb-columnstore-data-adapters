import core
import time

class StartupTest(core.MyTestCase):

    def test_no_table(self):
        # The first error should be about missing Avro file for this table
        self.startAdapter(['test', 't1'])
        output, errs = self.adapter.communicate(timeout=15)
        self.assertNotEqual(self.stopAdapter(), 0, msg=output)
        self.assertTrue('test.t1' in output, msg="Output should have 'test.t1' in it: %s" % output)


    def test_table_only_in_master(self):
        # Create a table in master and insert some data
        with self.master.cursor() as a:
            values = range(1, 10)
            a.execute('CREATE TABLE test.t1 (id int)')
            for i in values:
                a.execute('INSERT INTO test.t1 VALUES (%s)', i)
            a.execute('SELECT * FROM test.t1')
            self.assertEqual(len(a.fetchall()), len(values))

        # Wait for a bit, start the adapter, expect an error
        time.sleep(5)
        self.startAdapter(['test', 't1'])
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
        self.startAdapter(['test', 't1'])
        time.sleep(5)
        self.assertEqual(self.stopAdapter(), 0)
        output, errs = self.adapter.communicate()
        self.assertTrue(len(output) > 0, msg="Output:" + output)
        self.assertTrue('GTID' in output, msg="Output:" + output)


    def test_already_existing_table(self):
        """ Check that adapter works properly when both tables already exist """
        with self.master.cursor() as a, self.cs.cursor() as b:
            a.execute('CREATE TABLE test.t2 (id int)')
            a.execute('INSERT INTO test.t2 VALUES (1), (2), (3)')
            b.execute('CREATE TABLE test.t2 (id int) ENGINE=ColumnStore')

        time.sleep(5)
        output = self.runAdapter(['-n', 'test', 't2'], should_work=True, sleep=5)
        self.assertTrue(len(output) >= 3, msg="Output should be at least three lines: " + '\n'.join(output))


    def test_wrong_table_type(self):
        """ Create the table as InnoDB instead of ColumnStore """
        with self.master.cursor() as a, self.cs.cursor() as b:
            a.execute('CREATE TABLE test.t2 (id int)')
            b.execute('CREATE TABLE test.t2 (id int) ENGINE=InnoDB')
            a.execute('INSERT INTO test.t2 VALUES (1), (2), (3)')

        time.sleep(5)
        output = '\n'.join(self.runAdapter(['-n', 'test', 't2'], should_work=True, sleep=5))
        self.assertTrue('Table not found, create with' in output, msg=output)


    def test_stored_position(self):
        """ Check that the last GTID position is properly stored """
        with self.master.cursor() as a, self.cs.cursor() as b:
            a.execute('CREATE TABLE test.t3 (id int)')
            b.execute('CREATE TABLE test.t3 (id int) ENGINE=ColumnStore')
            for i in range(0, 3):
                a.execute('INSERT INTO test.t3 VALUES (%d)' % i)

        time.sleep(5)
        output = self.runAdapter(['-n', 'test', 't3'], should_work=True, sleep=5)
        self.assertTrue(len(output) >= 3, msg="Output should be at least three lines: " + '\n'.join(output))

        with self.master.cursor() as a:
            for i in range(0, 3):
                a.execute('INSERT INTO test.t3 VALUES (%d)' % i)

        output = self.runAdapter(['-n', 'test', 't3'], should_work=True, sleep=5)
        self.assertTrue('continuing' in '\n'.join(output), msg="adapter shoud continue from previous GTID: " + '\n'.join(output))
        self.assertTrue(len(output) >= 3, msg="Output should be at least three lines: " + '\n'.join(output))
