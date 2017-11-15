import unittest
import pymysql
import json
import os

class MyTestCase(unittest.TestCase):
    """The base class for all tests, creates a connection to both the master and ColumnStore"""

    config = json.load(open("config.json")) # Should be in current working directory
    master_config = config['master']
    cs_config = config['columnstore']

    def setUp(self):
        self.master = pymysql.connect(host=self.master_config['host'],
                                     port=self.master_config['port'],
                                     user=self.master_config['user'],
                                     password=self.master_config['password'])

        self.cs = pymysql.connect(host=self.cs_config['host'],
                                 port=self.cs_config['port'],
                                 user=self.cs_config['user'],
                                 password=self.cs_config['password'])

    def tearDown(self):
        self.master.close()
        self.cs.close()

    def test_something(self):
        with self.master.cursor() as a, self.cs.cursor() as b:
            a.execute("SELECT 1")
            b.execute("SELECT 1")
            self.assertEqual(a.fetchone(), b.fetchone())

    def test_something_else(self):
        with self.master.cursor() as a, self.cs.cursor() as b:
            a.execute("SELECT 2")
            b.execute("SELECT 1")
            self.assertNotEqual(a.fetchone(), b.fetchone())
