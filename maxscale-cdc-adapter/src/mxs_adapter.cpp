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

#include <atomic>
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
#include <mysql.h>

// For parsing the Columnstore.xml file
#include <boost/property_tree/ptree.hpp>
#include <boost/property_tree/xml_parser.hpp>
namespace pt = boost::property_tree;

#include "constants.h"
#include "context.hh"
#include "utils.hh"
#include "config.hh"
#include "gtid.hh"

using std::endl;
using std::cout;

// The global configuration
static Config config;

// List of thread contexts
static std::list<UContext> contexts;
static std::mutex ctx_lock;

// Set to a non-zero value when the process should stop
volatile static sig_atomic_t shutdown = 0;

// Handles terminate signals, used to stop the process
static void signalHandler(int sig)
{
    if (!shutdown)
    {
        const char msg[] = "\nShutting down...\n";
        int __attribute((unused)) rc = write(STDOUT_FILENO, msg, sizeof(msg) - 1);
        shutdown = 1;
    }
    else
    {
        const char msg[] = "\nSlow shutdown detected, the next interrupt will "
                           "forcibly stop the program\n";
        int __attribute((unused)) rc = write(STDOUT_FILENO, msg, sizeof(msg) - 1);
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

GTID readGtid(const UContext& ctx)
{
    GTID rval;
    std::string filename = config.statedir + "/" + ctx->database + "." + ctx->table;
    std::ifstream f(filename);

    if (f)
    {
        std::string str;
        std::getline(f, str);
        rval = GTID::from_string(str);

        if (rval.event_number == 0)
        {
            // Old GTID state file, add missing data
            rval.event_number = 1;
        }
    }
    else if (errno != ENOENT)
    {
        log("Failed to open state file '%s' for reading: %d, %s",
            filename.c_str(), errno, strerror(errno));
    }

    return rval;
}

void writeGtid(const UContext& ctx)
{
    std::string filename = config.statedir + "/" + ctx->database + "." + ctx->table;
    std::ofstream f(filename);

    if (f)
    {
        f << ctx->gtid.to_string() << endl;
    }
    else
    {
        log("Failed to open state file '%s' for writing: %d, %s",
            filename.c_str(), errno, strerror(errno));
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
void flushBatch(UContext& ctx, bool reconnect)
{
    ctx->bulk->commit();
    writeGtid(ctx);

    mcsapi::ColumnStoreSummary summary = ctx->bulk->getSummary();

    std::stringstream ss;
    ss << summary.getRowsInsertedCount() << " rows, "
        << ctx->trx << " transactions inserted over "
        << summary.getExecutionTime() << " seconds. "
        << "GTID = " << ctx->gtid.to_string() << endl;
    log("%s", ss.str().c_str());

    if (reconnect)
    {
        createBulk(ctx);
    }
    else
    {
        ctx->bulk.reset();
    }

    ctx->rows = 0;
    ctx->trx = 0;
    ctx->lastFlush = Clock::now();
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
        log("Failed to connect to ColumnStore SQL interface: %s", mysql_error(mysql));
        mysql_close(mysql);
        return false;
    }

    if (mysql_query(mysql, table_def.c_str()))
    {
        log("Failed to Create table `%s`.`%s` on ColumnStore: %s",
            ctx->database.c_str(), ctx->table.c_str(), mysql_error(mysql));
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

    GTID new_gtid = GTID::from_row(row);

    if (new_gtid.sequence != ctx->gtid.sequence && ctx->rows > 0)
    {
        ctx->trx++;
    }

    ctx->gtid = std::move(new_gtid);
    ctx->bulk->writeRow();
}

void processRow(UContext& ctx, CDC::SRow& row)
{
    // Start a bulk insert if we're not doing one already
    if (!ctx->bulk)
    {
        createBulk(ctx);
    }

    processRowRcvd(ctx, row);

    if (++ctx->rows >= config.rowlimit ||
        Clock::now() - ctx->lastFlush >= config.flush_interval)
    {
        flushBatch(ctx, true);
    }
}

bool continueFromGtid(UContext& ctx, GTID& gtid)
{
    bool rval = true;
    log("Continuing from GTID: %s", gtid.to_string().c_str());

    while (!shutdown)
    {
        if (CDC::SRow row = ctx->cdc.read())
        {
            GTID current = GTID::from_row(row);

            if (current < gtid)
            {
                // Both should point to the same transaction
                assert(current.to_triplet() == gtid.to_triplet());
            }
            else
            {
                ctx->gtid = gtid;

                if (gtid < current)
                {
                    log("Warning: Couldn't finish previous transaction %s before "
                        "reading a newer transaction %s. Continuing processing.",
                        gtid.to_string().c_str(), current.to_string().c_str());
                    processRow(ctx, row);
                }
                break;
            }
        }
        else if (ctx->cdc.error() != CDC::TIMEOUT)
        {
            rval = false;
            break;
        }
    }

    return rval;
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
    GTID gtid = readGtid(ctx); // GTID that was last processed in last run
    process_result rv = OK;

    try
    {
        // read the data being sent from MaxScale CDC Port
        if (ctx->cdc.connect(identifier, gtid.to_triplet()))
        {
            // Skip rows we have already read
            if (gtid)
            {
                continueFromGtid(ctx, gtid);
            }

            while (!shutdown)
            {
                if (CDC::SRow row = ctx->cdc.read())
                {
                    processRow(ctx, row);
                }
                else
                {
                    if (ctx->cdc.error() == CDC::TIMEOUT)
                    {
                        // We have been idle for too long. If a bulk insert is active and we
                        // have data to send, flush it to ColumnStore. If we have an open bulk
                        // insert but no data, simply close the bulk to release table locks.
                        if (ctx->rows)
                        {
                            flushBatch(ctx, false);
                        }
                        else if (ctx->bulk)
                        {
                            // We've been idle for too long, close the connection
                            // to release locks
                            ctx->bulk.reset();
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
                log("Failed to read row: %s", ctx->cdc.error().c_str());
            }
        }
        else
        {
            log("MaxScale connection could not be created: %s", ctx->cdc.error().c_str());
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
            log("Table not found, create it manually on ColumnStore with:\n\n\t%s\n\n"
                "You can use the -a option to have mxs_adapter automatically create it for you.",
                schema.c_str());
            rv = ERROR;
        }
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        log("Caught ColumnStore error: %s", e.what());
        rv = ERROR;
    }

    return rv;
}

void wait_for_signal()
{
    while (!shutdown)
    {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

static std::atomic<int> errors{0};

void streamTable(std::string database, std::string table)
{
    set_thread_id(database + "." + table);

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
            errors++;
        }
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        log("Caught ColumnStore error: %s", e.what());
        errors++;
    }
}

int main(int argc, char *argv[])
{
    set_thread_id("main");
    configureSignalHandlers(signalHandler);
    config = Config::process(argc, argv);

    if (access(config.columnstore_xml.c_str(), R_OK) == -1)
    {
        log("Could not access Columnstore.xml at '%s': %d, %s",
            config.columnstore_xml.c_str(), errno, strerror(errno));
        return 1;
    }

    std::list<std::thread> threads;

    for (auto&& a : config.input)
    {
        threads.emplace_back(streamTable, a.first, a.second);
    }

    // Wait until a terminate signal is received
    wait_for_signal();

    ctx_lock.lock();

    for (auto&& a : contexts)
    {
        // This isn't very pretty but it is a safe way to quickly stop
        // the CDC connector from waiting for more events. It'll also cause
        // the streaming process to close at a known good position.
        a->cdc.close();
    }

    ctx_lock.unlock();

    for (auto&& a : threads)
    {
        a.join();
    }

    return errors;
}
