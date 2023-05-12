package com.kpn.dsh.example;

import com.google.protobuf.Descriptors;
import com.kpn.dsh.messages.common.Key.Identity;
import com.kpn.dsh.messages.common.Key.KeyEnvelope;
import com.kpn.dsh.messages.common.Key.KeyHeader;
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

import java.io.IOException;
import java.util.Map;

/**
 * KeyEnvelopeSerializer serializes enveloped keys.
 *
 * The class also contains some static utility functions that
 * conveniently wrap a plain string key in an envelope.
 *
 * Before using the wrapper functions, make sure to configure the 
 * KeyEnvelopeSerializer by calling the setIdentifier method.
 */
public class KeyEnvelopeSerializer extends AbstractKafkaProtobufSerializer<KeyEnvelope> implements Serializer<KeyEnvelope> {

    private static int DEFAULT_CACHE_CAPACITY = 1000;

    private Map<Descriptors.Descriptor, ProtobufSchema> schemaCache;

    private boolean isKey;

    static private String tenant = null;
    static private String publisher = null;

    public KeyEnvelopeSerializer() {
        this.schemaCache = new BoundedConcurrentHashMap(DEFAULT_CACHE_CAPACITY);
    }

    public KeyEnvelopeSerializer(SchemaRegistryClient client) {
        this.schemaRegistry = client;
        this.schemaCache = new BoundedConcurrentHashMap(DEFAULT_CACHE_CAPACITY);
    }

    public KeyEnvelopeSerializer(SchemaRegistryClient client, Map<String, ?> props) {
        this(client, props, DEFAULT_CACHE_CAPACITY);
    }

    public KeyEnvelopeSerializer(SchemaRegistryClient client, Map<String, ?> props, int cacheCapacity) {
        this.schemaRegistry = client;
        this.configure(this.serializerConfig(props));
        this.schemaCache = new BoundedConcurrentHashMap(cacheCapacity);
    }

    public void configure(Map<String,?> configs, boolean isKey) {
        this.isKey = isKey;
        this.configure(new KafkaProtobufSerializerConfig(configs));
    }

    public void close() {}

    public byte[] serialize(String topic, KeyEnvelope key) {
        if (this.schemaRegistry == null) {
            throw new InvalidConfigurationException("SchemaRegistryClient not found. You need to configure the serializer or use serializer constructor with SchemaRegistryClient.");
        } else if (key == null) {
            return null;
        } else {
            ProtobufSchema schema = this.schemaCache.get(key.getDescriptorForType());
            if (schema == null) {
                System.err.println(String.format("Schema is null"));
                schema = ProtobufSchemaUtils.getSchema(key);

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
                this.schemaCache.put(key.getDescriptorForType(), schema);
            }
            return this.serializeImpl(this.getSubjectName(topic, this.isKey, key, schema), topic, this.isKey, key, schema);
        }
    }

    /**
     * Configure the producer's identity.
     *
     * You must call this function before you can invoke the wrap() method.
     *
     * @param tenant the tenant id
     * @param publisher a free-form string identifying this particular data producer.
     */
    static void setIdentifier(String tenant, String publisher) {
        KeyEnvelopeSerializer.tenant = tenant;
        KeyEnvelopeSerializer.publisher = publisher;
    }

    /**
     * Wrap a plain string key (the MQTT topic fragment) in a KeyEnvelope.
     *
     * QoS is set to 0 (BEST_EFFORT) and retained is set to false.
     *
     * Note: make sure to call setIdentifier() before calling this method.
     *
     * @param key the plain string key
     * @return the KeyEnvelope wrapping the plain string key
     */
    public static KeyEnvelope wrap(String key) {
        return wrap(key, 0, false);
    }

    /**
     * Wrap a plain string key (the MQTT topic fragment) in a KeyEnvelope with
     * specific QoS and retained settings.
     *
     * Note: make sure to call setIdentifier() before calling this method.
     *
     * @param key the plain string key
     * @param qos the QoS value (0 or 1)
     * @param retain indicates whether the message should be retained
     * @return the KeyEnvelope wrapping the plain string key
     */
    public static KeyEnvelope wrap(String key, int qos, boolean retain) {
        if (tenant == null || publisher == null) {
            throw new NullPointerException("KeyEnvelopeSerializer.setIdentifier() was not called prior to calling KeyEnvelopeSerializer.wrap()");
        }
        if (qos != 0 && qos != 1) {
            throw new IllegalArgumentException("qos must be 0 or 1");
        }
        return KeyEnvelope.newBuilder()
            .setKey(key)
            .setHeader(KeyHeader.newBuilder()
                    .setQosValue(qos)
                    .setRetained(retain)
                    .setIdentifier(Identity.newBuilder()
                        .setTenant(KeyEnvelopeSerializer.tenant)
                        .setFreeForm(KeyEnvelopeSerializer.publisher)))
            .build();
    }
}
