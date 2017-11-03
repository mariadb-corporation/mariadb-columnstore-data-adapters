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

#include <iostream>
#include "cdc_consumer.h"
#include "cdc_parser.h"
#include "debug.h"

namespace CDC
{

CDCConsumerCb::CDCConsumerCb(mcsapi::ColumnStoreDriver* driver, const std::string& db_name, const std::string& tbl_name):
    cdc_parser(nullptr),
    mcsapi_bulk(nullptr),
    has_finished(false),
    terminate(false),
    table(tbl_name),
    schema(db_name),
    mcsapi_driver(driver)
{
    cdc_parser = new Parser();
    mcsapi::ColumnStoreSystemCatalog mcsapi_syscat = mcsapi_driver->getSystemCatalog();
    mcsapi_table = mcsapi_syscat.getTable(db_name, tbl_name);
    for (uint16_t column = 0; column < mcsapi_table.getColumnCount(); column++)
    {
        mcsapi::ColumnStoreSystemCatalogColumn col_data = mcsapi_table.getColumn(column);
        cdc_parser->addKey(col_data.getColumnName());
    }
}

CDCConsumerCb::~CDCConsumerCb()
{
    delete mcsapi_bulk;
    delete cdc_parser;
}

void CDCConsumerCb::consume_cb(RdKafka::Message &msg, void *opaque)
{
    (void) opaque;
    Row cdc_row;
    switch (msg.err())
    {
        case RdKafka::ERR__TIMED_OUT:
            if (terminate)
            {
                mcsdebug("Teminating");
                if (mcsapi_bulk)
                {
                    mcsapi_bulk->commit();
                }
                has_finished = true;
            }
            break;

        case RdKafka::ERR_NO_ERROR:
            cdc_row = cdc_parser->parse((char*)msg.payload(), msg.len());
            mcsdebug("Message payload %s", (char*)msg.payload());
            if (cdc_row->eventType() != "insert")
            {
                mcsdebug("Non-insert event: %s", cdc_row->eventType().c_str());
                break;
            }
            mcsdebug("Write row event");
            if (!mcsapi_bulk)
            {
                startNewInsert();
            }
            writeEvent(cdc_row);
            break;

        case RdKafka::ERR__PARTITION_EOF:
           // has_finished = true;
            if (mcsapi_bulk)
            {
                mcsapi_bulk->commit();
                delete mcsapi_bulk;
                mcsapi_bulk = nullptr;
            }
            mcsdebug("EOF event");
            break;

        case RdKafka::ERR__UNKNOWN_TOPIC:
        case RdKafka::ERR__UNKNOWN_PARTITION:
        default:
            mcsapi_bulk->rollback();
            has_finished = true;
            mcsdebug("Error event");
            throw std::runtime_error(msg.errstr());
            break;
    }
}

void CDCConsumerCb::startNewInsert()
{
    mcsapi_bulk = mcsapi_driver->createBulkInsert(schema, table, 0, 0);
}

void CDCConsumerCb::writeEvent(Row cdc_row)
{
    size_t field_count = cdc_row->fieldCount();
    if (field_count == 0)
    {
        return;
    }

    for (size_t field=0; field < field_count; field++)
    {
        mcsapi::ColumnStoreSystemCatalogColumn column = mcsapi_table.getColumn(cdc_row->key(field));
        mcsapi_bulk->setColumn(column.getPosition(), cdc_row->value(field));
    }
    mcsapi_bulk->writeRow();
}

void CDCRebalanceCb::rebalance_cb(RdKafka::KafkaConsumer *consumer,
        RdKafka::ErrorCode err,
        std::vector<RdKafka::TopicPartition*> &partitions)
{
    if (err == RdKafka::ERR__ASSIGN_PARTITIONS)
    {
        consumer->assign(partitions);
    }
    else
    {
        consumer->unassign();
    }

}

}
