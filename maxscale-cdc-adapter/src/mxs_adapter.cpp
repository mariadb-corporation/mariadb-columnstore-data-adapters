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
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cassert>
#include <cctype>
#include <chrono>
#include <cstring>
#include <ctime>
#include <fstream>
#include <iostream>
#include <iterator>
#include <list>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_set>

#include <libmcsapi/mcsapi.h>
#include "cdc_connector.h"
#include <mysql.h>
#include <mysqld_error.h>

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
using std::chrono::milliseconds;
using std::chrono::steady_clock;
using std::chrono::duration_cast;

// The global configuration
static Config config;

// List of thread contexts
static std::list<UContext> contexts;
static std::mutex ctx_lock;

// Set to a non-zero value when the process should stop
volatile static sig_atomic_t shutdown = 0;

// For debug messages
#define debug(format, ...) do {if (config.debug)log(format, ##__VA_ARGS__);}while(false)

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
            if (strcasecmp(a.second.c_str(), "serial") == 0)
            {
                // ColumnStore doesn't support SERIAL
                a.second = "BIGINT UNSIGNED NOT NULL";
            }
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
        << "GTID = " << ctx->gtid.to_string();
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

bool openConnection(UContext& ctx)
{
    if (ctx->mysql)
    {
        // Already connected
        return true;
    }

    pt::ptree tree;
    pt::read_xml(config.columnstore_xml, tree);
    std::string host = tree.get<std::string>("Columnstore.CrossEngineSupport.Host");
    int port = tree.get<int>("Columnstore.CrossEngineSupport.Port");
    std::string user = tree.get<std::string>("Columnstore.CrossEngineSupport.User");
    std::string password = tree.get<std::string>("Columnstore.CrossEngineSupport.Password");

    UMYSQL mysql(mysql_init(NULL));
    bool rval = true;

    if (mysql_real_connect(mysql.get(), host.c_str(), user.c_str(),
                           password.empty() ? NULL : password.c_str(),
                           NULL, port, NULL, 0) == NULL)
    {
        log("Failed to connect to ColumnStore SQL interface: %s", mysql_error(mysql.get()));
        rval = false;
    }
    else
    {
        ctx->mysql = std::move(mysql);
    }

    return rval;
}

bool createTable(UContext& ctx, std::string table_def)
{
    bool rval = false;

    if (openConnection(ctx))
    {
        if (mysql_query(ctx->mysql.get(), table_def.c_str()))
        {
            log("Failed to create table `%s`.`%s` on ColumnStore: %s. SQL: %s",
                ctx->database.c_str(), ctx->table.c_str(), mysql_error(ctx->mysql.get()),
                table_def.c_str());
        }
        else
        {
            rval = true;
        }
    }

    return rval;
}

void updateGtid(UContext& ctx, CDC::SRow& row)
{
    GTID new_gtid = GTID::from_row(row);

    if (new_gtid.sequence != ctx->gtid.sequence && ctx->rows > 0)
    {
        ctx->trx++;
        debug("GTID %s", new_gtid.to_triplet().c_str());
    }

    ctx->gtid = std::move(new_gtid);
}

bool isNumber(const std::string& orig_type)
{
    std::string type;
    std::transform(orig_type.begin(), orig_type.end(), std::back_inserter(type), [](unsigned char c){return std::toupper(c);});
    auto pos = type.find_first_of('(');

    if (pos != std::string::npos)
    {
        // Remove the size of the type
        type.erase(pos, std::string::npos);
    }

    std::unordered_set<std::string> numbers =
    {
        "TINYINT",
        "BOOLEAN",
        "SMALLINT",
        "MEDIUMINT",
        "INT",
        "INTEGER",
        "BIGINT",
        "DECIMAL",
        "DEC",
        "NUMERIC",
        "FIXED",
        "FLOAT",
        "DOUBLE",
        "DOUBLE PRECISION",
        "REAL"
    };

    return numbers.count(type);
}

std::string fieldNamesAndValues(CDC::SRow& row, std::string sep)
{
    std::stringstream ss;

    for (size_t i = 0; i < row->length(); i++)
    {
        if (!isMetadataField(row->key(i)))
        {
            if (!ss.str().empty())
            {
                ss << sep;
            }

            const char* quote = isNumber(row->type(i)) ? "" : "'";
            ss << "`" << row->key(i) << "` = " << quote << row->value(i) << quote;
        }
    }

    return ss.str();
}

std::string createDelete(UContext& ctx, CDC::SRow& row)
{
    std::stringstream ss;

    ss << "DELETE FROM `" << ctx->database << "`.`" << ctx->table << "` WHERE ";
    ss << fieldNamesAndValues(row, " AND ");
    ss << " LIMIT 1";
    return ss.str();
}

std::string getNames(CDC::SRow& row)
{
    std::stringstream ss;

    for (size_t i = 0; i < row->length(); i++)
    {
        if (!isMetadataField(row->key(i)))
        {
            if (!ss.str().empty())
            {
                ss << ",";
            }
            ss << "`" << row->key(i) << "`";
        }
    }

    return ss.str();
}

std::string getValues(CDC::SRow& row)
{
    std::stringstream ss;

    for (size_t i = 0; i < row->length(); i++)
    {
        if (!isMetadataField(row->key(i)))
        {
            if (!ss.str().empty())
            {
                ss << ",";
            }

            if (row->is_null(i))
            {
                ss << "NULL";
            }
            else
            {
                assert(!row->value(i).empty());
                const char* quote = isNumber(row->type(i)) ? "" : "'";
                ss << quote << row->value(i) << quote;
            }
        }
    }

    return ss.str();
}

