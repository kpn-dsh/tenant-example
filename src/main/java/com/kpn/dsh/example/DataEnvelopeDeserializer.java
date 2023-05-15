package com.kpn.dsh.example;

import java.util.Map;

import com.kpn.dsh.messages.common.Envelope;
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
            Envelope.DataEnvelope dataEnvelope = Envelope.DataEnvelope.parseFrom(data);
            System.err.println("deserialize dataEnvelope data");
            return  dataEnvelope;
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            System.out.println("Got illegal protobuf message on "+topic);
            return null;
        }
    }
}
