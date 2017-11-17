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
#include <string>
#include <csignal>
#include <argp.h>
#include <libmcsapi/mcsapi.h>
#include "cdc_consumer.h"
#include "cdc_parser.h"
#include "debug.h"

static bool run = true;

static void sigterm (int sig)
{
    (void) sig;
    run = false;
}

const char* argp_program_version = "mcskafka 1.1.2";
const char* argp_program_bug_address = "<https://jira.mariadb.org/browse/MCOL>";
static char doc[] = "mcskafka - A Kafka consumer to write to MariaDB ColumnStore";
static char args_doc[] = "BROKER TOPIC SCHEMA TABLE";

struct argp_option options[] =
{
    {"group", 'g', "GROUP_ID", 0, "The Kafka group ID (default 1)", 0},
    { 0, 0, 0, 0, 0, 0 }
};

struct arguments
{
    char* broker;
    char* topic;
    char* schema;
    char* table;
    char* group;
};

static error_t parse_opt(int key, char *arg, struct argp_state *state)
{
    struct arguments* arguments = (struct arguments*)state->input;

    switch (key)
    {
        case 'g':
            arguments->group = arg;
            break;
        case ARGP_KEY_ARG:
            if (state->arg_num >= 4)
            {
                argp_usage(state);
                return ARGP_KEY_ERROR;
            }
            switch (state->arg_num)
            {
                case 0:
                    arguments->broker = arg;
                    break;
                case 1:
                    arguments->topic = arg;
                    break;
                case 2:
                    arguments->schema = arg;
                    break;
                case 3:
                    arguments->table = arg;
                    break;
                default:
                    break;
            }
            break;
        case ARGP_KEY_END:
            if (state->arg_num < 4)
            {
                argp_usage(state);
                return ARGP_KEY_ERROR;
            }
            break;
        default:
            return ARGP_ERR_UNKNOWN;
    }
    return 0;
}

static struct argp argp = { options, parse_opt, args_doc, doc, nullptr, nullptr, nullptr };

int main(int argc, char* argv[])
{
    struct arguments args;
    args.group = nullptr;

    argp_parse(&argp, argc, argv, 0, 0, &args);
    std::string brokers = args.broker;
    std::string errstr;
    std::vector<std::string> topics;

    topics.push_back(args.topic);

    RdKafka::Conf *conf = RdKafka::Conf::create(RdKafka::Conf::CONF_GLOBAL);
    CDC::CDCRebalanceCb cdc_rebalance_cb;
    conf->set("rebalance_cb", &cdc_rebalance_cb, errstr);
    conf->set("metadata.broker.list", brokers, errstr);
    if (args.group)
    {
        conf->set("group.id", args.group, errstr);
    }
    else
    {
        conf->set("group.id", "1", errstr);
    }

    mcsapi::ColumnStoreDriver* driver = new mcsapi::ColumnStoreDriver();
    CDC::CDCConsumerCb* cdc_consume_cb = new CDC::CDCConsumerCb(driver, args.schema, args.table);

//    conf->set("consume_cb", cdc_consume_cb, errstr);

    RdKafka::KafkaConsumer *consumer = RdKafka::KafkaConsumer::create(conf, errstr);
    if (!consumer) {
        std::cerr << "Failed to create consumer: " << errstr << std::endl;
        exit(1);
    }
    std::cout << "% Created consumer " << consumer->name() << std::endl;

    RdKafka::ErrorCode err = consumer->subscribe(topics);
    if (err) {
        std::cerr << "Failed to subscribe to " << topics.size() << " topics: "
                  << RdKafka::err2str(err) << std::endl;
        exit(1);
    }
    signal(SIGINT, sigterm);
    signal(SIGTERM, sigterm);

    while (!cdc_consume_cb->hasFinished()) {
        if (!run)
        {
            cdc_consume_cb->setTerm();
        }
        RdKafka::Message *msg = consumer->consume(1000);
        cdc_consume_cb->consume_cb(*msg, NULL);
        mcsdebug("CB done");
        delete msg;
    }

    consumer->close();
    delete driver;
    delete cdc_consume_cb;
    delete consumer;

    RdKafka::wait_destroyed(5000);

}
