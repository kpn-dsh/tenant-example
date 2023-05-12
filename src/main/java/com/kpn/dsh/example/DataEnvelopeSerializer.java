package com.kpn.dsh.example;

import java.io.IOException;
import java.util.Map;

import com.google.protobuf.Descriptors;
import com.kpn.dsh.messages.common.Data;
import com.kpn.dsh.messages.common.Data.*;
import com.kpn.dsh.messages.common.Key;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils;
import io.confluent.kafka.schemaregistry.utils.BoundedConcurrentHashMap;
import io.confluent.kafka.serializers.protobuf.AbstractKafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import com.google.protobuf.ByteString;

/**
 * DataEnvelopeSerializer serializes enveloped messages.
 *
 * The class also contains some static utility functions that
 * conveniently wrap a plain byte array in an envelope.
 */
public class DataEnvelopeSerializer extends AbstractKafkaProtobufSerializer<Data.DataEnvelope> implements Serializer<DataEnvelope> {

    private static int DEFAULT_CACHE_CAPACITY = 1000;

    private Map<Descriptors.Descriptor, ProtobufSchema> schemaCache;

    private boolean isKey;

    public DataEnvelopeSerializer() {
        this.schemaCache = new BoundedConcurrentHashMap(DEFAULT_CACHE_CAPACITY);
    }

    public DataEnvelopeSerializer(SchemaRegistryClient client) {
        this.schemaRegistry = client;
        this.schemaCache = new BoundedConcurrentHashMap(DEFAULT_CACHE_CAPACITY);
    }

    public DataEnvelopeSerializer(SchemaRegistryClient client, Map<String, ?> props) {
        this(client, props, DEFAULT_CACHE_CAPACITY);
    }

    public DataEnvelopeSerializer(SchemaRegistryClient client, Map<String, ?> props, int cacheCapacity) {
        this.schemaRegistry = client;
        this.configure(this.serializerConfig(props));
        this.schemaCache = new BoundedConcurrentHashMap(cacheCapacity);
    }

    public void configure(Map<String, ?> configs, boolean isKey) {
        this.isKey = isKey;
        this.configure(new KafkaProtobufSerializerConfig(configs));
    }

    public void close() {}

    public byte[] serialize(String topic, DataEnvelope data) {
        System.err.println(String.format("Schema registry is: %s",schemaRegistry.toString()));

        if (this.schemaRegistry == null) {
            throw new InvalidConfigurationException("SchemaRegistryClient not found. You need to configure the serializer or use serializer constructor with SchemaRegistryClient.");
        } else if (data == null) {
            return null;
        } else {
            ProtobufSchema schema = this.schemaCache.get(data.getDescriptorForType());
            if (schema == null) {
                schema = ProtobufSchemaUtils.getSchema(data);

                try {
                    boolean autoRegisterForDeps = this.autoRegisterSchema && !this.onlyLookupReferencesBySchema;
                    boolean useLatestForDeps = this.useLatestVersion && !this.onlyLookupReferencesBySchema;
                    schema = resolveDependencies(this.schemaRegistry, this.normalizeSchema,
                            autoRegisterForDeps, useLatestForDeps,
                            this.latestCompatStrict, this.latestVersions,
                            this.skipKnownTypes, this.referenceSubjectNameStrategy,
                            topic, this.isKey, schema);
                } catch (RestClientException | IOException e) {
                    throw new SerializationException("Error serializing Protobuf message", e);
                }

                this.schemaCache.put(data.getDescriptorForType(), schema);
            }
            return this.serializeImpl(this.getSubjectName(topic, this.isKey, data, schema), topic, this.isKey, data, schema);
        }
    }

    /**
     * Wrap a plain byte array in a DataEnvelope.
     *
     * @param data the plain message data
     * @return the wrapped data
     */
    public static DataEnvelope wrap(byte[] data) {
        if (null == data) {
            return DataEnvelope.newBuilder()
                .clearKind()
                .build();
        } else {
            return DataEnvelope.newBuilder()
                .setPayload(ByteString.copyFrom(data))
                .build();
        }
    }

    /**
     * Wrap a plain byte array in a DataEnvelope.
     *
     * @param data the plain message data
     * @param tracing a map where tracing info has been injected into
     * @return the wrapped data
     */
    public static DataEnvelope wrap(byte[] data, Map<String,String> tracing) {
        if (null == data) {
            return DataEnvelope.newBuilder()
                .clearKind()
                .putAllTracing(tracing)
                .build();
        } else {
            return DataEnvelope.newBuilder()
                .setPayload(ByteString.copyFrom(data))
                .putAllTracing(tracing)
                .build();
        }
    }
}
