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

#include <chrono>
#include <string>

#include "constants.h"

typedef std::chrono::steady_clock Clock;
typedef Clock::time_point TimePoint;
typedef std::chrono::seconds Seconds;

struct Config
{
    // MaxScale host
    std::string host = "127.0.0.1";

    // MaxScale CDC port
    int port = 4001;

    // MaxScale CDC username
    std::string user = "admin";

    // MaxScale CDC password
    std::string password = "mariadb";

    // State file directory
    std::string statedir = DEFAULT_STATE_DIR;

    // Row limit per batch
    int rowlimit = 1;

    // Connection timeout
    int timeout = 10;

    // Include table metadata
    bool metadata = true;

    // How long to wait before flushing batch when idle
    Seconds flush_interval = Seconds(5);

    // Path to Columnstore.xml
    std::string columnstore_xml = "/usr/local/mariadb/columnstore/etc/Columnstore.xml";

    // Automatically create tables on ColumnStore
    bool auto_create = false;

    // Process command line options into a configuration
    static Config process(int argc, char** argv);
};
