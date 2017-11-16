import unittest
import pymysql
import json
import os

class MyTestCase(unittest.TestCase):
    """The base class for all tests, creates a connection to both the master and ColumnStore"""

    config = json.load(open("config.json")) # Should be in current working directory
    master_config = config['master']
    cs_config = config['columnstore']

    def startAdapter(self, args):
        """Starts the adapter with the provided arguments"""
        self.stopAdapter()
        cmd = ['docker', 'exec', '-ti', 'mxs_adapter', 'mxs_adapter']
        self.adapter = os.Popen(cmd + args)

    def stopAdapter(self):
        """Stops the adapter"""
        if self.adapter != None:
            try:
                self.adapter.terminate()
                self.adapter.wait(timeout=10)
            except TimeoutExpired as ex:
                self.adapter.kill()

    def setUp(self):
        """Perform test case setup"""
        self.master = pymysql.connect(host=self.master_config['host'],
                                     port=self.master_config['port'],
                                     user=self.master_config['user'],
                                     password=self.master_config['password'])

        self.cs = pymysql.connect(host=self.cs_config['host'],
                                 port=self.cs_config['port'],
                                 user=self.cs_config['user'],
                                 password=self.cs_config['password'])
        self.adapter = None

    def tearDown(self):
        """Perform test case teardown"""
        self.master.close()
        self.cs.close()
        self.stopAdapter()
