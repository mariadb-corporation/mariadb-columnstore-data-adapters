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

#include <iostream>
#include <fstream>

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

/**
 * Get the global logger instance
 *
 * @return Global Logger instance
 */
Logger& getLogger();

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
