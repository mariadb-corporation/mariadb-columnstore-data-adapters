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

#include <atomic>
#include <thread>
#include <avro.h>

#include "common.h"
#include "kafka_consumer.h"
#include "cs_producer.h"

typedef std::vector<avro_value_t>  List;

class Controller
{
public:

    Controller(const Options& options);
    ~Controller();

    bool start();
    void stop();

private:
    std::atomic<bool> m_running;
    Options           m_options;
    List              m_queue;
    std::thread       m_thr;
    KafkaConsumer     m_consumer;
    CSProducer        m_producer;
    TimePoint         m_last_flush;

    void run();
    bool should_flush();
};
