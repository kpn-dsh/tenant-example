package com.kpn.dsh.example.kafka;

import com.kpn.dsh.example.envelope.DataEnvelopeDeserializer;
import com.kpn.dsh.example.envelope.DataEnvelopeSerializer;
import com.kpn.dsh.example.envelope.KeyEnvelopeDeserializer;
import com.kpn.dsh.example.envelope.KeyEnvelopeSerializer;
import com.kpn.dsh.messages.common.Envelope;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

public class KafkaUtils {
    final static Properties commonProps = getCommonProps();

    public static String getEnvOrDie(String env) {
        String ret = System.getenv(env);
        if (ret == null) {
            fatal(env + " environment needs to be set");
        }
        return ret;
    }

    /* load Kafka and streams properties from $PKI_CONFIG_DIR/datastreams.properties - populated by the get_signed_certificate.sh script */
    public static Properties getCommonProps() {
        String configDir = getEnvOrDie("PKI_CONFIG_DIR");
        Properties commonProps = new Properties();
        try {
            InputStream propStream = new FileInputStream(configDir + "/datastreams.properties");
            commonProps.load(propStream);
            propStream.close();
        } catch (Exception e) {
            fatal("Could not load stream properties file. Aborting. - " + e);
        }
        return commonProps;
    }

    public static Pattern getInputTopic() {
        String inputStream = getEnvOrDie("INPUT_STREAM");

        // The datastreams.<stream>.read property in the common properties file contains a regex pattern
        // to subscribe to - this facilitates subscription to data streams produced by multiple tenants.
        String strInputTopicPattern = commonProps.getProperty("datastream." + inputStream + ".read");
        if (strInputTopicPattern == null) {
            fatal("No permission to read from stream " + inputStream);
        }
        Pattern inputTopicPattern = Pattern.compile(strInputTopicPattern);
        System.out.println("inputTopicPattern" + inputTopicPattern);
        return inputTopicPattern;
    }

    public static String getOutputTopic() {
        String outputStream = getEnvOrDie("OUTPUT_STREAM");
        // The datastreams.<stream>.write property in the common properties file contains an exact
        // topic string to produce values to.
        String outputTopic = commonProps.getProperty("datastream." + outputStream + ".write");
        if (outputTopic == null) {
            fatal("No permission to write to stream " + outputStream);
        }
        System.out.println("outputTopic: " + outputTopic);
        return outputTopic;
    }

    public static String[] getComsumerGroups(String groupName) {
        return commonProps.getProperty("consumerGroups."+groupName).split(", *");
    }


    public static KafkaConsumer<Envelope.KeyEnvelope, Envelope.DataEnvelope> getConsumer() {
        Properties consumerProps = getCommonProps();

        String[] privateConsumerGroups = getComsumerGroups("private");
        String[] sharedConsumerGroups = getComsumerGroups("shared");

        consumerProps.put("group.id", sharedConsumerGroups[0]); // use a shared consumer group, otherwise all instances will respond to all messages
        consumerProps.put("key.deserializer", KeyEnvelopeDeserializer.class.getName());
        consumerProps.put("value.deserializer", DataEnvelopeDeserializer.class.getName());
        return new KafkaConsumer<>(consumerProps);
    }

    public static KafkaProducer<Envelope.KeyEnvelope, Envelope.DataEnvelope> getProducer() {
        Properties producerProps = getCommonProps();
        producerProps.put("acks", "all");
        producerProps.put("key.serializer", KeyEnvelopeSerializer.class.getName());
        producerProps.put("value.serializer", DataEnvelopeSerializer.class.getName());
        producerProps.put("partitioner.class", TopicLevelPartitioner.class.getName());

        return new KafkaProducer<>(producerProps);
    }

    public static void fatal(String message) {
        System.err.println("[fatal] " + message);
        System.exit(1);
    }

}
