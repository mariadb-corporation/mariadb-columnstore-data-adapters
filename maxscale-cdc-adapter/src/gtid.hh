/*
 * Copyright (c) 2017 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2020-09-01
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

#pragma once

#include <maxscale/cdc_connector.h>

struct GTID
{
    uint32_t domain = 0;
    uint32_t server_id = 0;
    uint64_t sequence = 0;
    uint64_t event_number = 0;

    static GTID from_string(std::string str)
    {
        std::stringstream ss(str);
        GTID gtid;
        char sep;

        ss >> gtid.domain >> sep
            >> gtid.server_id >> sep
            >> gtid.sequence >> sep
            >> gtid.event_number;

        return gtid;
    }

    static GTID from_row(CDC::SRow row)
    {
        return from_string(row->gtid() + ":" + row->value("event_number"));
    }

    std::string to_triplet() const
    {
        return std::to_string(domain) + "-" + std::to_string(server_id) + "-" + std::to_string(sequence);
    }

    std::string to_string() const
    {
        return to_triplet() + ":" + std::to_string(event_number);
    }

    bool operator<(const GTID& gtid)
    {
        if (sequence == gtid.sequence)
        {
            return event_number < gtid.event_number;
        }
        return sequence < gtid.sequence;
    }

    bool operator==(const GTID& gtid)
    {
        return sequence == gtid.sequence && event_number == gtid.event_number;
    }

    operator bool()
    {
        return sequence != 0;
    }
};
