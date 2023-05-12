package com.kpn.dsh.example;

import com.kpn.dsh.messages.common.Data.DataEnvelope;
import com.kpn.dsh.messages.common.Key.Identity;
import com.kpn.dsh.messages.common.Key.KeyEnvelope;
import com.kpn.dsh.messages.common.Key.KeyHeader;
import com.uber.jaeger.Configuration;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.util.GlobalTracer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;

import static com.kpn.dsh.messages.common.Data.DataEnvelope.KindCase.KIND_NOT_SET;

/**
 * Noop consumer rebalance listener for Kafka.
 *
 * The kafka client API requires you to specify a rebalance listener for wildcard subscribes.
 */
class NoopRebalanceListener implements ConsumerRebalanceListener {
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) { }
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) { }
}

/**
 * This example implements a really simple automated bot that responds to commands that are sent
 * to it over MQTT.
 *
 * Whenever a supported command is sent on /tt/&lt;input-stream&gt;/command/&lt;whatever/suffix&gt;,
 * the bot responds appropriately with a message on /tt/&lt;output-stream&gt;/response/&lt;whatever/suffix&gt;.
 * The bot understands following commands:
 * - "whoami": the response will include the sender's identity (tenant id/publisher id)
 * - "restart": the response will say "bye bye" and the container will restart.
 */
public class Main {
    static KafkaConsumer<KeyEnvelope, DataEnvelope> consumer;
    static KafkaProducer<KeyEnvelope, DataEnvelope> producer;
    static String outputTopic;
    static Pattern inputTopicPattern;
    static String[] privateConsumerGroups;
    static String[] sharedConsumerGroups;

    public static void fatal(String message) {
        System.err.println("[fatal] " + message);
        System.exit(1);
    }

    public static String getEnvOrDie(String env) {
        String ret = System.getenv(env);
        if (ret == null) {
            fatal(env + " environment needs to be set");
        }
        return ret;
    }

    public static KeyEnvelope wrapKey(String key) {
        return KeyEnvelopeSerializer.wrap(key);
    }

    public static DataEnvelope wrapData(String data, Span span) {
        byte[] asBytes = data.getBytes(Charset.forName("UTF-8"));
        if (span == null) {
            return DataEnvelopeSerializer.wrap(asBytes);
        }
        HashMap<String,String> trace=new HashMap<>();
        GlobalTracer.get().inject(span.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(trace));
        return DataEnvelopeSerializer.wrap(asBytes, trace);
    }

