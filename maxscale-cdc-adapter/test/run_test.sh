#!/bin/bash

docker exec -i test_tester_1 bash <<EOF |& tee test.log
cd /tests
python3 -m unittest discover
EOF
