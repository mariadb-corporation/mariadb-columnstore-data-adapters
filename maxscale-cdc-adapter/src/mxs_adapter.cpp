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

#include <errno.h>
#include <signal.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <cassert>
#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include <unordered_set>

#include <libmcsapi/mcsapi.h>
#include <maxscale/cdc_connector.h>

#include "constants.h"
#include "utils.hh"
#include "config.hh"

typedef mcsapi::ColumnStoreDriver Driver;
typedef std::unique_ptr<mcsapi::ColumnStoreBulkInsert> Bulk;
typedef mcsapi::ColumnStoreSystemCatalogTable TableInfo;

using std::endl;
using std::cout;

// The global configuration
static Config config;

// Last processed GTID
static std::string lastGTID;

// Whether we have read any rows since the last flush
static bool haveRows = false;

// Set to false when the process should stop
static bool running = true;

// Handles terminate signals, used to stop the process
static void signalHandler(int sig)
{
    if (running)
    {
        logger() << "\nShutting down in " << config.timeout << " seconds..." << endl;
        running = false;
    }
    else
    {
        logger() << "\nTerminating immediately" << endl;
        setSignal(sig, SIG_DFL);
        raise(sig);
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
    std::string filename = config.statedir + "/" + db + "." + table;
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
    std::string filename = config.statedir + "/" + db + "." + table;
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
        if (config.metadata || !isMetadataField(a.first))
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
        if (config.metadata || !isMetadataField(row->key(i)))
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

                    if (rowCount >= config.rowlimit)
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
                        if (difftime(time(NULL), init) >= config.flush_interval)
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
    configureSignalHandlers(signalHandler);
    config = Config::process(argc, argv);

    int rval = 0;
    std::string database = argv[optind];
    std::string table = argv[optind + 1];
    Driver* driver = nullptr;

    try
    {
        driver = config.columnstore_xml.empty() ? new mcsapi::ColumnStoreDriver() : new mcsapi::ColumnStoreDriver(config.columnstore_xml);
        // Here is where make connection to MaxScale to receive CDC
        CDC::Connection cdcConnection(config.host, config.port, config.user, config.password, config.timeout);

        //  TODO: one thread per table, for now one table per process
        if (!processTable(driver, &cdcConnection, database, table))
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
