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

#include <new>
#include <iostream>
#include <assert.h>

#include "http_request.h"

/**
 * Public functions
 */

static char curl_error_buf[CURL_ERROR_SIZE + 1] = "";

Request* Request::execute(const std::string url, const std::string method,
                          const std::map<std::string, std::string> headers)
{    
    Request* r = NULL;

    try
    {
        r = new Request(url, method, headers);

        if (!r->doRequest())
        {
            delete r;
            r = nullptr;
        }
    }
    catch(...)
    {
    }

    return r;
}

Request::~Request()
{
    curl_easy_cleanup(m_handle);
}

int Request::statusCode() const
{
    long rc = 0;
    curl_easy_getinfo(m_handle, CURLINFO_RESPONSE_CODE, &rc);
    return rc;
}

const std::string& Request::result() const
{
    return m_buffer;
}

const char* getHttpError()
{
    return curl_error_buf;
}

/**
 * Private functions
 */

size_t Request::write_callback(char *ptr, size_t size, size_t nmemb, void *userdata)
{
    assert(userdata);
    Request* r = static_cast<Request*>(userdata);
    size_t n_used = 0;

    if (r && size > 0)
    {
        r->m_buffer.append(ptr, nmemb);
        n_used = nmemb;
    }

    return n_used;
}

bool Request::doRequest()
{
    //curl_easy_setopt(m_handle, CURLOPT_VERBOSE, 1);
    curl_easy_setopt(m_handle, CURLOPT_WRITEFUNCTION, Request::write_callback);
    curl_easy_setopt(m_handle, CURLOPT_WRITEDATA, (void*)this);
    curl_easy_setopt(m_handle, CURLOPT_ERRORBUFFER, curl_error_buf);
    curl_easy_setopt(m_handle, CURLOPT_URL, m_url.c_str());

    return curl_easy_perform(m_handle) == CURLE_OK;
}

Request::Request(const std::string url, const std::string method,
                 const std::map<std::string, std::string> headers):
    m_url(url),
    m_method(method),
    m_headers(headers),
    m_handle(curl_easy_init())
{
    if (!m_handle)
    {
        throw std::bad_alloc();
    }
}
