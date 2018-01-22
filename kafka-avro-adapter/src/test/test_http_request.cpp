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

#include <iostream>

#include "../http_request.h"

int main(int argc, char** argv)
{
    int rval = 0;

    if (argc > 1)
    {
        Request* r = Request::execute(argv[1]);

        if (r)
        {
            std::cout << r->result() << std::endl;
        }
        else
        {
            rval = -1;
        }
    }

    return rval;
}
