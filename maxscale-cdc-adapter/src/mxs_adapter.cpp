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
#include <list>
#include <chrono>
#include <thread>
#include <mutex>

#include <libmcsapi/mcsapi.h>
#include <maxscale/cdc_connector.h>
#include <mysql/mysql.h>

// For parsing the Columnstore.xml file
#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/xml_parser.hpp>
namespace pt = boost::property_tree;

#include "constants.h"
#include "context.hh"
#include "utils.hh"
#include "config.hh"

using std::endl;
using std::cout;

// The global configuration
static Config config;

static std::mutex ctx_lock;
static std::list<UContext> contexts;

// Last processed GTID
static std::string lastGTID;

// Whether we have read any rows since the last flush
static bool haveRows = false;

// Set to a non-zero value when the process should stop
volatile static sig_atomic_t shutdown = 0;

// Handles terminate signals, used to stop the process
static void signalHandler(int sig)
{
    if (!shutdown)
    {
        const char msg[] = "\nShutting down...\n";
        write(STDOUT_FILENO, msg, sizeof(msg) - 1);
        shutdown = 1;
    }
    else
    {
        const char msg[] = "\nSlow shutdown detected, the next interrupt will "
                           "forcibly stop the program\n";
        write(STDOUT_FILENO, msg, sizeof(msg) - 1);
        setSignal(sig, SIG_DFL);
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

std::string readGtid(const UContext& ctx)
{
    std::string rval;
    std::string filename = config.statedir + "/" + ctx->database + "." + ctx->table;
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

void writeGtid(const UContext& ctx, std::string gtid)
{
    std::string filename = config.statedir + "/" + ctx->database + "." + ctx->table;
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

std::string getCreateFromSchema(const UContext& ctx)
{
    bool first = true;
    std::stringstream ss;
    ss << "CREATE TABLE " << ctx->database << "." << ctx->table << " (";

    for (auto&& a : ctx->cdc.fields())
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

// Opens a new bulk insert
void createBulk(UContext& ctx)
{
    ctx->bulk.reset(ctx->driver->createBulkInsert(ctx->database, ctx->table, 0, 0));
}

// Flushes a bulk insert batch
void flushBatch(UContext& ctx, int rowCount, bool reconnect)
{
    ctx->bulk->commit();
    writeGtid(ctx, lastGTID);

    mcsapi::ColumnStoreSummary summary = ctx->bulk->getSummary();

    logger()
        << summary.getRowsInsertedCount() << " rows, "
        << rowCount << " transactions inserted over "
        << summary.getExecutionTime() << " seconds. "
        << "GTID = " << lastGTID << endl;

    if (reconnect)
    {
        createBulk(ctx);
    }
    else
    {
        ctx->bulk.reset();
    }
}

bool CreateTable(UContext& ctx, std::string table_def)
{
    bool rval = true;
    pt::ptree tree;
    pt::read_xml(config.columnstore_xml, tree);
    std::string host = tree.get<std::string>("Columnstore.CrossEngineSupport.Host");
    int port = tree.get<int>("Columnstore.CrossEngineSupport.Port");
    std::string user = tree.get<std::string>("Columnstore.CrossEngineSupport.User");
    std::string password = tree.get<std::string>("Columnstore.CrossEngineSupport.Password");

    MYSQL* mysql = mysql_init(NULL);

    if (mysql_real_connect(mysql, host.c_str(), user.c_str(),
                           password.empty() ? NULL : password.c_str(),
                           NULL, port, NULL, 0) == NULL)
    {
        logger() << "Failed to connect to ColumnStore SQL interface: " << mysql_error(mysql) << endl;
        mysql_close(mysql);
        return false;
    }

    if (mysql_query(mysql, table_def.c_str()))
    {
        logger() << "Failed to Create table `" << ctx->database << "`.`" << ctx->table
            << "` on ColumnStore: " << mysql_error(mysql) << endl;
        rval = false;
    }

    mysql_close(mysql);

    return rval;
}

// Process a row received from MaxScale CDC Connector and add it into ColumnStore Bulk object
void processRowRcvd(UContext& ctx, CDC::SRow& row)
{
    TableInfo& info = ctx->driver->getSystemCatalog().getTable(ctx->database, ctx->table);
    assert(row->length() > 6);

    for (size_t i = 0; i < row->length(); i++)
    {
        if (config.metadata || !isMetadataField(row->key(i)))
        {
            // TBD how is null value provided by cdc connector API ? process accordingly
            // row.key[i] is the columnname and row.value(i) is value in string form for the ith column
            uint32_t pos = info.getColumn(row->key(i)).getPosition();
            ctx->bulk->setColumn(pos, row->value(i));
        }
    }

    lastGTID = row->gtid();
    ctx->bulk->writeRow();
}

enum process_result
{
    OK,
    ERROR,
    RETRY
};

process_result processTable(UContext& ctx)
{
    std::string identifier = ctx->database + "." + ctx->table;
    std::string gtid = readGtid(ctx); // GTID that was last processed in last run
    process_result rv = OK;

    try
    {
        // read the data being sent from MaxScale CDC Port
        if (ctx->cdc.connect(identifier, gtid))
        {
            //skip the row for lastGTID, as it was processed in last run
            if (!gtid.empty())
            {
                lastGTID = gtid;
                logger() << "Continuing from GTID " << gtid << endl;
                ctx->cdc.read();
            }

            int rowCount = 0;
            time_t init = time(NULL);

            while (!shutdown)
            {
                if (CDC::SRow row = ctx->cdc.read())
                {
                    // Start a bulk insert if we're not doing one already
                    if (!ctx->bulk)
                    {
                        createBulk(ctx);
                    }

                    // Take each field and build the bulk->setColumn(<colnum>, columnValue);
                    // Convert the binary row object received from MaxScale to mcsAPI bulk object.
                    std::string currentGTID = lastGTID;

                    processRowRcvd(ctx, row);

                    init = time(NULL);
                    haveRows = true;

                    if (!currentGTID.empty() && lastGTID != currentGTID)
                    {
                        rowCount++;
                    }

                    if (rowCount >= config.rowlimit)
                    {
                        flushBatch(ctx, rowCount, true);
                        rowCount = 0;
                        haveRows = false;
                    }
                }
                else
                {
                    if (ctx->cdc.error() == CDC::TIMEOUT)
                    {
                        if (difftime(time(NULL), init) >= config.flush_interval)
                        {
                            // We have been idle for too long. If a bulk insert is active and we
                            // have data to send, flush it to ColumnStore. If we have an open bulk
                            // insert but no data, simply close the bulk to release table locks.
                            if (haveRows)
                            {
                                flushBatch(ctx, rowCount, false);
                                rowCount = 0;
                                haveRows = false;
                                init = time(NULL);
                            }
                            else if (ctx->bulk)
                            {
                                // We've been idle for too long, close the connection
                                // to release locks
                                ctx->bulk.reset();
                            }
                        }
                    }
                    else
                    {
                        break;
                    }
                }
            }

            if (!shutdown)
            {
                // We're stopping because of an error
                logger() << "Failed to read row: " << ctx->cdc.error() << endl;
            }
        }
        else
        {
            logger() << "MaxScale connection could not be created: " << ctx->cdc.error() << endl;
            rv = ERROR;
        }
    }
    catch (mcsapi::ColumnStoreNotFound &e)
    {
        std::string schema = getCreateFromSchema(ctx);

        if (config.auto_create)
        {
            rv = CreateTable(ctx, schema) ? RETRY : ERROR;
        }
        else
        {
            logger() << "Table not found, create it manually on ColumnStore with:" << endl << endl
                << "    " << schema << endl << endl
                << "You use the -a option to have mxs_adapter create it automatically for you." << endl;
            rv = ERROR;
        }
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        logger() << "Caught ColumnStore error: " << e.what() << endl;
        rv = ERROR;
    }

    return rv;
}

void stop_on_signal()
{
    while (!shutdown)
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }

    ctx_lock.lock();

    for (auto&& a : contexts)
    {
        // This isn't very pretty but it is a safe way to quickly stop
        // the CDC connector from waiting for more events. It'll also cause
        // the streaming process to close at a known good position.
        a->cdc.close();
    }

    ctx_lock.unlock();
}

int main(int argc, char *argv[])
{
    configureSignalHandlers(signalHandler);

    // Start a thread that waits for a signal and stops the streams
    std::thread signal_watcher(stop_on_signal);

    config = Config::process(argc, argv);

    int rval = 0;
    std::string database = argv[optind];
    std::string table = argv[optind + 1];

    try
    {
        process_result rc;

        do
        {
            ctx_lock.lock();
            auto it = contexts.emplace(contexts.end(), new Context(config, table, database));
            ctx_lock.unlock();

            rc = processTable(*it);

            ctx_lock.lock();
            contexts.erase(it);
            ctx_lock.unlock();
        }
        while (rc == RETRY);

        if (rc == ERROR)
        {
            rval = 1;
        }
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        logger() << "Caught ColumnStore error: " << e.what() << endl;
        rval = 1;
    }

    shutdown = 1;
    signal_watcher.join();

    return rval;
}
