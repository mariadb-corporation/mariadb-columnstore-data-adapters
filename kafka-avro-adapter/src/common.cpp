/*
 * Copyright (c) 2018 MariaDB Corporation Ab
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

#include <map>
#include <iostream>
#include <sstream>
#include <cstring>
#include <signal.h>
#include <execinfo.h>
#include <unistd.h>
#include <jansson.h>

#include "common.h"

using std::endl;
using std::cout;

static bool running = true;
Logger logger;

static bool setSignal(int sig, void (*f)(int))
{
    bool rval = true;
    struct sigaction sigact;
    memset(&sigact, 0, sizeof(sigact));
    sigact.sa_handler = f;

    int err;

    do
    {
        errno = 0;
        err = sigaction(sig, &sigact, NULL);
    }
    while (errno == EINTR);

    if (err < 0)
    {
        logger() << "Failed to set signal: " << strerror(errno) << endl;
        rval = false;
    }

    return rval;
}

static void signalHandler(int sig)
{
    if (running)
    {
        logger() << "\nShutting down..." << endl;
        running = false;
    }
    else
    {
        logger() << "\nTerminating immediately" << endl;
        setSignal(sig, SIG_DFL);
        raise(sig);
    }
}

static void fatalHandler(int sig)
{
    void* addrs[128];
    logger() << "Received fatal signal " << sig << endl;
    int count = backtrace(addrs, sizeof(addrs) / sizeof(addrs[0]));
    backtrace_symbols_fd(addrs, count, STDOUT_FILENO);
    setSignal(sig, SIG_DFL);
    raise(sig);
}

void configureSignals()
{
    std::map<int, void(*)(int)> signals =
    {
        std::make_pair(SIGTERM, signalHandler),
        std::make_pair(SIGINT, signalHandler),
        std::make_pair(SIGSEGV, fatalHandler),
        std::make_pair(SIGABRT, fatalHandler),
        std::make_pair(SIGFPE, fatalHandler)
    };

    for (auto a : signals)
    {
        if (!setSignal(a.first, a.second))
        {
            exit(1);
        }
    }
}

bool isRunning()
{
    return running;
}

template <class T> bool is_type(json_t* json)
{
    return false;
}

template <> bool is_type<uint32_t>(json_t* json)
{
    return json_is_integer(json);
}

template <> bool is_type<std::string>(json_t* json)
{
    return json_is_string(json);
}

template <> bool is_type<Seconds>(json_t* json)
{
    return json_is_integer(json);
}

template <class T> T from_type(json_t* json)
{
    return T();
}

template <> uint32_t from_type<uint32_t>(json_t* json)
{
    return json_integer_value(json);
}

template <> std::string from_type<std::string>(json_t* json)
{
    return json_string_value(json);
}

template <> Seconds from_type<Seconds>(json_t* json)
{
    return Seconds(json_integer_value(json));
}

template <class T> void from_json(json_t* json, std::string name, T& t)
{
    json_t* val = json_object_get(json, name.c_str());

    if (val)
    {
        if (is_type<T>(val))
        {
            t = from_type<T>(val);
        }
        else
        {
            throw AdapterError("Invalid JSON type for `" + name + "`");
        }
    }
}

Options::Options(std::string arg_topic, std::string arg_database, std::string arg_table, json_t* opts):
    topic(arg_topic),
    database(arg_database),
    table(arg_table)
{
    from_json(opts, "broker", broker);
    from_json(opts, "group", group);
    from_json(opts, "logfile", logfile);
    from_json(opts, "registry", registry);
    from_json(opts, "timeout", timeout);
    from_json(opts, "config", config);
    from_json(opts, "max_rows", max_rows);
    from_json(opts, "max_time", max_time);
}

template <> void Closer<json_t*>::close(json_t* t)
{
    json_decref(t);
}

#define THROW_IF(stmt, err) do{if ((stmt)){throw AdapterError(err);}}while(false)

std::vector<Options> Options::open(std::string filename)
{
    json_error_t err;
    json_t* json = json_load_file(filename.c_str(), 0, &err);
    Closer<json_t*> c(json);
    std::vector<Options> rval;

    if (json)
    {
        json_t* streams = json_object_get(json, "streams");
        json_t* opts = json_object_get(json, "options");
        THROW_IF(!json_is_object(json), "Root level object is not a JSON object");
        THROW_IF(!json_is_array(streams), "`streams` is not a JSON array");
        THROW_IF(!json_is_object(opts), "`options` is not a JSON object");

        int idx;
        json_t* val;
        json_array_foreach(streams, idx, val)
        {
            const char* topic = json_string_value(json_object_get(val, "topic"));
            const char* table = json_string_value(json_object_get(val, "table"));
            const char* database = json_string_value(json_object_get(val, "database"));
            THROW_IF(!topic, "Invalid `topic` value in stream definition");
            THROW_IF(!table, "Invalid `table` value in stream definition");
            THROW_IF(!database, "Invalid `database` value in stream definition");
            rval.emplace_back(topic, database, table, opts);
        }
    }
    else
    {
        std::stringstream ss;
        ss << "Failed to process configuration (at line " << err.line
            << ", column " << err.column <<"): " << err.text;
        throw AdapterError(ss.str());
    }

    return rval;
}
