package com.kpn.dsh.example;

import com.kpn.dsh.messages.common.Data.DataEnvelope;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.protobuf.AbstractKafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * DataEnvelopeDeserializer deserializes enveloped messages.
 */
public class DataEnvelopeDeserializer extends AbstractKafkaProtobufDeserializer<DataEnvelope> implements Deserializer<DataEnvelope> {

    public DataEnvelopeDeserializer() {
    }

    public DataEnvelopeDeserializer(SchemaRegistryClient client) {
        this.schemaRegistry = client;
    }

    public DataEnvelopeDeserializer(SchemaRegistryClient client, Map<String, ?> props) {
        this(client, props, null);
    }

    public DataEnvelopeDeserializer(SchemaRegistryClient client, Map<String, ?> props, Class<DataEnvelope> type) {
        this.schemaRegistry = client;
        this.configure(this.deserializerConfig(props), type);
    }

    public void configure(Map<String, ?> configs, boolean isKey) {
        this.configure(new KafkaProtobufDeserializerConfig(configs), isKey);
    }

    protected void configure(KafkaProtobufDeserializerConfig config, boolean isKey) {
        this.isKey = isKey;
        if (isKey) {
            this.configure(config, (Class<DataEnvelope>) config.getClass("specific.protobuf.key.type"));
        } else {
            this.configure(config, (Class<DataEnvelope>) config.getClass("specific.protobuf.value.type"));
        }
    }

    public DataEnvelope deserialize(String topic, byte[] data) {
        if (null == data) return null;

        return (DataEnvelope) this.deserialize(false, topic, this.isKey, data);
    }

    public void close() {}
}
