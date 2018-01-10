#pragma once
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

#include "common.h"

#include <memory>

#include <libmcsapi/mcsapi.h>

typedef std::unique_ptr<mcsapi::ColumnStoreBulkInsert> BulkInsert;

class CSProducer
{
public:
    CSProducer(const Options& options);
    ~CSProducer();

    // The function takes ownership of the values passed to this function
    void write(ValueList& values);

private:
    const Options&            m_options;
    mcsapi::ColumnStoreDriver m_driver;

    void processAvroType(BulkInsert& insert, avro_value_t* value);
};
