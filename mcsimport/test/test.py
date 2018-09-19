#!/usr/bin/python

import os, sys, subprocess, datetime, yaml, csv
import mysql.connector as mariadb

DB_NAME = 'test'

# main test program
def executeTestSuite():
    if len(sys.argv) != 2:
        print(sys.argv[0], "mcsimport_executable")
        sys.exit(666)

    test_path = os.path.dirname(os.path.realpath(__file__))
    global mcsimport_executable
    mcsimport_executable = sys.argv[1]

    #check if the provided mcsimport_executable is valid
    try:
        subprocess.call([mcsimport_executable])
    except Exception as e:
        print("mcsimport couldn't be executed\n", e)
        sys.exit(667)

    # set up necessary variables
    global user
    user = "root"
    global host
    host = "localhost"
    if os.environ.get("MCSAPI_CS_TEST_IP") is not None:
        host=os.environ.get("MCSAPI_CS_TEST_IP")
    if os.environ.get("MCSAPI_CS_TEST_USER") is not None:
        user=os.environ.get("MCSAPI_CS_TEST_USER")
    global password
    password = os.environ.get("MCSAPI_CS_TEST_PASSWORD")

    # test execution main loop
    print("")
    total_tests = 0
    failed_tests = 0
    for file in os.listdir(test_path):
        if (os.path.isdir(os.path.join(test_path, file))):
            total_tests +=1
            test_directory = os.path.join(test_path, file)
            failed = executeTest(test_directory)
            if failed:
                failed_tests += 1
    
    print("\n%d tests failed out of %d" %(failed_tests,total_tests))
    sys.exit(failed_tests)

# executes a test of a sub-directory and returns True if the test failed
def executeTest(test_directory):
    failed = False
    print("Execute test from directory %s" %(test_directory,))
    
    # load the test configuration
    try:
        testConfig = loadTestConfig(test_directory)
        print("Configuration loaded for test: %s" % (testConfig["name"]))
    except Exception as e:
        print("Error while processing test configuration %s\nError: %s\nTest failed\n" %(os.path.join(test_directory,"config.yaml"),e))
        return True
    
    # execute the prepare_test method to generate needed test files if prepare.py is present in the test directory
    if os.path.exists(os.path.join(test_directory,"prepare.py")):
        sys.path.append(test_directory)
        try:
            from prepare import prepare_test, cleanup_test
            prepare_test(test_directory)
        except Exception as e:
            print("Error during the processing of %s.\nError: %s\nTest failed\n" %(os.path.join(test_directory,"prepare.py"),e))
            failed = True
        finally:
            sys.path.remove(test_directory)
        if failed:
            return True
        else:
            print("Executed custom prepare_test() method")    
    
    # execute the DDL to prepare the test table if DDL.sql is present in test directory
    if os.path.exists(os.path.join(test_directory,"DDL.sql")):
        failed = prepareColumnstoreTable(os.path.join(test_directory,"DDL.sql"),testConfig["table"])
        if failed:
            print("Test failed\n")
            return True
        else:
            print("Test target tables created")
    
    # do the actual injection via mcsimport
    failed = executeMcsimport(test_directory,testConfig)
    if failed:
        print("Test failed\n")
        return True
    else:
        print("mcsimport executed and return code validated")
    
    # validate the test results line by line if expected.csv is found
    failed = validateInjection(test_directory,testConfig["table"])
    if failed:
        print("Test failed\n")
        return True
    else:
        print("Injection validated successfull against expected.csv")
    
    # clean up generated input files through prepare.py's cleanup_test method
    if os.path.exists(os.path.join(test_directory,"prepare.py")):
        try:
            cleanup_test(test_directory)
        except Exception as e:
            print("Error during the processing of %s.\nError: %s\nTest failed\n" %(os.path.join(test_directory,"prepare.py"),e))
            return True
        print("Executed custom cleanup_test() method")
    
    # clean up the columnstore test table
    failed = cleanUpColumnstoreTable(testConfig["table"])
    if failed:
        print("Test failed\n")
        return True
    else:
        print("Test target tables cleaned up")
    
    
    print("Test succeeded\n")
    return False

# parses the configuration file, loads the test config, and throws an exception if it fails
def loadTestConfig(test_directory):
    with open(os.path.join(test_directory,"config.yaml"), 'r') as stream:
        testConfig = yaml.load(stream)
    if testConfig["name"] is None:
        raise Exception("test name couldn't be extracted from configuration")
    if testConfig["expected_exit_value"] is None:
        raise Exception("test expected exit value couldn't be extracted from configuration")
    return testConfig
    
# executes the SQL statements of given file to set up the test table
def prepareColumnstoreTable(file, table):
    error = False
    try:
        conn = mariadb.connect(user=user, password=password, host=host, database=DB_NAME)
        cursor = conn.cursor();
        cursor.execute("DROP TABLE IF EXISTS %s" %(table,))
        with open(file) as f:
            content = f.readlines()
            content = [x.strip() for x in content]
            for line in content:
                cursor.execute(line)
    except mariadb.Error as err:
        print("Error during SQL operation while processing %s.\nLine: %s\nError: %s" %(file,line,err))
        error = True
    except Exception as e:
        print("Error while processing %s.\nError: %s" %(file,e))
        error = True
    finally:
        if cursor: cursor.close()
        if conn: conn.close()
    
    return error

