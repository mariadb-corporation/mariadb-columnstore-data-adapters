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

#include <memory>

#include <librdkafka/rdkafkacpp.h>

#include "common.h"

typedef std::unique_ptr<RdKafka::KafkaConsumer> KConsumer;
typedef std::unique_ptr<RdKafka::Message>       KMessage;
typedef std::pair<bool, avro_value_t>           Result;

class KafkaConsumer
{
public:
    KafkaConsumer(const Options& options);
    ~KafkaConsumer();
    Result read();

private:
    const Options& m_options;
    KConsumer      m_consumer;

    avro_value_t processMessage(const KMessage& msg);
    std::string getSchema(uint32_t id);
};
