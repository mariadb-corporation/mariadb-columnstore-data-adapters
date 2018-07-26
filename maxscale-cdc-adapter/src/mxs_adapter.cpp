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
#include <vector>

#include <libmcsapi/mcsapi.h>
#include <maxscale/cdc_connector.h>

#include "constants.h"
#include "context.hh"
#include "utils.hh"
#include "config.hh"

using std::endl;
using std::cout;

// The global configuration
static Config config;

static std::vector<UContext> contexts;

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

bool processTable(UContext& ctx)
{
    std::string identifier = ctx->database + "." + ctx->table;
    std::string gtid = readGtid(ctx); // GTID that was last processed in last run
    bool rv = true;

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

            while (running)
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

            if (running)
            {
                // We're stopping because of an error
                logger() << "Failed to read row: " << ctx->cdc.error() << endl;
            }
        }
        else
        {
            logger() << "MaxScale connection could not be created: " << ctx->cdc.error() << endl;
            rv = false;
        }
    }
    catch (mcsapi::ColumnStoreNotFound &e)
    {
        rv = false;

        // Try to read a row from the CDC connection
        if (ctx->cdc.read())
        {
            logger() << "Table not found, create with:" << endl << endl
                     << "    " << getCreateFromSchema(ctx) << endl
                     << endl;
        }
        else
        {
            logger() << "Failed to read row: " << ctx->cdc.error() << endl;
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

    try
    {
        contexts.emplace_back(new Context(config, table, database));

        if (!processTable(contexts.back()))
        {
            rval = 1;
        }
    }
    catch (mcsapi::ColumnStoreError &e)
    {
        logger() << "Caught ColumnStore error: " << e.what() << endl;
        rval = 1;
    }

    return rval;
}
