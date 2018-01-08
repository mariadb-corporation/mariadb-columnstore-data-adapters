#include <map>
#include <iostream>
#include <cstring>
#include <signal.h>
#include <execinfo.h>
#include <unistd.h>

#include "common.h"

using std::endl;
using std::cout;

static bool running = true;
static Logger logger;

Logger& getLogger()
{
    return logger;
}

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