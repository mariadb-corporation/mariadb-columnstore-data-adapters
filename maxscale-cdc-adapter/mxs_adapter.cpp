/*
 * Copyright (c) 2016 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2020-01-01
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

#include <libmcsapi/mcsapi.h>
#include <cdc_connector.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <memory>
#include <limits.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <signal.h>

bool processTable(mcsapi::ColumnStoreDriver *driver, CDC::Connection * cdcConnection, std::string dbName,
                  std::string tblName);
int processRowRcvd(CDC::Row *row, mcsapi::ColumnStoreBulkInsert *bulk);
std::string readGTID(std::string DbName, std::string TblName);
int writeGTID(std::string DbName, std::string TblName, std::string gtID);

// Last processed GTID
static std::string lastGTID;

// Number of rows for each bulk insert
static int rowLimit = 1;

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
             << endl;
}

static bool setSignal(int sig, void (*f)(int))
{
    bool rval = true;
    struct sigaction sigact = {};
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
        logger() << "\nShutting down..." << endl;
        running = false;
    }
    else
    {
        logger() << "\nTerminating immediately" << endl;
        setSignal(sig, SIG_DFL);
        raise(sig);
    }
}

static void configureSignals()
{
    std::map<int, void(*)(int)> signals =
    {
        std::make_pair(SIGTERM, signalHandler),
        std::make_pair(SIGINT, signalHandler)
    };

    for (auto a : signals)
    {
        if (!setSignal(a.first, a.second))
        {
            exit(1);
        }
    }
}

int main(int argc, char *argv[])
{
    char c;
    std::ofstream logfile;
    std::string config;
    std::string mxsIPAddr = "127.0.0.1";
    int mxsCDCPort = 4001;
    std::string mxsUser = "admin";
    std::string mxsPassword  = "mariadb";
    strcpy(program_name, basename(argv[0]));
    configureSignals();

    while ((c = getopt(argc, argv, "l:h:P:p:u:c:r:")) != -1)
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

        case 'P':
            mxsCDCPort = atoi(optarg);
            break;

        case 'u':
            mxsUser = optarg;
            break;

        case 'p':
            mxsPassword = optarg;
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

    if (argc - optind < 2)
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
        // Here is where make connection to MaxScale to recieve CDC
        std::shared_ptr<CDC::Connection> cdcConnection(new CDC::Connection(mxsIPAddr, mxsCDCPort, mxsUser,
                                                                           mxsPassword));

        //  TODO: one thread per table, for now one table per process
        processTable(driver, cdcConnection.get(), mxsDbName, mxsTblName);
        // which table to insert into
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        logger() << "Error caught: " << e.what() << endl;
    }
    delete driver;
}

std::string getCreateFromSchema(std::string db, std::string tbl, CDC::ValueMap fields)
{
    std::stringstream ss;
    bool first = true;

    ss << "CREATE TABLE " << db << "." << tbl << " (";

    for (const auto& a : fields)
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

    ss << ") ENGINE=ColumnStore;";

    return ss.str();
}

bool processTable(mcsapi::ColumnStoreDriver* driver, CDC::Connection * cdcConnection, std::string dbName,
                  std::string tblName)
{
    // one bulk object per table
    std::shared_ptr<mcsapi::ColumnStoreBulkInsert> bulk;
    CDC::Row row;
    std::string tblReq = dbName + "." + tblName;
    std::string gtid = readGTID(dbName, tblName); //GTID that was last processed in last run
    bool rv = true;

    try
    {
        // read the data being sent from MaxScale CDC Port
        if (cdcConnection->createConnection() &&
            cdcConnection->requestData(tblReq, gtid))
        {

            bulk.reset(driver->createBulkInsert(dbName, tblName, 0, 0));

            if (!gtid.empty()) //skip the row for lastGTID, as it was processed in last run
            {
                cdcConnection->read();
            }
            row = cdcConnection->read();
            logger() << "Reading first row " << row << endl;
            int rowCount = 0;
            time_t init = time(NULL);
            while (running && row)
            {
                logger() << "Processing row " << endl;
                // Take each field and build the bulk->setColumn(<colnum>, columnValue);
                // Convert the binary row object received from MaxScale to mcsAPI bulk object.
                std::string currentGTID = lastGTID;

                if (processRowRcvd(&row, bulk.get()))
                {
                    if (lastGTID != currentGTID)
                    {
                        rowCount++;
                    }

                    if (difftime(time(NULL), init) >= 5.0 || rowCount >= rowLimit)
                    {
                        logger() << "COMMIT" << endl;
                        bulk->commit();
                        mcsapi::ColumnStoreSummary summary = bulk->getSummary();
                        logger() << summary.getRowsInsertedCount() << " inserted in " <<
                                 summary.getExecutionTime() << " seconds. lastGTID = " << lastGTID << endl;
                        writeGTID(dbName, tblName, lastGTID);
                        rowCount = 0;
                        init = time(NULL);
                        logger() << "Creating BulkInsert for " << dbName << " " << tblName << endl;
                        bulk.reset(driver->createBulkInsert(dbName, tblName, 0, 0));
                        logger() << "BulkInsert created" << endl;
                    }
                    // and read the next row
                    logger() << "Reading next row " << endl;
                    row = cdcConnection->read();
                }
                else
                {
                    logger() << "Empty Row Received" << endl;
                }
            }

            if (running)
            {
                // We're stopping because of an error
                logger() << "Failed to read row: " << cdcConnection->getError() << endl;
            }

        }
        else
        {
            logger() << "MaxScale connection could not be created: " << cdcConnection->getError() << endl;
        }
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        logger() << __func__ << ": " << e.what() << endl;

        if (strstr(e.what(), "Table not found"))
        {
            cdcConnection->read();
            logger() << "Table can be created with:" << endl
                     << getCreateFromSchema(dbName, tblName, cdcConnection->getFields()) << endl;
        }
        rv = false;
    }

    return rv;
}

// Process a row received from MaxScale CDC Connector and add it into ColumnStore Bulk object
int processRowRcvd(CDC::Row *row, mcsapi::ColumnStoreBulkInsert *bulk)
{
    CDC::Row& r = *row;
    size_t fc = r->fieldCount();
    logger() << "Enter processRowRcvd number of fields " << fc << endl;
    if ( fc <= 6)
    {
        return 0;
    }
    for ( size_t i = 0; i < fc; i++)
    {
        logger() << " adding column " <<  r->key(i) << " value " << r->value(i) << endl;
        // TBD how is null value provided by cdc connector API ? process accordingly
        // row.key[i] is the columnname and row.value(i) is value in string form for the ith column
        bulk->setColumn(i, r->value(i));
    }
    lastGTID = r->gtid();
    bulk->writeRow();
    logger() << "Exit processRow for GTID " << lastGTID << endl;
    return (int)fc;
}

std::string readGTID(std::string DbName, std::string TblName)
{
    std::ifstream  afile;
    std::string retVal = "";

    afile.open(DbName + "." + TblName);
    afile >> retVal;
    afile.close();
    return retVal;
}

int writeGTID(std::string DbName, std::string TblName, std::string gtID)
{
    std::ofstream  afile;
    afile.open(DbName + "." + TblName);

    afile << gtID << endl;
    afile.close();
    return 1;
}
