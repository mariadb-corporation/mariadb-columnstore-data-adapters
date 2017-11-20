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

#include "cdc_parser.h"

#include <stdexcept>
#include <unistd.h>
#include <string.h>
#include <sstream>
#include <openssl/sha.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <jansson.h>
#include <iostream>

#define CDC_CONNECTOR_VERSION "1.0.0"

#define ERRBUF_SIZE 512
#define READBUF_SIZE 1024

static const char OK_RESPONSE[] = "OK\n";

static const char CLOSE_MSG[] = "CLOSE";
static const char REGISTER_MSG[] = "REGISTER UUID=CDC_CONNECTOR-" CDC_CONNECTOR_VERSION ", TYPE=";
static const char REQUEST_MSG[] = "REQUEST-DATA ";

namespace
{

static inline int nointr_read(int fd, void *dest, size_t size)
{
    int rc = read(fd, dest, size);

    while (rc == -1 && errno == EINTR)
    {
        rc = read(fd, dest, size);
    }

    return rc;
}

static inline int nointr_write(int fd, const void *src, size_t size)
{
    int rc = write(fd, src, size);

    while (rc == -1 && errno == EINTR)
    {
        rc = write(fd, src, size);
    }

    return rc;
}

std::string json_to_string(json_t* json)
{
    std::stringstream ss;

    switch (json_typeof(json))
    {
    case JSON_STRING:
        ss << json_string_value(json);
        break;

    case JSON_INTEGER:
        ss << json_integer_value(json);
        break;

    case JSON_REAL:
        ss << json_real_value(json);
        break;

    case JSON_TRUE:
        ss << "true";
        break;

    case JSON_FALSE:
        ss << "false";
        break;

    case JSON_NULL:
        break;

    default:
        break;

    }

    return ss.str();
}

}

namespace CDC
{

/**
 * Public functions
 */

Parser::Parser(uint32_t flags) :
    m_flags(flags)
{
}

Parser::~Parser()
{
}

static inline bool is_schema(json_t* json)
{
    bool rval = false;
    json_t* j = json_object_get(json, "fields");

    if (j && json_is_array(j) && json_array_size(j))
    {
        rval = json_object_get(json_array_get(j, 0), "name") != NULL;
    }

    return rval;
}

void Parser::processSchema(json_t* json)
{
    m_keys.clear();
    m_types.clear();

    json_t* arr = json_object_get(json, "fields");
    //char* raw = json_dumps(json, 0);
    size_t i;
    json_t* v;

    json_array_foreach(arr, i, v)
    {
        json_t* name = json_object_get(v, "name");
        json_t* type = json_object_get(v, "real_type");
        std::string nameval = name ? json_string_value(name) : "";
        std::string typeval = type ? json_string_value(type) : "undefined";
        m_keys.push_back(nameval);
        m_types.push_back(typeval);
    }
}

Row Parser::processRow(json_t* js)
{
    ValueList values;
    m_error.clear();

    for (ValueList::iterator it = m_keys.begin();
         it != m_keys.end(); it++)
    {
        json_t* v = json_object_get(js, it->c_str());

        if (v)
        {
            values.push_back(json_to_string(v));
        }
        else
        {
            m_error = "No value for key found: ";
            m_error += *it;
            break;
        }
    }

    json_t* v = json_object_get(js, "event_type");
    std::string event_type = json_to_string(v);

    Row rval;

    if (m_error.empty())
    {
        rval = Row(new InternalRow(m_keys, m_types, values, event_type));
        std::cout << "Returning " << rval << std::endl;
    }
    else
       std::cout << m_error << std::endl;

    return rval;
}

Row Parser::parse(const char* data, int len)
{
    Row rval;

    json_error_t err;
    json_t* js = json_loadb(data, len, JSON_ALLOW_NUL, &err);

    if (js)
    {
        if (is_schema(js))
        {
            processSchema(js);
            // TODO: have a flag in rval to say we got a schema
            std::cout << "Schema received in cdc connection " << rval << std::endl;
        }
        else
        {
            rval = processRow(js);
            std::cout << "Row received in cdc connection " << rval << std::endl;
        }

        json_decref(js);
    }
    else
    {
        m_error = "Failed to parse JSON: ";
        m_error += err.text;
        std::cout << m_error << std::endl;
    }

    return rval;
}

}