std::string createInsert(UContext& ctx, CDC::SRow& row)
{
    std::stringstream ss;
    ss << "INSERT INTO `" << ctx->database << "`.`" << ctx->table<< "` "
       << "(" << getNames(row) << ") VALUES (" << getValues(row) << ")";
    return ss.str();
}

std::string createUpdate(UContext& ctx, CDC::SRow& before, CDC::SRow& after)
{
    std::stringstream ss;

    ss << "UPDATE `" << ctx->database << "`.`" << ctx->table << "` SET ";
    ss << fieldNamesAndValues(after, ", ");
    ss << " WHERE ";
    ss << fieldNamesAndValues(before, " AND ");
    ss << " LIMIT 1";
    return ss.str();
}

bool doQuery(UContext& ctx, const std::string& query)
{
    bool rval = true;

    if (mysql_query(ctx->mysql.get(), query.c_str()))
    {
        log("Failed to execute query '%s': %d, %s", query.c_str(),
            mysql_errno(ctx->mysql.get()), mysql_error(ctx->mysql.get()));
        rval = false;
    }

    return rval;
}

void transformRow(UContext& ctx, CDC::SRow& row)
{
    std::string type = row->value("event_type");
    std::string query;

    if (type == "delete")
    {
        query = createDelete(ctx, row);
    }
    else if (type == "insert")
    {
        query = createInsert(ctx, row);
    }
    else if (type == "update_before")
    {
        ctx->update_before = row;
    }
    else
    {
        assert(type == "update_after");
        assert(ctx->update_before);
        query = createUpdate(ctx, ctx->update_before, row);
        ctx->update_before.reset();
    }

    if (!query.empty() && openConnection(ctx))
    {
        thread_local auto dml_avg = milliseconds(0);
        auto start = steady_clock::now();

        if (doQuery(ctx, query) ||
            (mysql_errno(ctx->mysql.get()) == ER_NO_SUCH_TABLE &&
             createTable(ctx, getCreateFromSchema(ctx)) &&
             doQuery(ctx, query)))
        {
            debug("%s", query.c_str());
            updateGtid(ctx, row);
            writeGtid(ctx);
        }

        auto d = (steady_clock::now() - start) * 0.1;
        dml_avg *= 0.9;
        dml_avg += duration_cast<milliseconds>(d);
        debug("DML average: %ldms", dml_avg.count());
    }
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
            debug("%s: %s", row->key(i).c_str(), row->value(i).c_str());
        }
    }

    updateGtid(ctx, row);
    ctx->bulk->writeRow();
}

void processRow(UContext& ctx, CDC::SRow& row)
{
    // Uncomment once MCOL-1662 is fixed
    if (/*row->value("event_type") != "insert" &&*/ config.transform)
    {
        if (ctx->bulk)
        {
            flushBatch(ctx, false);
        }

        transformRow(ctx, row);
    }
    else
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
}

bool continueFromGtid(UContext& ctx, GTID& gtid)
{
    bool rval = true;
    int nrows = 0;
    log("Continuing from GTID: %s", gtid.to_string().c_str());

    while (!shutdown)
    {
        if (CDC::SRow row = ctx->cdc.read())
        {
            nrows++;
            GTID current = GTID::from_row(row);

            if (current < gtid)
            {
                // Both should point to the same transaction
                if (current.to_triplet() != gtid.to_triplet())
                {
                    // This assertion might not be correct: more details are needed if it is triggered
                    log("%s != %s", current.to_string().c_str(), gtid.to_string().c_str());
                }
                assert(current.to_triplet() == gtid.to_triplet());
            }
            else
            {
                if (row->value("event_type") == "update_before")
                {
                    // We still need the update_after event which will be the next one
                    continue;
                }

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

    debug("Skipped %d rows", nrows);
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
        debug("Requesting data for table: %s", identifier.c_str());

        // read the data being sent from MaxScale CDC Port
        if (ctx->cdc.connect(identifier, gtid ? gtid.to_triplet() : ""))
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
                            debug("Flushing batch");
                            flushBatch(ctx, false);
                        }
                        else if (ctx->bulk)
                        {
                            // We've been idle for too long, close the connection
                            // to release locks
                            debug("Read timeout with empty batch, closing it");
                            ctx->bulk.reset();
                        }
                        else
                        {
                            debug("Read timeout");
                        }
                    }
                    else
                    {
                        debug("Received error: %s", ctx->cdc.error().c_str());
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
            debug("Creating table: %s", schema.c_str());
            rv = createTable(ctx, schema) ? RETRY : ERROR;
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
    std::unique_lock<std::mutex> guard(ctx_lock);
    set_thread_id("main");
    configureSignalHandlers(signalHandler);
    config = Config::process(argc, argv);

    if (config.input.empty())
    {
        log("No valid table names were defined");
        return 1;
    }

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
        debug("Started thread %p", &threads.back());
    }

    debug("Started %lu threads", threads.size());
    guard.unlock();

    // Wait until a terminate signal is received
    wait_for_signal();

    debug("Signal received, stopping");

    guard.lock();

    for (auto&& a : contexts)
    {
        // This isn't very pretty but it is a safe way to quickly stop
        // the CDC connector from waiting for more events. It'll also cause
        // the streaming process to close at a known good position.
        a->cdc.close();
    }

    guard.unlock();

    for (auto&& a : threads)
    {
        debug("Joining thread %p", &a);
        a.join();
    }

    return errors;
}
