package com.kpn.dsh.example.envelope;

import java.util.Map;
import com.kpn.dsh.messages.common.Envelope.DataEnvelope;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * DataEnvelopeDeserializer deserializes enveloped messages.
 */
public class DataEnvelopeDeserializer implements Deserializer<DataEnvelope> {

    public void configure(Map<String,?> configs, boolean isKey) {}

    public void close() {}

    public DataEnvelope deserialize(String topic, byte[] data) {
        if (null == data) return null;

        try {
            return DataEnvelope.parseFrom(data);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            System.out.println("Got illegal protobuf message on "+topic);
            return null;
        }
    }
}
