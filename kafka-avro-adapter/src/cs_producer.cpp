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

#include "cs_producer.h"

#include <cassert>

#include "common.h"

void CSProducer::processAvroType(BulkInsert& insert, avro_value_t* value)
{
    auto table = m_driver.getSystemCatalog().getTable(m_options.database, m_options.table);
    processAvroType(insert, value, table);
    insert->writeRow();
}

void CSProducer::processAvroType(BulkInsert& insert,
    avro_value_t* value,
    mcsapi::ColumnStoreSystemCatalogTable& table)
{
    // This won't work for nested UNIONs or UNIONS with base types
    if (avro_value_get_type(value) == AVRO_RECORD)
    {
        size_t n_fields = 0;
        avro_value_get_size(value, &n_fields);

        for (size_t i = 0; i < n_fields; i++)
        {
            avro_value_t field;
            avro_value_get_by_index(value, i, &field, NULL);
            const char* name = avro_schema_record_field_name(avro_value_get_schema(value), i);
            //logger() << "record field name: " << name << std::endl;
            int table_idx = table.getColumn(name).getPosition();

            if ( avro_value_get_type(&field) == AVRO_UNION )
            {
                //logger() << "union " << name << std::endl;
                int disc;
                avro_value_get_discriminant(&field, &disc);
                avro_value_t union_value;
                avro_value_set_branch(&field, disc, &union_value);
                
                avro_type_t union_type = avro_value_get_type(&union_value);
                
                // Use type not disc value
                if ( union_type !=  AVRO_NULL )
                {
                    if( union_type == AVRO_RECORD )
                    {
                        //logger() << "union.record " << name << std::endl;
                        char* json_string;
                        processAvroType(insert, &union_value, table);
                        // Put the raw JSON for a RECORD just in case
                        avro_value_to_json(&union_value, 1, &json_string);
                        std::string jsonString = std::string(json_string);
                        insert->setColumn(table_idx, jsonString);
                        free(json_string);
                    }
                    else
                    {
                        //logger() << "union.non-record " << name << std::endl;
                        processAvroNonRec(insert, &union_value, table, 
                            table_idx);
                    }
                }
                else
                {
                    //logger() << "union.null " << name << std::endl;
                    insert->setNull(table_idx);
                }
            }
            else if ( avro_value_get_type(&field) == AVRO_RECORD )
            {
                //logger() << "record " << name << std::endl;
                char* json_string;
                processAvroType(insert, &field, table);
                // Put the raw JSON for a RECORD just in case
                avro_value_to_json(&field, 1, &json_string);
                std::string jsonString = std::string(json_string);
                insert->setColumn(table_idx, jsonString);
                free(json_string);
            }
            else
            {
                //logger() << "other " << name << std::endl;
                processAvroNonRec(insert, &field, table, table_idx);
            }
        }
    }
}

void CSProducer::processAvroNonRec(BulkInsert& insert, 
    avro_value_t* field, 
    mcsapi::ColumnStoreSystemCatalogTable& table,
    int table_idx )
{
    switch (avro_value_get_type(field))
    {
        case AVRO_STRING:
        {
            const char* s;
            size_t len;
            int r = avro_value_get_string(field, &s, &len);
            assert(r == 0);
            assert(len > 0);
            std::string str = std::string(s, len - 1);
            insert->setColumn(table_idx, str);
            break;
        }
        case AVRO_BYTES:
        {
            const void* s;
            size_t len;
            int r = avro_value_get_bytes(field, &s, &len);
            assert(r == 0);
            insert->setColumn(table_idx, std::string((char*)s, len));

            break;
        }
        case AVRO_INT32:
        {
            int32_t out;
            int r = avro_value_get_int(field, &out);
            assert(r == 0);
            insert->setColumn(table_idx, out);
            break;
        }
        case AVRO_INT64:
        {
            int64_t out;
            int r = avro_value_get_long(field, &out);
            assert(r == 0);
            insert->setColumn(table_idx, out);
            break;
        }
        case AVRO_FLOAT:
        {
            float out;
            int r = avro_value_get_float(field, &out);
            assert(r == 0);
            insert->setColumn(table_idx, out);
            break;
        }
        case AVRO_DOUBLE:
        {
            double out;
            int r = avro_value_get_double(field, &out);
            assert(r == 0);
            insert->setColumn(table_idx, out);
            break;
        }
        case AVRO_BOOLEAN:
        {
            int out = 0;
            int r = avro_value_get_boolean(field, &out);
            assert(r == 0);
            insert->setColumn(table_idx, (bool)out);
            break;
        }
        case AVRO_NULL:
        {
            insert->setNull(table_idx);
            break;
        }
        case AVRO_ENUM:
        {
            int out = 0;
            int r = avro_value_get_enum(field, &out);
            assert(r == 0);
            insert->setColumn(table_idx, out);
            break;
        }
        case AVRO_FIXED:
        {
            const void* s;
            size_t len;
            int r = avro_value_get_fixed(field, &s, &len);
            assert(r == 0);
            insert->setColumn(table_idx, std::string((char*)s, len));
            break;
        }
        case AVRO_MAP:
        {
            logger() << "Unsupported type: MAP" << std::endl;
            break;
        }
        case AVRO_ARRAY:
        {
            logger() << "Unsupported type: ARRAY" << std::endl;
            break;
        }
        case AVRO_LINK:
        {
            logger() << "Unsupported type: LINK" << std::endl;
            break;
        }

        default:
            logger() << "Unsupported type: " << avro_value_get_type(field) << std::endl;
            assert(false);
            break;
    }
}

CSProducer::CSProducer(const Options& options):
    m_options(options),
    m_driver(options.config)
{
}

CSProducer::~CSProducer()
{
}

void CSProducer::write(std::vector<avro_value_t>& values)
{
    BulkInsert insert(m_driver.createBulkInsert(m_options.database, m_options.table, 0, 0));
    insert->setBatchSize(1);

    for (auto& a: values)
    {
        processAvroType(insert, &a);
        avro_value_decref(&a);
    }

    values.clear();
    insert->commit();
}
