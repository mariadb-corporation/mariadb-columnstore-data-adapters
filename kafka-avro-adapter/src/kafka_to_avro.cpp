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

void usage()
{
    logger() << "Usage: " << program_name << " [OPTIONS]" << endl
             << endl
             << "  -c CONFIG    Path to configuration file" << endl
             << endl;
}

int main(int argc, char* argv[])
{
    program_name = basename(argv[0]);
    configureSignals();
    std::string config = "config.json";

    char c;
    while ((c = getopt(argc, argv, "c:")) != -1)
    {
        switch (c)
        {

        case 'c':
            config = optarg;
            break;

            default:
            usage();
            exit(1);
            break;
        }
    }

    try
    {
        auto options = Options::open(config);
        std::vector<std::unique_ptr<Controller>> controllers;

        for (const auto& opt: options)
        {
            controllers.emplace_back(new Controller(opt));
            controllers.back()->start();
        }

        while (isRunning())
        {
            sleep(1);
        }

        for (auto& ctrl: controllers)
        {
            ctrl->stop();
        }
    }
    catch (std::runtime_error e)
    {
        logger() << e.what() << std::endl;
    }

    return 0;
}
