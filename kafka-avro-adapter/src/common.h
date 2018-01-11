#pragma once
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

#include <chrono>
#include <exception>
#include <fstream>
#include <functional>
#include <iostream>
#include <string>
#include <vector>

#include <avro.h>

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
    std::string database;
    std::string table;
    std::string broker;
    std::string group;
    std::string logfile;
    std::string registry;
    uint32_t    timeout;
    std::string config;
    uint32_t    max_rows;
    Seconds     max_time;

    Options():
        broker("127.0.0.1:9092"),
        group("1"),
        registry("127.0.0.1:8081"),
        timeout(10000),
        max_rows(1000),
        max_time(Seconds(5))
    {
    }
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
