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
#include <cstring>
#include <cctype>
#include <map>
#include <cassert>
#include <memory>
#include <signal.h>
#include <execinfo.h>
#include <unistd.h>

#include <libmcsapi/mcsapi.h>
#include <librdkafka/rdkafkacpp.h>
#include <avro.h>
#include <jansson.h>

#include "common.h"
#include "http_request.h"
#include "controller.h"

using std::endl;

static std::string program_name;

typedef std::shared_ptr<mcsapi::ColumnStoreDriver> CSDriver;

static Options options;

void usage()
{
    logger() << "Usage: " << program_name << " [OPTION]... DATABASE TABLE" << endl
             << endl
             << "  DATABASE       Target database" << endl
             << "  TABLE          Table to stream" << endl
             << endl
             << "  -b BROKER    Broker address in HOST:PORT format (" << options.broker << ")" << endl
             << "  -g GROUP     Consumer group (" << options.group << ")" << endl
             << "  -l FILE      Log output to filename given as argument" << endl
             << "  -d REGISTRY  Schema-registry address in HOST:PORT format (" << options.registry << ")" << endl
             << "  -t TIMEOUT   Kafka timeout in milliseconds (" << options.timeout << ")" << endl
             << "  -c CONFIG    Path to ColumnStore configuration file" << endl
             << "  -r ROWS      Flush bulk load after this many rows (" << options.max_rows << ")" << endl
             << "  -s SECONDS   Flush bulk load after this many seconds (" << options.max_time.count() << ")" << endl
             << endl;
}

int main(int argc, char* argv[])
{
    program_name = basename(argv[0]);
    configureSignals();

    char c;
    while ((c = getopt(argc, argv, "b:c:g:l:t:r:s:")) != -1)
    {
        switch (c)
        {
        case 'l':
            options.logfile = optarg;
            if (!logger.open(optarg))
            {
                logger() << "Failed to open logfile:" << optarg << endl;
                exit(1);
            }
            break;

        case 'b':
            options.broker = optarg;
            break;

        case 'g':
            options.group = optarg;
            break;

        case 't':
            options.timeout = atol(optarg);
            break;

        case 'c':
            options.config = optarg;
            break;

        case 'r':
            options.max_rows = atol(optarg);
            break;

        case 's':
            options.max_time = Seconds(atol(optarg));
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

    options.table = argv[optind + 1];
    options.database = argv[optind];

    try
    {
        Controller controller(options);
        controller.start();
        
        while (isRunning())
        {
            sleep(1);
        }
        
        controller.stop();
    }
    catch (std::runtime_error e)
    {
        logger() << e.what() << std::endl;
    }

    return 0;
}
