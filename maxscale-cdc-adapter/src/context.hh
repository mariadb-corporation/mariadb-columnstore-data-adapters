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
#include <maxscale/cdc_connector.h>

#include "config.hh"

typedef std::unique_ptr<mcsapi::ColumnStoreDriver> Driver;
typedef std::unique_ptr<mcsapi::ColumnStoreBulkInsert> Bulk;
typedef mcsapi::ColumnStoreSystemCatalogTable TableInfo;

struct Context
{
    std::string table;    // Table that is being streamed
    std::string database; // Database where the table is located
    Driver driver;        // ColumnStore Bulk API driver
    Bulk bulk;            // Bulk insert currently in progress (can be empty)
    CDC::Connection cdc;  // MaxScale CDC connection

    Context(const Config& config, std::string tbl, std::string db):
        table(tbl),
        database(db),
        driver(config.columnstore_xml.empty() ? new mcsapi::ColumnStoreDriver() :
               new mcsapi::ColumnStoreDriver(config.columnstore_xml)),
        cdc(config.host, config.port, config.user, config.password, config.timeout)
    {
    }

    ~Context()
    {
        if (bulk)
        {
            bulk->rollback();
        }
    }
};
