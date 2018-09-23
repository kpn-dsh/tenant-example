package com.kpn.dsh.example;

import java.util.Map;
import java.nio.charset.Charset;
import com.kpn.dsh.messages.common.Envelope.*;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * KeyEnvelopeDeserializer deserializes enveloped keys.
 */
public class KeyEnvelopeDeserializer extends EnvelopeSerdes implements Deserializer<KeyEnvelope> {
    public void configure(Map<String,?> configs, boolean isKey) {
        super.configure(configs, isKey);
    }

    public KeyEnvelope deserialize(String topic, byte[] data) {
        if (data == null)
            return null;
        if (isEnvelopedTopic(topic)) {
            try {
                return KeyEnvelope.parseFrom(data);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                System.out.println("Got illegal protobuf message on "+topic);
                return null;
            }
        } else {
            return KeyEnvelope.newBuilder()
                .setKey(new String(data, Charset.forName("UTF-8")))
                .setHeader(KeyHeader.newBuilder()
                        .setQosValue(0)
                        .setRetained(false)
                        .setIdentifier(Identity.newBuilder()
                            .setTenant("unknown")
                            .setPublisher("unknown")))
                .build();
        }
    }

    public void close() {
    }
}
