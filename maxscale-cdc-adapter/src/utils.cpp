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
#include <stdarg.h>

#include <map>
#include <mutex>

using std::endl;
using Guard = std::lock_guard<std::mutex>;

// Minimal logger
class Logger
{
public:
    std::ostream& operator()()
    {
        return *m_ref;
    }

    bool open(std::string file)
    {
        m_logfile.open(file);

        if (m_logfile.good())
        {
            m_ref = &m_logfile;
        }

        return m_logfile.good();
    }

private:
    std::ostream* m_ref = &std::cout;
    std::ofstream m_logfile;
};

static Logger logger;
static std::mutex lock;
thread_local std::string thr_id;

void log(const char* format, ...)
{
    va_list valist;
    va_start(valist, format);
    int len = vsnprintf(NULL, 0, format, valist);
    va_end(valist);

    char buf[len + 1];
    va_start(valist, format);
    vsnprintf(buf, sizeof(buf), format, valist);
    va_end(valist);

    time_t now = time(NULL);
    char t[100];
    strftime(t, sizeof(t), "%Y-%m-%d %H:%M:%S", localtime(&now));

    Guard guard(lock);
    logger() << t << " [" << thr_id << "] " << buf << endl;
}

bool set_logfile(std::string file)
{
    return logger.open(file);
}

void set_thread_id(std::string id)
{
    thr_id = id;
}

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
        log("Failed to set signal: %d, %s", errno,  strerror(errno));
        rval = false;
    }

    return rval;
}

static void fatalHandler(int sig)
{
    void* addrs[128];
    log("Received fatal signal %d", sig);
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
