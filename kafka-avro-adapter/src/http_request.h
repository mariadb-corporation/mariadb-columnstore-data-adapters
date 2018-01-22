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

#include <map>
#include <string>
#include <utility>

#include <curl/curl.h>

class Request
{
    Request(const Request&)  = delete;
    Request& operator=(const Request&) = delete;

public:
    ~Request();

    /**
     * Execute a HTTP request
     *
     * The value returned by this function must be freed with `delete`
     *
     * @return Pointer to the executed Request or nullptr on error
     */
    static Request* execute(const std::string url, const std::string method = "GET",
                            const std::map<std::string, std::string> headers = {});

    /**
     * Get the HTTP status code
     */
    int statusCode() const;

    /**
     * Get the result of the operation as a string
     */
    const std::string& result() const;

private:
    Request(const std::string url, const std::string method,
            const std::map<std::string, std::string> headers);
    bool doRequest();

    static size_t write_callback(char *ptr, size_t size, size_t nmemb, void *userdata);

    std::string m_url;
    std::string m_method;
    std::map<std::string, std::string> m_headers;
    CURL* m_handle;
    std::string m_buffer;
};

const char* getHttpError();
