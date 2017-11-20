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

#include <cstdint>
#include <string>
#include <tr1/memory>
#include <vector>
#include <algorithm>
#include <jansson.h>

#pragma once

/** Request format flags */
#define CDC_REQUEST_TYPE_JSON (1 << 0)
#define CDC_REQUEST_TYPE_AVRO (1 << 1)

namespace CDC
{

// The typedef for the Row type
class InternalRow;
typedef std::tr1::shared_ptr<InternalRow> Row;

typedef std::vector<std::string> ValueList;

// A class that parses CDC data
class Parser
{
public:
    Parser(uint32_t flags = CDC_REQUEST_TYPE_JSON);
    virtual ~Parser();
    Row parse(const char* data, int len);
    const std::string& getSchema() const
    {
        return m_schema;
    }
    const std::string& getError() const
    {
        return m_error;
    }
    void addKey(const std::string& key)
    {
        m_keys.push_back(key);
    }

private:
    uint32_t m_flags;
    std::string m_error;
    std::string m_schema;
    ValueList m_keys;
    ValueList m_types;

    bool readRow(std::string& dest);
    void processSchema(json_t* json);
    Row processRow(json_t*);
};

// Internal representation of a row, used via the Row type
class InternalRow
{
public:

    //size_t fieldCount() const
    size_t fieldCount()
    {
        return m_values.size();
    }

    const std::string& value(size_t i) const
    {
        return m_values[i];
    }

    const std::string& value(const std::string& str) const
    {
        ValueList::const_iterator it = std::find(m_keys.begin(), m_keys.end(), str);
        return m_values[it - m_keys.begin()];
    }

    const std::string& key(size_t i) const
    {
        return m_keys[i];
    }

    const std::string& type(size_t i) const
    {
        return m_types[i];
    }

    const std::string& eventType() const
    {
        return event_type;
    }

    ~InternalRow()
    {
    }

private:
    ValueList m_keys;
    ValueList m_types;
    ValueList m_values;
    std::string event_type;

    // Not intended to be copied
    InternalRow(const InternalRow&);
    InternalRow& operator=(const InternalRow&);
    InternalRow();

    // Only a Connection should construct an InternalRow
    friend class Parser;

    InternalRow(const ValueList& keys,
                const ValueList& types,
                const ValueList& values,
                const std::string& event):
        m_keys(keys),
        m_types(types),
        m_values(values),
        event_type(event)
    {
    }

};

}
