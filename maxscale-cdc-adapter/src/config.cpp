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

#include "config.hh"

#include <getopt.h>
#include <string.h>
#include <sstream>

#include "utils.hh"
#include "constants.h"

using std::endl;

void usage()
{
    Config config; // A default constructed Config has only defaults

    std::stringstream ss;

    ss << "Usage: mxs_adapter [OPTION]... DATABASE TABLE" << endl
             << endl
             << "  DATABASE       Target database" << endl
             << "  TABLE          Table to stream" << endl
             << endl
             << "  -f FILE      TSV file with database and table names to stream (must be in `database TAB table NEWLINE` format)" << endl
             << "  -h HOST      MaxScale host (default: " << config.host << ")" << endl
             << "  -P PORT      Port number where the CDC service listens (default: " << config.port << ")" << endl
             << "  -u USER      Username for the MaxScale CDC service (default: " << config.user << ")" << endl
             << "  -p PASSWORD  Password of the user (default: " << config.password << ")" << endl
             << "  -c CONFIG    Path to the Columnstore.xml file (default: '" << config.columnstore_xml << "')" << endl
             << "  -a           Automatically create tables on ColumnStore" << endl
             << "  -s           Directory used to store the state files (default: '" << config.statedir << "')" << endl
             << "  -r ROWS      Number of events to group for one bulk load (default: " << config.rowlimit << ")" << endl
             << "  -t TIME      Connection timeout (default: 10)" << endl
             << "  -n           Disable metadata generation (timestamp, GTID, event type)" << endl
             << "  -i TIME      Flush data every TIME seconds (default: " << config.flush_interval.count() << ")" << endl
             << "  -l FILE      Log output to FILE instead of stdout" << endl
             << "  -v           Print version and exit" << endl
             << endl;

    log("%s", ss.str().c_str());
}

Config Config::process(int argc, char** argv)
{
    Config config;
    char c;

    while ((c = getopt(argc, argv, "af:l:h:P:p:u:c:r:t:i:s:nv")) != -1)
    {
        switch (c)
        {
        case 'a':
            config.auto_create = true;
            break;

        case 'l':
            if (!set_logfile(optarg))
            {
                log("Failed to open logfile '%s': %d, %s", optarg, errno, strerror(errno));
                exit(1);
            }
            break;

        case 'h':
            config.host = optarg;
            break;

        case 'r':
            config.rowlimit = atoi(optarg);
            break;

        case 't':
            config.timeout = atoi(optarg);
            break;

        case 'P':
            config.port = atoi(optarg);
            break;

        case 'u':
            config.user = optarg;
            break;

        case 'p':
            config.password = optarg;
            break;

        case 'n':
            config.metadata = false;
            break;

        case 'i':
            config.flush_interval = Seconds(atoi(optarg));
            break;

        case 'c':
            config.columnstore_xml = optarg;
            break;

        case 's':
            config.statedir = optarg;
            break;

        case 'v':
            std::cout << VERSION << " " << GIT_COMMIT << endl;
            exit(0);
            break;

        case 'f':
            config.input_file = optarg;
            break;

        default:
            usage();
            exit(1);
            break;
        }
    }

    if (!config.input_file.empty())
    {
        std::ifstream f(config.input_file);
        std::string line;

        while (std::getline(f, line))
        {
            char* db = strtok(&line[0], "\t\n");
            char* tbl = strtok(nullptr, "\t\n");

            if (db && db[0] && tbl && tbl[0])
            {
                config.input.emplace_back(db, tbl);
            }
        }
    }
    else if (argc - optind != 2)
    {
        // Missing arguments
        usage();
        exit(1);
    }
    else
    {
        config.input.emplace_back(argv[optind], argv[optind + 1]);
    }

    return config;
}