# executes mcsimport and compares the expected return code. Returns True if the return code doesn't match.
def executeMcsimport(test_directory,testConfig):
    
    # forge the test command to execute
    cmd = [mcsimport_executable, DB_NAME, testConfig["table"]]
    if os.path.exists(os.path.join(test_directory,"input.csv")):
        cmd.append(os.path.join(test_directory,"input.csv"))
    if os.path.exists(os.path.join(test_directory,"mapping.yaml")):
        cmd.append("-m %s" % (os.path.join(test_directory,"mapping.yaml"),))
    if testConfig["delimiter"] is not None:
        cmd.append("-d %s" % (testConfig["delimiter"],))
    if testConfig["date_format"] is not None:
        cmd.append("-df %s" % (testConfig["date_format"],))
    if testConfig["default_non_mapped"]:
        cmd.append("-default_non_mapped")
    
    try:
        subprocess.check_output(cmd,stderr=subprocess.STDOUT,universal_newlines=True)
    except subprocess.CalledProcessError as exc: # this exception is raised once the return code is not 0
        if testConfig["expected_exit_value"] != exc.returncode: # therefore we have to validate mcsimport's return code here
            print("Error while executing mcsimport.\nCommand: %s\nmcsimport output: %s\nError: mcsimport's actual return code of %d doesn't match the expected return code of %d" %(cmd, exc.output, exc.returncode, testConfig["expected_exit_value"]))
            return True
        else:
            return False
    except Exception as e:
        print("Error while executing mcsimport.\nCommand: %s\nError: %s" %(cmd,e,))
        return True
    
    if testConfig["expected_exit_value"] != 0: # we further have to validate mcsimport's return code here in case it was 0 and didn't raise the exception
        print("Error while executing mcsimport.\nCommand: %s\nError: mcsimport's actual return code of 0 doesn't match the expected return code of %d" %(cmd, testConfig["expected_exit_value"]))
        return True
    
    return False

# validates if the injected values match the expected values
def validateInjection(test_directory,table):
    error = False
    try:
        conn = mariadb.connect(user=user, password=password, host=host, database=DB_NAME)
        cursor = conn.cursor();
        # validate that the number of rows of expected.csv and target table match
        cursor.execute("SELECT COUNT(*) AS cnt FROM %s" % (table,))
        cnt = cursor.fetchone()[0]
        num_lines = sum(1 for line in open(os.path.join(test_directory,"expected.csv")))
        assert num_lines == cnt, "the number of injected rows: %d doesn't match the number of expected rows: %d" % (cnt, num_lines)
        
        # validate that each input line in expected.csv was injected into the target table
        with open(os.path.join(test_directory,"expected.csv")) as csv_file:
            csv_reader = csv.reader(csv_file, delimiter=',')
            for line in csv_reader:
                if line[0] != "":
                    cursor.execute("SELECT * FROM %s WHERE id=%s" % (table,line[0]))
                else:
                    cursor.execute("SELECT * FROM %s WHERE id IS NULL" % (table,))
                row = cursor.fetchone()
                assert row is not None, "no target row could be fetched for expected id: %s" %(str(line[0]))
                
                for i in range(len(line)):
                    if row[i] is None:
                        assert line[i] == "", "target and expected don't match.\ntarget:   %s\nexpected: %s\ntarget item: NULL doesn't match expected item: %s" % (row,line,str(line[i]))
                    else:
                        assert str(line[i]) == str(row[i]), "target and expected don't match.\ntarget:   %s\nexpected: %s\ntarget item: %s doesn't match expected item: %s" % (row,line,str(row[i]),str(line[i]))
        
    except mariadb.Error as err:
        print("SQL error during ColumnStore validation operation.\nError: %s" %(err,))
        error = True
    except AssertionError as er:
        print("Assertion error during ColumnStore validation operation.\nError: %s" %(er,))
        error = True
    except Exception as e:
        print("Error during ColumnStore validation operation.\nError: %s" %(e,))
        error = True
    finally:
        if cursor: cursor.close()
        if conn: conn.close()
    
    return error
    
# cleans up the ColumnStore test table
def cleanUpColumnstoreTable(table):
    error = False
    try:
        conn = mariadb.connect(user=user, password=password, host=host, database=DB_NAME)
        cursor = conn.cursor();
        cursor.execute("DROP TABLE IF EXISTS %s" %(table,))
    except mariadb.Error as err:
        print("Error during ColumnStore test table cleanup operation.\nError: %s" %(err,))
        error = True
    finally:
        if cursor: cursor.close()
        if conn: conn.close()
    
    return error
    
# execute the test suite
executeTestSuite()
