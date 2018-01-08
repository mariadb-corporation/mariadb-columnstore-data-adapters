/*
 * Copyright (c) 2017 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2020-09-01
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

#include <iostream>
#include <fstream>
#include <string>
#include <sstream>
#include <cstring>
#include <map>
#include <signal.h>
#include <execinfo.h>
#include <unistd.h>

#include <librdkafka/rdkafkacpp.h>
#include <avro.h>

#include "common.h"

using std::endl;
static Logger& logger = getLogger();
static std::string program_name;

void usage()
{
    logger() << "Usage: " << program_name << " [OPTION]... DATABASE TABLE" << endl
             << endl
             << "  DATABASE       Target database" << endl
             << "  TABLE          Target table" << endl
             << endl
             << "  -c CONFIG    Path to the Columnstore.xml file (installed by MariaDB ColumnStore)" << endl
             << endl;
}

int main(int argc, char* argv[])
{
    program_name = basename(argv[0]);
    configureSignals();

    std::string config;
    char c;

    while ((c = getopt(argc, argv, "l:c:")) != -1)
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

        case 'c':
            config = optarg;
            break;

        default:
            usage();
            exit(1);
            break;
        }
    }

    if (argc - optind != 2)
    {
        // Missing arguments
        usage();
        exit(1);
    }

    char buf[1024];
    size_t nbytes;
    //avro_reader_t reader = avro_reader_memory(buf, sizeof(buf));

    while (isRunning() &&
           (nbytes = std::cin.read(buf, sizeof(buf)).gcount()) > 0)
    {
        std::cout.write(buf, nbytes);
    }

    return 0;
}
