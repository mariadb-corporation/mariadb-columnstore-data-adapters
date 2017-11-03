/*
 * Copyright (c) 2017 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2020-01-01
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

#include <sys/time.h>

#pragma once

#if DEBUG
#define mcsdebug(MSG, ...) do { \
    struct timeval tv; \
    time_t nowtime; \
    struct tm *nowtm; \
    char tmpdbuf[64], dbuf[64]; \
    gettimeofday(&tv, NULL); \
    nowtime = tv.tv_sec; \
    nowtm = localtime(&nowtime); \
    strftime(tmpdbuf, sizeof tmpdbuf, "%H:%M:%S", nowtm); \
    snprintf(dbuf, sizeof dbuf, "%s.%06ld", tmpdbuf, tv.tv_usec); \
    fprintf(stderr, "[mcskafka][%s] %s:%d " MSG "\n", dbuf,  __FILENAME__, __LINE__, ##__VA_ARGS__); \
} while(0)
#else
#define mcsdebug(MSG, ...)
#endif
