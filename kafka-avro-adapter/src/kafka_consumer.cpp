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

#include "kafka_consumer.h"

#include <map>
#include <sstream>

#include <avro.h>
#include <jansson.h>

#include "common.h"
#include "http_request.h"

// The ID to Avro schema mappings provided by the schema-registry
typedef std::map<uint32_t, std::string> SchemaRegistry;
static SchemaRegistry schema_registry;

// A "magic" value used to detect a valid Avro-serialized value
#define KAFKA_AVRO_MAGIC   0x0

// The size of the ID for the Avro schema (stored in schema-registry: https://github.com/confluentinc/schema-registry)
#define KAFKA_AVRO_ID_SIZE 4

// Extracts the network order schema ID
#define get_avro_id(p) ((uint32_t)(p[0] << 24 | p[1] << 16 | p[2] << 8 | p[3]))

// Closer specialization for avro_reader_t
template <> void Closer<avro_reader_t>::close(avro_reader_t t)
{
    avro_reader_free(t);
}

/**
 * Get the Avro schema from schema-registry that matches the given ID
 *
 * @param id ID to request
 *
 * @return The schema or empty string on error
 */
std::string KafkaConsumer::getSchema(uint32_t id)
{
    SchemaRegistry::iterator it = schema_registry.find(id);

    if (it == schema_registry.end())
    {
        std::stringstream url;
        url << m_options.registry << "/schemas/ids/" << id;
        Request* r = Request::execute(url.str());

        if (r)
        {
            json_error_t err;
            json_t* json = json_loads(r->result().c_str(), 0, &err);

            if (json)
            {
                json_t* json_schema = json_object_get(json, "schema");

                if (json_schema)
                {
                    auto ret = schema_registry.insert({id, json_string_value(json_schema)});

                    if (ret.second)
                    {
                        it = ret.first;
                    }
                }
                json_decref(json);
            }
        }
    }

    std::string rval;

    if (it == schema_registry.end())
    {
        std::stringstream ss;
        ss << "Unknown schema ID: " << id;
        throw AdapterError(ss.str());
    }
    else
    {
        rval = it->second;
    }

    return rval;
}

avro_value_t KafkaConsumer::processMessage(const KMessage& msg)
{
    uint8_t* ptr = (uint8_t*)msg->payload();
    uint8_t* end = ptr + msg->len();
    uint32_t id = 0;

    if (ptr + KAFKA_AVRO_ID_SIZE >= end || *ptr != KAFKA_AVRO_MAGIC)
    {
        throw AdapterError("Malformed Avro event data, message too small or bad Avro magic");
    }

    ptr++;
    id = get_avro_id(ptr);
    ptr += KAFKA_AVRO_ID_SIZE;

    std::string schema_text = getSchema(id);
    avro_schema_t avro_schema;
    int rc = avro_schema_from_json_length(schema_text.c_str(), schema_text.length(), &avro_schema);

    if (rc)
    {
        throw AdapterError(std::string("Failed to process schema: ") + strerror(rc));
    }

    avro_value_iface_t* iface = avro_generic_class_from_schema(avro_schema);
    avro_value_t value;
    avro_generic_value_new(iface, &value);
    avro_reader_t reader = avro_reader_memory((char*)ptr, end - ptr);
    Closer<avro_reader_t> c(reader);

    if (!reader || avro_value_read(reader, &value))
    {
        throw AdapterError("Failed to read Avro value.");
    }

    return value;
}

KafkaConsumer::KafkaConsumer(const Options& options):
    m_options(options)
{
    RdKafka::Conf* conf = RdKafka::Conf::create(RdKafka::Conf::CONF_GLOBAL);
    std::string errstr;
    conf->set("metadata.broker.list", m_options.broker, errstr);
    conf->set("group.id", m_options.group, errstr);
    m_consumer.reset(RdKafka::KafkaConsumer::create(conf, errstr));

    if (!m_consumer) {
        throw AdapterError("Failed to create consumer: " + errstr);
    }

    RdKafka::ErrorCode err = m_consumer->subscribe({m_options.database + "." + m_options.table});

    if (err) {
        throw AdapterError("Failed to subscribe to  topic: " + RdKafka::err2str(err));
    }
}

KafkaConsumer::~KafkaConsumer()
{   
    m_consumer->close();
    RdKafka::wait_destroyed(5000);
}

Result KafkaConsumer::read()
{
    KMessage msg(m_consumer->consume(m_options.timeout));
    Result rval = {false, {}};

    switch (msg->err())
    {
        case RdKafka::ERR__TIMED_OUT:
        case RdKafka::ERR__PARTITION_EOF:
            // TODO: Figure out what to do here
            break;

        case RdKafka::ERR_NO_ERROR:
            rval.second = processMessage(msg);
            rval.first = true;
            break;

        default:
            throw AdapterError(msg->errstr());
            break;
    }

    return rval;
}