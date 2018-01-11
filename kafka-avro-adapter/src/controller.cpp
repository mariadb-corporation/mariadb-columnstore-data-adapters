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

#include "controller.h"
#include <cassert>
#include <deque>

Controller::Controller(const Options& options):
    m_running(false),
    m_options(options),
    m_consumer(options),
    m_producer(options)
{
}

Controller::~Controller()
{
}

bool Controller::should_flush()
{
    return m_queue.size() >= m_options.max_rows ||
        Clock::now() - m_last_flush > m_options.max_time;
}

void Controller::run()
{
    while (m_running.load())
    {
        try
        {
            Result r = m_consumer.read();
            if (r.first)
            {
                m_queue.push_back(r.second);
            }

            if (m_queue.size() > 0 && should_flush())
            {
                m_producer.write(m_queue);
                m_last_flush = Clock::now();
            }
        }
        catch (AdapterError e)
        {
           logger() << "Adapter Exception: " << e.what() << std::endl;
        }
        catch (mcsapi::ColumnStoreError e)
        {
           logger() << "ColumnStore Exception: " << e.what() << std::endl;
        }
    }
}

bool Controller::start()
{
    assert(!m_running.load());
    m_running.store(true);
    m_thr = std::thread(&Controller::run, this);
    return true;
}

void Controller::stop()
{
    if (m_running.load())
    {
        m_running.store(false);
        m_thr.join();
    }
}
