package com.example.app;

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.Properties;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class App {

    static final String SCHEMA_STRING = "{"
            + "\"namespace\": \"KafkaAvroGenerator\", "
            + "\"type\": \"record\", "
            + "\"name\": \"Record\", "
            + "\"fields\": ["
            + "{\"name\": \"user_id\", \"type\": \"int\"}, "
            + "{\"name\": \"data\", \"type\": \"string\"}"
            + "]}";

    public static void main(String[] args) {
        org.apache.log4j.BasicConfigurator.configure();

        // The Avro schema for the data
        Schema schema = new Schema.Parser().parse(SCHEMA_STRING);

        // Parameters for the Kafka Producer
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        p.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://127.0.0.1:8081");
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

        Producer<String, GenericRecord> producer = new KafkaProducer<>(p);

        for (int i = 0; i < 5; i++) {
            // Create a new record, fill it with data and send it with the producer to the 'hello.world' topic
            GenericRecord record = new GenericData.Record(schema);
            record.put("user_id", i);
            record.put("data", "hello world" + Integer.toString(i * i));
            producer.send(new ProducerRecord<>("hello.world", null, record));
        }

        producer.close();
    }
}