    // returns null if the envelope doesn't contain tracing information
    public static SpanContext spanContextFromDataEnvelope(DataEnvelope d) {
        if (d.getTracingMap().size() == 0) {
            return null;
        }
        return GlobalTracer.get().extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(d.getTracingMap()));
    }

    // creates a span that covers the period between message creation and now
    public static Span makeInTransitSpan(SpanContext envSpan, ConsumerRecord<KeyEnvelope, DataEnvelope> record) {
        Span inTransit = GlobalTracer.get()
                .buildSpan("message-fetch-delay")
                .withStartTimestamp(record.timestamp()*1000)
                .addReference(References.FOLLOWS_FROM, envSpan)
                .start();
        inTransit.finish();
        return inTransit;
    }

    public static void processMessage(ConsumerRecord<KeyEnvelope, DataEnvelope> record) {
        // ignore messages with missing key or value
        KeyEnvelope wrappedKey = record.key();
        if (wrappedKey == null) {
            return;
        }
        DataEnvelope wrappedValue = record.value();
        if (wrappedValue == null) {
            return;
        }

        KeyHeader header = wrappedKey.getHeader();
        Identity identity = header.getIdentifier();

        // wrappedKey.getKey() returns the MQTT topic path fragment past /tt/<stream-name>
        String[] splitKey = wrappedKey.getKey().split("/");

        if (!splitKey[0].equals("command")) {
            System.out.println("Key not handled: "+wrappedKey.getKey());
            return;
        }

        // if the incoming message contains tracing information, start a tracing span as well
        Span span = null;
        SpanContext parentSpanContext = spanContextFromDataEnvelope(wrappedValue);
        if (parentSpanContext != null) {
            Span inTransit = makeInTransitSpan(parentSpanContext,record) ;
            span = GlobalTracer.get()
                    .buildSpan("process-message")
                    .addReference(References.FOLLOWS_FROM, inTransit.context())
                    .start();
        }

        String command = (KIND_NOT_SET == wrappedValue.getKindCase())
                ? null
                : wrappedValue.getPayload().toStringUtf8();

        System.out.println("Received command: " + command);

        splitKey[0] = "response";
        KeyEnvelope responseKey = wrapKey(String.join("/", splitKey));
        switch (command) {
            case "whoami":
                String response = answerId + " says you are: " + identity;
                DataEnvelope responseData = wrapData(response, span);
                producer.send(
                        new ProducerRecord<>(outputTopic, responseKey, responseData));
                producer.flush();
                break;
            case "restart":
                producer.send(
                        new ProducerRecord<>(outputTopic, responseKey, wrapData("bye bye", span)));
                consumer.wakeup(); // this will raise the WakeupException that will break us out of the infinite loop
                producer.flush();
                break;
            default:
                System.out.println("Command not handled: "+command);
                break;
        }
        if (span != null) {
            span.finish();
        }
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

    public static void setupKafka() {
        String inputStream = getEnvOrDie("INPUT_STREAM");
        String outputStream = getEnvOrDie("OUTPUT_STREAM");

        Properties commonProps = getCommonProps();

        // The datastreams.<stream>.read property in the common properties file contains a regex pattern
        // to subscribe to - this facilitates subscription to data streams produced by multiple tenants.
        String strInputTopicPattern = commonProps.getProperty("datastream." + inputStream + ".read");
        if (strInputTopicPattern == null) {
            fatal("No permission to read from stream " + inputStream);
        }
        inputTopicPattern = Pattern.compile(strInputTopicPattern);

        // The datastreams.<stream>.write property in the common properties file contains an exact
        // topic string to produce values to.
        outputTopic = commonProps.getProperty("datastream." + outputStream + ".write");
        if (outputTopic == null) {
            fatal("No permission to write to stream " + outputStream);
        }

        privateConsumerGroups = commonProps.getProperty("consumerGroups.private").split(", *");
        sharedConsumerGroups = commonProps.getProperty("consumerGroups.shared").split(", *");

        /* set up kafka consumer */
        Properties consumerProps = getCommonProps();
        consumerProps.put("group.id", privateConsumerGroups[0]); // use a shared consumer group, otherwise all instances will respond to all messages
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, KeyEnvelopeDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, DataEnvelopeDeserializer.class.getName());
        consumerProps.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_KEY_TYPE, KeyEnvelope.class.getName());
        consumerProps.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, DataEnvelope.class.getName());
        consumerProps.put("schema.registry.url", "https://schema-store.dex-dev-c.marathon.mesos:8443");
        consumerProps.put("schema.registry.ssl.truststore.location", consumerProps.getProperty("ssl.truststore.location"));
        consumerProps.put("schema.registry.ssl.truststore.password", consumerProps.getProperty("ssl.truststore.password"));
        consumerProps.put("schema.registry.ssl.keystore.location", consumerProps.getProperty("ssl.keystore.location"));
        consumerProps.put("schema.registry.ssl.keystore.password", consumerProps.getProperty("ssl.keystore.password"));
        consumerProps.put("schema.registry.ssl.key.password", consumerProps.getProperty("ssl.key.password"));
        consumer = new KafkaConsumer<>(consumerProps);

        /* set up kafka producer */
        Properties producerProps = getCommonProps();
        producerProps.put("acks", "all");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KeyEnvelopeSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class.getName());
        producerProps.put("partitioner.class",TopicLevelPartitioner.class.getName());
        producerProps.put("schema.registry.url", "https://schema-store.dex-dev-c.marathon.mesos:8443");
        producerProps.put("schema.registry.ssl.truststore.location", producerProps.getProperty("ssl.truststore.location"));
        producerProps.put("schema.registry.ssl.truststore.password", producerProps.getProperty("ssl.truststore.password"));
        producerProps.put("schema.registry.ssl.keystore.location", producerProps.getProperty("ssl.keystore.location"));
        producerProps.put("schema.registry.ssl.keystore.password", producerProps.getProperty("ssl.keystore.password"));
        producerProps.put("schema.registry.ssl.key.password", producerProps.getProperty("ssl.key.password"));
        producer = new KafkaProducer<>(producerProps);
    }

    private static String answerId = "tenant-example";

    public static void main(String[] args) {
        /* When a container is started on the DSH platform, the MARATHON_APP_ID
         * environment is set. It contains a string that looks like:
         * /tenant/name_of_the_application. We split that string to get the
         * tenant and the application name. */
        String[] identifier = getEnvOrDie("MARATHON_APP_ID").replaceFirst("^/", "").split("/",2);
        /* Set our identity. The identity consists of the tenant name and It will be included in the key envelope of every
         * outgoing message. */
        KeyEnvelopeSerializer.setIdentifier(identifier[0], identifier.length > 1 ? identifier[1] : "tenant-example");

        answerId=identifier[0] + "/" + (identifier.length > 1 ? identifier[1] : "tenant-example");

        Configuration jaegerCfg = Configuration.fromEnv();
        Configuration.ReporterConfiguration reporterCfg = jaegerCfg.getReporter();

        GlobalTracer.register(
                jaegerCfg
                        .withReporter(reporterCfg.withSender(reporterCfg.getSenderConfiguration().withEndpoint(System.getenv("DSH_TRACING_ENDPOINT"))))
                        .getTracer()
        );

        setupKafka();

        try {
            consumer.subscribe(inputTopicPattern, new NoopRebalanceListener());

            while (true) {
                try {
                    ConsumerRecords<KeyEnvelope, DataEnvelope> records = consumer.poll(Long.MAX_VALUE);
                    for (ConsumerRecord<KeyEnvelope, DataEnvelope> record : records) {
                        processMessage(record);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    consumer.commitSync();
                }
            }
        } catch (WakeupException e) {
            // ignore for shutdown
        } finally {
            consumer.close();
            producer.close();
        }
    }
}
