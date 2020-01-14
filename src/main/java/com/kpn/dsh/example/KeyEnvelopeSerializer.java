package com.kpn.dsh.example;

import java.util.Map;
import java.nio.charset.Charset;
import com.kpn.dsh.messages.common.Envelope.*;
import org.apache.kafka.common.serialization.Serializer;

/**
 * KeyEnvelopeSerializer serializes enveloped keys.
 *
 * The class also contains some static utility functions that
 * conveniently wrap a plain string key in an envelope.
 *
 * Before using the wrapper functions, make sure to configure the 
 * KeyEnvelopeSerializer by calling the setIdentifier method.
 */
public class KeyEnvelopeSerializer implements Serializer<KeyEnvelope> {
    public void configure(Map<String,?> configs, boolean isKey) {}

    public void close() {}

    public byte[] serialize(String topic, KeyEnvelope data) {
        return data.toByteArray();
    }

    static private String tenant = null;
    static private String publisher = null;

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
