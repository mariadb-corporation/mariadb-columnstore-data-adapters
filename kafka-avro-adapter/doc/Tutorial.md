# Kafka-Avro Adapter Tutorial

This is a short tutorial on how to create a Java application that serializes
data to Kafka in Avro format and how to stream this data into MariaDB
ColumnStore via the Kafka-Avro Data Adapter.

## Setting Up Kafka

Here's a very simple Docker Compose file for creating a local setup with a Kafka
broker.

```
version: '3'
services:
    zookeeper:
        image: confluent/zookeeper
        container_name: zookeeper
        ports:
            - "2181:2181"
            - "2888:2888"
            - "3888:3888"

    kafka:
        image: confluent/kafka
        container_name: kafka
        ports:
            - "9092:9092"
        depends_on:
            - zookeeper
        environment:
            KAFKA_ADVERTISED_HOST_NAME: kafka

    schema-registry:
        image: confluent/schema-registry
        container_name: schema-registry
        ports:
            - "8081:8081"
        depends_on:
            - kafka
```

Using this configuration, run `docker-compose up -d` and you have a local Kafka
setup. After that, just add the following line into `/etc/hosts`.

```
127.0.0.1 kafka
```

This way the hostname that the kafka broker advertises works for both the docker
containers and the host system.

## Setting Up ColumnStore

Clone [this repository](https://github.com/mariadb-corporation/mariadb-columnstore-docker)
and follow the instructions on how to set it up. To make testing of the setup
easier, add `--network host` to the `docker` command. This will start the
ColumnStore container with the host system's network.

After the container is running, execute the following command to copy the
ColumnStore configuration file from the container. Replace the `mcs-container`
with the name of your container.

```
docker cp mcs-container:/usr/local/mariadb/columnstore/etc/Columnstore.xml .
```

The command copies the `Columnstore.xml` file that contains all the
information needed to connect to ColumnStore.

## Creating the Client Application

The following is a minimal client application that streams data into Kafka as
Avro.

```java
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

public class KafkaAvroGenerator {

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
```

You can import the libraries by adding the following dependencies into your
`pom.xml`.

```
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>1.0.0-cp1</version>
</dependency>
  <dependency>
      <groupId>io.confluent</groupId>
    <artifactId>kafka-streams-avro-serde</artifactId>
    <version>4.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
    <version>1.8.2</version>
</dependency>
```

After that, build the client with the build system of your liking.

## Configuring the Adapter

The adapter is configured with a JSON configuration file. Write the following
into a file named `config.json`.

```javascript
{
    "options" : {
        "broker"   : "127.0.0.1:9092",
        "registry" : "127.0.0.1:8081",
        "config" : "/path/to/Columnstore.xml"
    },
    "streams" : [
        {
            "topic" : "hello.world",
            "database" : "test",
            "table" : "t1"
        }
    ]
}
```

The `streams` field defines an array of streams we want to connect to
ColumnStore. The `topic` field defines the name of the topic to consume and the
`database` and `table` fields together define the target table where the data is
stored.

The `options` field defines the adapter configuration. The `broker` field is the
address of the Kafka broker, the `registry` is the address of the Schema
Registry and `config` is the path to the `Columnstore.xml` file we copied
earlier.

Once the configuration is created, the database table needs to be created in
ColumnStore. The layout of the table must match the Avro schema of the consumed
stream. Execute the following SQL to create a table that matches the schema of
the test application.

```sql
CREATE TABLE test.t1(user_id INT, data VARCHAR(64));
```

## Starting the Adapter

To start the adapter, run the following command.

```
kafka_to_avro -c config.json
```

The `-c` flag tells where the configuration file is located. The default path
that the adapter uses is `/etc/mcs-kafka-adapter/config.json`.

Once the adapter is running, start the example Java client which will then
produce records and send them to the Kafka broker. In a few moments, the
ColumnStore table `test.t1` should have the data.
