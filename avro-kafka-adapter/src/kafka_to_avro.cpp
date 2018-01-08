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

#include <iostream>
#include <fstream>
#include <string>
#include <sstream>
#include <cstring>
#include <cctype>
#include <map>
#include <signal.h>
#include <execinfo.h>
#include <unistd.h>

#include <librdkafka/rdkafkacpp.h>

#include "common.h"
#include "http_request.h"

using std::endl;
static Logger logger;

static std::string program_name;

// A "magic" value used to detect a valid Avro-serialized value
#define KAFKA_AVRO_MAGIC   0x0

// The size of the ID for the Avro schema (stored in schema-registry: https://github.com/confluentinc/schema-registry)
#define KAFKA_AVRO_ID_SIZE 4

// Extracts the network order schema ID
#define get_avro_id(p) ((uint32_t)(p[0] << 24 | p[1] << 16 | p[2] << 8 | p[3]))

static struct
{
    std::string database;
    std::string table;
    std::string broker;
    std::string group;
    std::string logfile;
    std::string registry;
} options;

void usage()
{
    logger() << "Usage: " << program_name << " [OPTION]... DATABASE TABLE" << endl
             << endl
             << "  DATABASE       Target database" << endl
             << "  TABLE          Table to stream" << endl
             << endl
             << "  -b BROKER    Broker address in HOST:PORT format (default: 127.0.0.1:9092)" << endl
             << "  -g GROUP     Consumer group (default: 1)" << endl
             << "  -l FILE      Log output to filename given as argument" << endl
             << "  -d REGISTRY  Schema-registry address in HOST:PORT format (default: 127.0.0.1:8081)" << endl
             << endl;
}

// The ID to Avro schema mappings provided by the schema-registry
static std::map<uint32_t, std::string> schema_registry;

void processMessage(RdKafka::Message* msg)
{
    uint8_t* ptr = (uint8_t*)msg->payload();
    uint8_t* end = ptr + msg->len();
    uint32_t id = 0;

    if (ptr + KAFKA_AVRO_ID_SIZE < end && *ptr == KAFKA_AVRO_MAGIC)
    {
        ptr++;
        id = get_avro_id(ptr);
        ptr += KAFKA_AVRO_ID_SIZE;
    }

    if (schema_registry.count(id) == 0)
    {
        std::stringstream url;
        url << options.registry << "/schemas/ids/" << id;
        Request* r = Request::execute(url.str());

        if (r)
        {
            schema_registry[id] = r->result();
        }
        else
        {
           schema_registry[id] = "{\"error\": \"Could not perform request to schema-registry\"}";
        }
    }

    printf("Schema %u: %s\n", id, schema_registry[id].c_str());

    while (ptr < end)
    {
        printf("%02hhx ", *ptr);
        ptr++;
    }

    printf(":");
    ptr = (uint8_t*)msg->payload();

    while (ptr < end)
    {
        printf("%c", isprint(*ptr) ? *ptr : '.');
        ptr++;
    }

    printf("\n");
}

int main(int argc, char* argv[])
{
    options.broker = "127.0.0.1:9092";
    options.group = "1";
    options.registry = "127.0.0.1:8081";

    program_name = basename(argv[0]);
    configureSignals();

    char c;
    while ((c = getopt(argc, argv, "l:b:g:")) != -1)
    {
        switch (c)
        {
        case 'l':
            options.logfile = optarg;
            if (!logger.open(optarg))
            {
                logger() << "Failed to open logfile:" << optarg << endl;
                exit(1);
            }
            break;

        case 'b':
            options.broker = optarg;
            break;

        case 'g':
            options.group = optarg;
            break;

        default:
            usage();
            exit(1);
            break;
        }
    }

    if (argc - optind != 2)
    {
        // Missing arguments
        usage();
        exit(1);
    }

    options.table = argv[optind + 1];
    options.database = argv[optind];

    std::stringstream ss;
    ss << options.database << "." << options.table;
    std::string topic = ss.str();

    RdKafka::Conf* conf = RdKafka::Conf::create(RdKafka::Conf::CONF_GLOBAL);
    std::string errstr;
    conf->set("metadata.broker.list", options.broker, errstr);
    conf->set("group.id", options.group, errstr);

    RdKafka::KafkaConsumer* consumer = RdKafka::KafkaConsumer::create(conf, errstr);
    if (!consumer) {
        std::cerr << "Failed to create consumer: " << errstr << std::endl;
        exit(1);
    }
    std::cout << "Created consumer: " << consumer->name() << std::endl;

    RdKafka::ErrorCode err = consumer->subscribe({topic});
    if (err) {
        std::cerr << "Failed to subscribe to  topic: " << RdKafka::err2str(err) << std::endl;
        exit(1);
    }
    std::cout << "Subscribed to: " << topic << std::endl;

    RdKafka::Message* msg;

    while (isRunning() && (msg = consumer->consume(1000)))
    {
        switch (msg->err())
        {
        case RdKafka::ERR__TIMED_OUT:
        case RdKafka::ERR__PARTITION_EOF:
            break;

        case RdKafka::ERR_NO_ERROR:
            // TODO: process message as Avro
            processMessage(msg);
            break;

        default:
            std::cerr << "Error: " << msg->errstr()  << std::endl;
            break;
        }

        delete msg;
    }

    consumer->close();
    delete consumer;

    RdKafka::wait_destroyed(5000);
    return 0;
}
