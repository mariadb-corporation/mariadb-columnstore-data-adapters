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

#include "utils.hh"

#include <errno.h>
#include <string.h>
#include <signal.h>
#include <execinfo.h>
#include <unistd.h>

#include <map>

using std::endl;

Logger logger;

bool setSignal(int sig, void (*f)(int))
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

static void fatalHandler(int sig)
{
    void* addrs[128];
    logger() << "Received fatal signal " << sig << endl;
    int count = backtrace(addrs, sizeof(addrs) / sizeof(addrs[0]));
    backtrace_symbols_fd(addrs, count, STDOUT_FILENO);
    setSignal(sig, SIG_DFL);
    raise(sig);
}

void configureSignalHandlers(void (*term)(int))
{
    std::map<int, void(*)(int)> signals =
    {
        std::make_pair(SIGTERM, term),
        std::make_pair(SIGINT, term),
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
