package com.kpn.dsh.example;

import com.google.protobuf.InvalidProtocolBufferException;
import com.kpn.dsh.messages.common.Key;
import com.kpn.dsh.messages.common.Key.KeyEnvelope;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.serializers.protobuf.AbstractKafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * KeyEnvelopeDeserializer deserializes enveloped keys.
 */
public class KeyEnvelopeDeserializer extends AbstractKafkaProtobufDeserializer<KeyEnvelope> implements Deserializer<KeyEnvelope> {

    public KeyEnvelopeDeserializer() {
    }

    public KeyEnvelopeDeserializer(SchemaRegistryClient client) {
        this.schemaRegistry = client;
    }

    public KeyEnvelopeDeserializer(SchemaRegistryClient client, Map<String, ?> props) {
        this(client, props, null);
    }

    public KeyEnvelopeDeserializer(SchemaRegistryClient client, Map<String, ?> props, Class<KeyEnvelope> type) {
        this.schemaRegistry = client;
        this.configure(this.deserializerConfig(props), type);
    }

    public void configure(Map<String, ?> configs, boolean isKey) {
        this.configure(new KafkaProtobufDeserializerConfig(configs), isKey);
    }

    protected void configure(KafkaProtobufDeserializerConfig config, boolean isKey) {
        this.isKey = isKey;
        if (isKey) {
            this.configure(config, (Class<KeyEnvelope>) config.getClass("specific.protobuf.key.type"));
        } else {
            this.configure(config, (Class<KeyEnvelope>) config.getClass("specific.protobuf.value.type"));
        }
    }

    public KeyEnvelope deserialize(String topic, byte[] key) {
        if (null == key) {
            System.err.println("key is null");
            return null;
        } else {
            System.err.println("key is not null");
        }
        System.err.println("key: " + key);
        System.err.println("key: " + new String(key));

        KeyEnvelope keyEnvelope = null;
        try {
            keyEnvelope = KeyEnvelope.parseFrom(key);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        System.err.println("keyEnvelope key: " + keyEnvelope.getKey());

//        ByteBuffer buffer = this.getByteBuffer(key);
//        int id = buffer.getInt();
//        System.err.println("id is: " + id);
//        try {
//            this.schemaRegistry.getAllSubjects().forEach(subject -> System.err.println("subject: " + subject));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        keyEnvelope = (KeyEnvelope) this.deserialize(key);
//        System.err.println("keyEnvelope key: " + keyEnvelope.getKey());

        return keyEnvelope;
    }

    public void close() {}
}
