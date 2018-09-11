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

/**
 * Log a message
 *
 * @param format Format string in printf style
 * @param ...    Variadic parameter
 */
void log(const char* format, ...) __attribute ((format(printf, 1, 2)));

/**
 * Set where the log writes
 *
 * @param file File where the logger writes
 *
 * @return True if opening file was successful
 */
bool set_logfile(std::string file);

/**
 * Set thread-local log identifier
 *
 * @param id Id to use
 */
void set_thread_id(std::string id);

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
