#!/bin/bash

docker exec -i tester bash <<EOF |& tee test.log
cd /tests
python3 -m unittest discover
EOF
