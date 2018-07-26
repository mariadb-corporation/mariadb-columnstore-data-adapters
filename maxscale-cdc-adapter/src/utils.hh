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

#include <iostream>
#include <fstream>

// Minimal logger
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

extern Logger logger;

/**
 * Set a signal handler
 *
 * @param sig Signal to handle
 * @param f   Handler for the signal
 *
 * @return True if signal handler was successfully set
 */
bool setSignal(int sig, void (*f)(int));

/**
 * Set terminate signal handler and configure fatal signal handlers
 *
 * This function must be called as early as possible after program startup.
 *
 * @param term  Called when the process should terminate
 */
void configureSignalHandlers(void (*term)(int));
