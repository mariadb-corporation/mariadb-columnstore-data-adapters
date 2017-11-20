import unittest
import pymysql
import json
import time
import subprocess

def getContainerIP(container):
    p = subprocess.run(['docker', 'inspect', container, '-f "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}"'],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
    return p.stdout.strip()[1:-1]

class MyTestCase(unittest.TestCase):
    """The base class for all tests, creates a connection to both the master and ColumnStore"""

    config = json.load(open("config.json")) # Should be in current working directory
    for i in config:
        if 'host' in config[i]:
            config[i]['host'] = getContainerIP(config[i]['host'])


    def startAdapter(self, args):
        """Starts the adapter with the provided arguments"""
        self.stopAdapter()
        cmd = ['docker', 'exec', '-i', 'mxs_adapter', '/usr/bin/mxs_adapter', '-ucdcuser', '-pcdc', '-hmaxscale', '-P4001']

        # Run the process in /tmp to make cleanup between tests easier (last GTID is stored in a file in $PWD)
        self.adapter = subprocess.Popen(cmd + args, stdout=subprocess.PIPE, universal_newlines=True)


    def stopAdapter(self):
        """Stops the adapter"""
        ret = -1

        if self.adapter != None:
            if self.adapter.returncode == None:
                try:
                    self.adapter.terminate()
                    self.adapter.wait(timeout=10)

                except:
                    self.adapter.kill()
                    self.adapter.wait()

            ret = self.adapter.returncode

        return 0 if ret == -15 else ret


    def runAdapter(self, args, sleep = 5, should_work = True):
        """Run the adapter, wait for a while and return the output.

        Keyword arguments:

        should_work   Should the adapter execution succeed
        sleep         How long to wait before stopping the adapter
        """
        self.startAdapter(args)
        time.sleep(sleep)
        self.stopAdapter()
        output, errs = self.adapter.communicate()
        return output.split('\n')


    def restartContainer(self, container):
        wd = self.config['environment']['docker-compose']
        subprocess.run(['docker-compose', 'rm', '-vfs', container], cwd=wd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        subprocess.run(['docker-compose', 'up', '-d'], cwd=wd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def connect(self, target):
        wait_max = 30
        for i in range(0, wait_max):
            try:
                return pymysql.connect(host=self.config[target]['host'],
                               port=self.config[target]['port'],
                               user=self.config[target]['user'],
                               password=self.config[target]['password'],
                               autocommit=True)
            except Exception as ex:
                if i < wait_max - 1:
                    time.sleep(1)
                else:
                    raise ex

    def setUp(self):
        """Perform test case setup"""

        # Remove old state tracking files
        subprocess.run(['docker', 'exec', '-i', 'mxs_adapter', 'bash', '-c', 'rm -f /test.*'])

        self.master = self.connect('master')
        self.cs = self.connect('columnstore')
        self.adapter = None

        with self.master.cursor() as a, self.cs.cursor() as b:
            for c in ['DROP DATABASE IF EXISTS test',
                      'CREATE DATABASE test',
                      'RESET MASTER',
                      'USE test']:
                a.execute(c)
                b.execute(c)

        # Restart the MaxScale container to reset avro files
        self.restartContainer('maxscale')
        self.connect('maxscale')


    def tearDown(self):
        """Perform test case teardown"""
        self.master.close()
        self.cs.close()
        self.stopAdapter()
