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

#pragma once

#include <memory>

#include <libmcsapi/mcsapi.h>
#include "cdc_connector.h"
#include <mysql.h>

#include "config.hh"
#include "gtid.hh"

namespace std
{

template<>
struct default_delete<MYSQL>
{
    void operator()(MYSQL* mysql)
    {
        mysql_close(mysql);
    }
};

}

typedef std::unique_ptr<mcsapi::ColumnStoreDriver> Driver;
typedef std::unique_ptr<mcsapi::ColumnStoreBulkInsert> Bulk;
typedef mcsapi::ColumnStoreSystemCatalogTable TableInfo;
typedef std::unique_ptr<MYSQL> UMYSQL;

struct Context
{
    std::string table;    // Table that is being streamed
    std::string database; // Database where the table is located
    Driver driver;        // ColumnStore Bulk API driver
    Bulk bulk;            // Bulk insert currently in progress (can be empty)
    CDC::Connection cdc;  // MaxScale CDC connection
    int rows = 0;         // Number of processed rows for current batch
    int trx = 0;          // Number of processed transactions for current batch
    TimePoint lastFlush;  // When the last batch was flushed
    GTID gtid;            // The GTID of the last transaction that was processed
    UMYSQL mysql;         // CS database connection

    // Temporary storage used when data transformation is enabled
    CDC::SRow update_before;

    Context(const Config& config, std::string tbl, std::string db):
        table(tbl),
        database(db),
        driver(new mcsapi::ColumnStoreDriver(config.columnstore_xml)),
        cdc(config.host, config.port, config.user, config.password, config.timeout),
        lastFlush(Clock::now())
    {
    }

    ~Context()
    {
        if (bulk)
        {
            try
            {
                bulk->rollback();
            }
            catch (...)
            {
                // Ignore exceptions, there's not much we can do at this point
            }
        }
    }
};

typedef std::unique_ptr<Context> UContext;
