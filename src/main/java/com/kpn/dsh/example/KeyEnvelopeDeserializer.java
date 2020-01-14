package com.kpn.dsh.example;

import java.util.Map;
import java.nio.charset.Charset;
import com.kpn.dsh.messages.common.Envelope.*;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * KeyEnvelopeDeserializer deserializes enveloped keys.
 */
public class KeyEnvelopeDeserializer implements Deserializer<KeyEnvelope> {

    public void configure(Map<String,?> configs, boolean isKey){}

    public KeyEnvelope deserialize(String topic, byte[] data) {
        if (data == null) return null;

        try {
            return KeyEnvelope.parseFrom(data);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            System.out.println("Got illegal protobuf message on "+topic);
            return null;
        }
    }

    public void close() {}
}
