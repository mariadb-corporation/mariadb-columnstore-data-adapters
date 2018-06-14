/*
 * Copyright (c) 2017 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2020-09-01
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

#include <libmcsapi/mcsapi.h>
#include <maxscale/cdc_connector.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <memory>
#include <unordered_set>
#include <limits.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <signal.h>
#include <assert.h>
#include <execinfo.h>

bool processTable(mcsapi::ColumnStoreDriver *driver, CDC::Connection * cdcConnection, std::string dbName,
                  std::string tblName);
int processRowRcvd(CDC::SRow& row, mcsapi::ColumnStoreBulkInsert *bulk, mcsapi::ColumnStoreSystemCatalogTable& table);
std::string readGTID(std::string DbName, std::string TblName);
int writeGTID(std::string DbName, std::string TblName, std::string gtID);

// Last processed GTID
static std::string lastGTID;

// Number of rows for each bulk insert
static int rowLimit = 1;

// Read timeout
static size_t timeOut = 10;

// Read timeout
static bool withMetadata = true;

// Flush data after being idle for this many seconds
static int idleFlushPeriod = 5;

// Whether we have read any rows since the last flush
static bool haveRows = false;

class Logger
{
public:

    Logger():
        m_ref(&std::cout)
    {
    }

    std::ostream& operator()()
    {
        return *m_ref;
    }

    bool open(std::string file)
    {
        m_logfile.open(file);
        return m_logfile.good();
    }

private:
    std::ostream* m_ref;
    std::ofstream m_logfile;
};

using std::endl;
static Logger logger;

extern char *optarg;
extern int optind, opterr, optopt;
static char program_name[PATH_MAX + 1];

void usage()
{
    logger() << "Usage: " << program_name << " [OPTION]... DATABASE TABLE" << endl
             << endl
             << "  DATABASE       Target database" << endl
             << "  TABLE          Table to stream" << endl
             << endl
             << "  -h HOST      MaxScale host" << endl
             << "  -P PORT      Port number where the CDC service listens" << endl
             << "  -u USER      Username for the MaxScale CDC service" << endl
             << "  -p PASSWORD  Password of the user" << endl
             << "  -c CONFIG    Path to the Columnstore.xml file (installed by MariaDB ColumnStore)" << endl
             << "  -r ROWS      Number of events to group for one bulk load (default: " << rowLimit << ")" << endl
             << "  -t TIME      Connection timeout (default: 10)" << endl
             << "  -n           Disable metadata generation (timestamp, GTID, event type)" << endl
             << "  -i TIME      Flush data after being idle for this many seconds (default: " << idleFlushPeriod << ")" << endl
             << "  -l FILE      Log output to filename given as argument" << endl
             << endl;
}

static bool setSignal(int sig, void (*f)(int))
{
    bool rval = true;
    struct sigaction sigact;
    memset(&sigact, 0, sizeof(sigact));
    sigact.sa_handler = f;

    int err;

    do
    {
        errno = 0;
        err = sigaction(sig, &sigact, NULL);
    }
    while (errno == EINTR);

    if (err < 0)
    {
        logger() << "Failed to set signal: " << strerror(errno) << endl;
        rval = false;
    }

    return rval;
}

static bool running = true;

static void signalHandler(int sig)
{
    if (running)
    {
        logger() << "\nShutting down in " << timeOut << " seconds..." << endl;
        running = false;
    }
    else
    {
        logger() << "\nTerminating immediately" << endl;
        setSignal(sig, SIG_DFL);
        raise(sig);
    }
}

static void fatalHandler(int sig)
{
    void* addrs[128];
    logger() << "Received fatal signal " << sig << endl;
    int count = backtrace(addrs, sizeof(addrs) / sizeof(addrs[0]));
    backtrace_symbols_fd(addrs, count, STDOUT_FILENO);
    setSignal(sig, SIG_DFL);
    raise(sig);
}

static void configureSignals()
{
    std::map<int, void(*)(int)> signals =
    {
        std::make_pair(SIGTERM, signalHandler),
        std::make_pair(SIGINT, signalHandler),
        std::make_pair(SIGSEGV, fatalHandler),
        std::make_pair(SIGABRT, fatalHandler),
        std::make_pair(SIGFPE, fatalHandler)
    };

    for (auto a : signals)
    {
        if (!setSignal(a.first, a.second))
        {
            exit(1);
        }
    }
}

static bool isMetadataField(const std::string& field)
{
    static std::unordered_set<std::string> metadataFields =
        {
            "domain",
            "event_number",
            "event_type",
            "sequence",
            "server_id",
            "timestamp"
        };

    return metadataFields.find(field) != metadataFields.end();
}

int main(int argc, char *argv[])
{
    int rval = 0;
    char c;
    std::ofstream logfile;
    std::string config;
    std::string mxsIPAddr = "127.0.0.1";
    int mxsCDCPort = 4001;
    std::string mxsUser = "admin";
    std::string mxsPassword  = "mariadb";
    strcpy(program_name, basename(argv[0]));
    configureSignals();

    while ((c = getopt(argc, argv, "l:h:P:p:u:c:r:t:i:n")) != -1)
    {
        switch (c)
        {
        case 'l':
            if (!logger.open(optarg))
            {
                logger() << "Failed to open logfile:" << optarg << endl;
                exit(1);
            }
            break;

        case 'h':
            mxsIPAddr = optarg;
            break;

        case 'r':
            rowLimit = atoi(optarg);
            break;

        case 't':
            timeOut = atoi(optarg);
            break;

        case 'P':
            mxsCDCPort = atoi(optarg);
            break;

        case 'u':
            mxsUser = optarg;
            break;

        case 'p':
            mxsPassword = optarg;
            break;

        case 'n':
            withMetadata = false;
            break;

        case 'i':
            idleFlushPeriod = atoi(optarg);
            break;

        case 'c':
            config = optarg;
            break;

        default:
            usage();
            exit(1);
            break;
        }
    }

    if (argc - optind != 2)
    {
        // Missing arguments
        usage();
        exit(1);
    }

    std::string mxsDbName = argv[optind];
    std::string mxsTblName = argv[optind + 1];
    mcsapi::ColumnStoreDriver* driver = 0;

    try
    {
        driver = config.empty() ? new mcsapi::ColumnStoreDriver() : new mcsapi::ColumnStoreDriver(config);
        // Here is where make connection to MaxScale to receive CDC
        CDC::Connection cdcConnection(mxsIPAddr, mxsCDCPort, mxsUser, mxsPassword, timeOut);

        //  TODO: one thread per table, for now one table per process
        if (!processTable(driver, &cdcConnection, mxsDbName, mxsTblName))
        {
            rval = 1;
        }
        // which table to insert into
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        logger() << "Caught ColumnStore error: " << e.what() << endl;
        rval = 1;
    }
    delete driver;

    return rval;
}

std::string getCreateFromSchema(std::string db, std::string tbl, CDC::ValueMap fields)
{
    std::stringstream ss;
    bool first = true;

    ss << "CREATE TABLE " << db << "." << tbl << " (";

    for (const auto& a : fields)
    {

        if (withMetadata || !isMetadataField(a.first))
        {
            if (first)
            {
                first = false;
            }
            else
            {
                ss << ", ";
            }

            ss << a.first << " " << a.second;
        }
    }

    ss << ") ENGINE=ColumnStore;";

    return ss.str();
}

void flushBatch(mcsapi::ColumnStoreDriver* driver,
                std::shared_ptr<mcsapi::ColumnStoreBulkInsert>& bulk,
                std::string dbName, std::string tblName,
                int rowCount, bool reconnect)
{
    bulk->commit();
    mcsapi::ColumnStoreSummary summary = bulk->getSummary();
    logger() << summary.getRowsInsertedCount() << " rows and " << rowCount << " transactions inserted in " <<
        summary.getExecutionTime() << " seconds. GTID = " << lastGTID << endl;
    writeGTID(dbName, tblName, lastGTID);

    if (reconnect)
    {
        // Reconnect to ColumnStore for more inserts
        bulk.reset(driver->createBulkInsert(dbName, tblName, 0, 0));
    }
    else
    {
        // Close the connection and free table locks
        bulk.reset();
    }
}

bool processTable(mcsapi::ColumnStoreDriver* driver, CDC::Connection * cdcConnection, std::string dbName,
                  std::string tblName)
{
    // one bulk object per table
    std::shared_ptr<mcsapi::ColumnStoreBulkInsert> bulk;
    CDC::SRow row;
    std::string tblReq = dbName + "." + tblName;
    std::string gtid = readGTID(dbName, tblName); //GTID that was last processed in last run
    bool rv = true;

    try
    {
        // read the data being sent from MaxScale CDC Port
        if (cdcConnection->connect(tblReq, gtid))
        {
            mcsapi::ColumnStoreSystemCatalogTable& table = driver->getSystemCatalog().getTable(dbName, tblName);
            if (!gtid.empty()) //skip the row for lastGTID, as it was processed in last run
            {
                lastGTID = gtid;
                logger() << "Continuing from GTID " << gtid << endl;
                cdcConnection->read();
            }
            int rowCount = 0;
            time_t init = time(NULL);
            size_t n_reads = 0;

            while (running)
            {
                if (!(row = cdcConnection->read()))
                {
                    if (cdcConnection->error() == CDC::TIMEOUT)
                    {
                        if (difftime(time(NULL), init) >= idleFlushPeriod)
                        {
                            // We have been idle for too long. If a bulk insert is active and we
                            // have data to send, flush it to ColumnStore. If we have an open bulk
                            // insert but no data, simply close the bulk to release table locks.
                            if (haveRows)
                            {
                                flushBatch(driver, bulk, dbName, tblName, rowCount, false);
                                rowCount = 0;
                                haveRows = false;
                                init = time(NULL);
                            }
                            else if (bulk)
                            {
                                bulk.reset();
                            }
                        }

                        // Timeout, try again
                        n_reads++;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }

                // Start a bulk insert if we're not doing one already
                if (!bulk)
                {
                    bulk.reset(driver->createBulkInsert(dbName, tblName, 0, 0));
                }

                // We have an event, reset the timeout counter
                n_reads = 0;

                // Take each field and build the bulk->setColumn(<colnum>, columnValue);
                // Convert the binary row object received from MaxScale to mcsAPI bulk object.
                std::string currentGTID = lastGTID;

                if (processRowRcvd(row, bulk.get(), table))
                {
                    init = time(NULL);
                    haveRows = true;

                    if (!currentGTID.empty() && lastGTID != currentGTID)
                    {
                        rowCount++;
                    }

                    if (rowCount >= rowLimit)
                    {
                        flushBatch(driver, bulk, dbName, tblName, rowCount, true);
                        rowCount = 0;
                        haveRows = false;
                    }
                }
                else
                {
                    logger() << "Empty Row Received" << endl;
                }
            }

            if (running)
            {
                // We're stopping because of an error
                logger() << "Failed to read row: " << cdcConnection->error() << endl;
            }
            if (bulk)
            {
                bulk->rollback();
            }
        }
        else
        {
            logger() << "MaxScale connection could not be created: " << cdcConnection->error() << endl;
            rv = false;
        }
    }
    catch (mcsapi::ColumnStoreNotFound &e)
    {
        rv = false;

        // Try to read a row from the CDC connection
        if (cdcConnection->read())
        {
            logger() << "Table not found, create with:" << endl << endl
                     << "    " << getCreateFromSchema(dbName, tblName, cdcConnection->fields()) << endl
                     << endl;
        }
        else
        {
            logger() << "Failed to read row: " << cdcConnection->error() << endl;
        }
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        logger() << "Caught ColumnStore error: " << e.what() << endl;
        rv = false;
    }

    return rv;
}

// Process a row received from MaxScale CDC Connector and add it into ColumnStore Bulk object
int processRowRcvd(CDC::SRow& row, mcsapi::ColumnStoreBulkInsert *bulk, mcsapi::ColumnStoreSystemCatalogTable& table)
{
    size_t fc = row->length();
    assert(fc > 6);

    for ( size_t i = 0; i < fc; i++)
    {
        if (withMetadata || !isMetadataField(row->key(i)))
        {
            // TBD how is null value provided by cdc connector API ? process accordingly
            // row.key[i] is the columnname and row.value(i) is value in string form for the ith column
            uint32_t pos = table.getColumn(row->key(i)).getPosition();
            bulk->setColumn(pos, row->value(i));
        }
    }
    lastGTID = row->gtid();
    bulk->writeRow();

    return (int)fc;
}

std::string readGTID(std::string DbName, std::string TblName)
{
    std::ifstream afile;
    std::string retVal = "";

    afile.open(DbName + "." + TblName);
    afile >> retVal;
    afile.close();
    return retVal;
}

int writeGTID(std::string DbName, std::string TblName, std::string gtID)
{
    std::ofstream afile;
    afile.open(DbName + "." + TblName);

    afile << gtID << endl;
    afile.close();
    return 1;
}
