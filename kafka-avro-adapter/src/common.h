#pragma once
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

#include <chrono>
#include <exception>
#include <fstream>
#include <functional>
#include <iostream>
#include <string>
#include <vector>

#include <avro.h>
#include <jansson.h>

// The default clock and time
typedef std::chrono::steady_clock Clock;
typedef Clock::time_point         TimePoint;
typedef std::chrono::seconds      Seconds;
typedef std::vector<avro_value_t> ValueList;

// A generic error
// TODO: Subclass this to distinct error types
class AdapterError: public std::runtime_error
{
public:
    AdapterError(const std::string& arg):
    runtime_error(arg){}
};

// A simple class that calls a function with one parameter when it is destroyed
template <class T> class Closer
{
public:
    Closer(T t):
        m_t(t)
    {
    }

    ~Closer()
    {
        close(m_t);
    }

    void close(T t)
    {        
    }

    private:
        T                      m_t;
};

// Program options
struct Options
{
    std::string topic;
    std::string database;
    std::string table;
    std::string broker = "127.0.0.1:9092";
    std::string group = "1";
    std::string logfile;
    std::string registry = "127.0.0.1:8081";
    uint32_t    timeout = 10000;
    std::string config = "Columnstore.xml";
    uint32_t    max_rows = 1000;
    Seconds     max_time = Seconds(5);

    /**
     * Default options
     */
    Options(std::string topic, std::string database, std::string table, json_t* opts);

    /**
     * Create options from a JSON file
     *
     * The file must define a single JSON object that defines both the `options`
     * and `streams` fields.
     *
     * The `options` field must be a JSON object that defines the options and
     * their new values. Any options not defined are configured with their default
     * values.
     *
     * The `streams` field must be an array of objects with each object defining
     * the `topic`, `database` and `table` fields. The `topic` field defines the topic
     * that is streamed to the ColumnStore table defined by both the `database`
     * and `table` fields.
     *
     * The following is an example configuration file that defines one custom
     * option and one stream.
     *
     * @verbatim
     * {
     *   "options" : {
     *     "broker" : "192.168.0.100:9092"
     *   },
     *   "streams" : [
     *     {
     *       "topic" : "my-topic",
     *       "database" : "test",
     *       "table" : "t1"
     *     }
     *   ]
     * }
     * @endverbatim
     *
     * @param filename File to process
     *
     * @return List of options representing the streams
     */
    static std::vector<Options> open(std::string filename);
};

// Minimalistic logger, prints to stdout by default
class Logger
{
public:

    Logger():
        m_ref(&std::cout)
    {
    }

    std::ostream& operator()()
    {
        return *m_ref;
    }

    bool open(std::string file)
    {
        m_logfile.open(file);
        return m_logfile.good();
    }

private:
    std::ostream* m_ref;
    std::ofstream m_logfile;
};

// The global logger instance
extern Logger logger;

/**
 * Configure signal handlers
 */
void configureSignals();

/**
 * Check if the program is still running
 *
 * @return True if program is still running
 */
bool isRunning();
