package com.kpn.dsh.example;

import java.util.Map;
import com.kpn.dsh.messages.common.Envelope.*;
import org.apache.kafka.common.serialization.Serializer;
import com.google.protobuf.ByteString;

import static com.kpn.dsh.messages.common.Envelope.DataEnvelope.KindCase.KIND_NOT_SET;

/**
 * DataEnvelopeSerializer serializes enveloped messages.
 *
 * The class also contains some static utility functions that
 * conveniently wrap a plain byte array in an envelope.
 */
public class DataEnvelopeSerializer extends EnvelopeSerdes implements Serializer<DataEnvelope> {
    public void configure(Map<String,?> configs, boolean isKey) {
        super.configure(configs, isKey);
    }

    public void close() {}

    public byte[] serialize(String topic, DataEnvelope data) {
        if (isEnvelopedTopic(topic)) {
            return data.toByteArray();
        } else {
            return (KIND_NOT_SET == data.getKindCase())
                    ? null
                    : data.getPayload().toByteArray();
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
