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

#include <libmcsapi/mcsapi.h>
#include <librdkafka/rdkafkacpp.h>
#include "cdc_parser.h"

#pragma once

namespace CDC
{

class CDCConsumerCb
{
public:
    CDCConsumerCb(mcsapi::ColumnStoreDriver* driver, const std::string& db_name, const std::string& tbl_name);
    ~CDCConsumerCb();
    void consume_cb(RdKafka::Message &msg, void *opaque);
    void writeEvent(Row cdc_row);
    void startNewInsert();
    bool hasFinished() { return has_finished; };
    void setTerm() { terminate = true; }
private:
    Parser* cdc_parser;
    mcsapi::ColumnStoreBulkInsert* mcsapi_bulk;
    mcsapi::ColumnStoreSystemCatalogTable mcsapi_table;
    bool has_finished;
    bool terminate;
    std::string table;
    std::string schema;
    mcsapi::ColumnStoreDriver* mcsapi_driver;
};

class CDCRebalanceCb : public RdKafka::RebalanceCb
{
public:
    void rebalance_cb (RdKafka::KafkaConsumer *consumer,
		     RdKafka::ErrorCode err,
             std::vector<RdKafka::TopicPartition*> &partitions);
};

}
