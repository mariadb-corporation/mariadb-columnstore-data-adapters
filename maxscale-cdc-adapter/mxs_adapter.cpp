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

#include <assert.h>
#include <errno.h>
#include <execinfo.h>
#include <limits.h>
#include <signal.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include <unordered_set>

#include <libmcsapi/mcsapi.h>
#include <maxscale/cdc_connector.h>

#include "config.h"

typedef mcsapi::ColumnStoreDriver Driver;
typedef std::unique_ptr<mcsapi::ColumnStoreBulkInsert> Bulk;
typedef mcsapi::ColumnStoreSystemCatalogTable TableInfo;

// MaxScale hostname
static std::string mxsHost = "127.0.0.1";

// MaxScale port
static int mxsPort = 4001;

// MaxScale user
static std::string mxsUser = "admin";

// MaxScale password
static std::string mxsPassword  = "mariadb";

// Last processed GTID
static std::string lastGTID;

// Path to the directory where the state files are stored
static std::string stateFileDir = DEFAULT_STATE_DIR;

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
             << "  -h HOST      MaxScale host (default: " << mxsHost << ")" << endl
             << "  -P PORT      Port number where the CDC service listens (default: " << mxsPort << ")" << endl
             << "  -u USER      Username for the MaxScale CDC service (default: " << mxsUser << ")" << endl
             << "  -p PASSWORD  Password of the user (default: " << mxsPassword << ")" << endl
             << "  -c CONFIG    Path to the Columnstore.xml file (installed by MariaDB ColumnStore)" << endl
             << "  -s           Directory used to store the state files (default: '" << DEFAULT_STATE_DIR << "')" << endl
             << "  -r ROWS      Number of events to group for one bulk load (default: " << rowLimit << ")" << endl
             << "  -t TIME      Connection timeout (default: 10)" << endl
             << "  -n           Disable metadata generation (timestamp, GTID, event type)" << endl
             << "  -i TIME      Flush data after being idle for this many seconds (default: " << idleFlushPeriod << ")" << endl
             << "  -l FILE      Log output to FILE instead of stdout" << endl
             << "  -v           Print version and exit" << endl
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

std::string readGtid(std::string db, std::string table)
{
    std::string rval;
    std::string filename = stateFileDir + "/" + db + "." + table;
    std::ifstream f(filename);

    if (f.good())
    {
        f >> rval;
    }
    else if (errno != ENOENT)
    {
        logger() << "Failed to open state file '" << filename << "' for reading: "
                 << errno << ", " << strerror(errno) << endl;
    }

    return rval;
}

void writeGtid(std::string db, std::string table, std::string gtid)
{
    std::string filename = stateFileDir + "/" + db + "." + table;
    std::ofstream f(filename);

    if (f.good())
    {
        f << gtid << endl;
    }
    else
    {
        logger() << "Failed to open state file '" << filename << "' for writing: "
                 << errno << ", " << strerror(errno) << endl;
    }
}

std::string getCreateFromSchema(std::string db, std::string tbl, CDC::ValueMap fields)
{
    bool first = true;
    std::stringstream ss;
    ss << "CREATE TABLE " << db << "." << tbl << " (";

    for (auto&& a : fields)
    {
        if (withMetadata || !isMetadataField(a.first))
        {
            ss << (first ? "" : ", ")  << a.first << " " << a.second;
            first = false;
        }
    }

    ss << ") ENGINE=ColumnStore;";

    return ss.str();
}

void flushBatch(Driver* driver, Bulk& bulk, std::string db, std::string table,
                int rowCount, bool reconnect)
{
    bulk->commit();
    writeGtid(db, table, lastGTID);

    mcsapi::ColumnStoreSummary summary = bulk->getSummary();

    logger()
        << summary.getRowsInsertedCount() << " rows, "
        << rowCount << " transactions inserted over "
        << summary.getExecutionTime() << " seconds. "
        << "GTID = " << lastGTID << endl;

    bulk.reset(reconnect ? driver->createBulkInsert(db, table, 0, 0) : nullptr);
}

// Process a row received from MaxScale CDC Connector and add it into ColumnStore Bulk object
void processRowRcvd(CDC::SRow& row, Bulk& bulk, TableInfo& table)
{
    assert(row->length() > 6);

    for (size_t i = 0; i < row->length(); i++)
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
}

bool processTable(Driver* driver, CDC::Connection* cdcConnection,
                  std::string db, std::string table)
{
    Bulk bulk;
    std::string identifier = db + "." + table;
    std::string gtid = readGtid(db, table); // GTID that was last processed in last run
    bool rv = true;

    try
    {
        // read the data being sent from MaxScale CDC Port
        if (cdcConnection->connect(identifier, gtid))
        {
            //skip the row for lastGTID, as it was processed in last run
            if (!gtid.empty())
            {
                lastGTID = gtid;
                logger() << "Continuing from GTID " << gtid << endl;
                cdcConnection->read();
            }

            int rowCount = 0;
            time_t init = time(NULL);

            while (running)
            {
                if (CDC::SRow row = cdcConnection->read())
                {
                    // Start a bulk insert if we're not doing one already
                    if (!bulk)
                    {
                        bulk.reset(driver->createBulkInsert(db, table, 0, 0));
                    }

                    // Take each field and build the bulk->setColumn(<colnum>, columnValue);
                    // Convert the binary row object received from MaxScale to mcsAPI bulk object.
                    std::string currentGTID = lastGTID;

                    TableInfo& info = driver->getSystemCatalog().getTable(db, table);
                    processRowRcvd(row, bulk, info);

                    init = time(NULL);
                    haveRows = true;

                    if (!currentGTID.empty() && lastGTID != currentGTID)
                    {
                        rowCount++;
                    }

                    if (rowCount >= rowLimit)
                    {
                        flushBatch(driver, bulk, db, table, rowCount, true);
                        rowCount = 0;
                        haveRows = false;
                    }
                }
                else
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
                                flushBatch(driver, bulk, db, table, rowCount, false);
                                rowCount = 0;
                                haveRows = false;
                                init = time(NULL);
                            }
                            else if (bulk)
                            {
                                // We've been idle for too long, close the connection
                                // to release locks
                                bulk.reset();
                            }
                        }
                    }
                    else
                    {
                        break;
                    }
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
                     << "    " << getCreateFromSchema(db, table, cdcConnection->fields()) << endl
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

int main(int argc, char *argv[])
{
    int rval = 0;
    char c;
    std::string config;
    strcpy(program_name, basename(argv[0]));
    configureSignals();

    while ((c = getopt(argc, argv, "l:h:P:p:u:c:r:t:i:s:nv")) != -1)
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
            mxsHost = optarg;
            break;

        case 'r':
            rowLimit = atoi(optarg);
            break;

        case 't':
            timeOut = atoi(optarg);
            break;

        case 'P':
            mxsPort = atoi(optarg);
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

        case 's':
            stateFileDir = optarg;
            break;

        case 'v':
            std::cout << VERSION << " " << GIT_COMMIT << endl;
            exit(0);
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
    Driver* driver = nullptr;

    try
    {
        driver = config.empty() ? new mcsapi::ColumnStoreDriver() : new mcsapi::ColumnStoreDriver(config);
        // Here is where make connection to MaxScale to receive CDC
        CDC::Connection cdcConnection(mxsHost, mxsPort, mxsUser, mxsPassword, timeOut);

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
