package com.kpn.dsh.example;

import java.util.Map;
import com.kpn.dsh.messages.common.Envelope.DataEnvelope;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * DataEnvelopeDeserializer deserializes enveloped messages.
 */
public class DataEnvelopeDeserializer extends EnvelopeSerdes implements Deserializer<DataEnvelope> {

    public void configure(Map<String,?> configs, boolean isKey) {
        super.configure(configs, isKey);
    }

    public void close() {}

    public DataEnvelope deserialize(String topic, byte[] data) {
        if (isEnvelopedTopic(topic)) {
            if (null == data) return null;   // this is actually an error, there should always be a DataEnvelope
            try {
                return DataEnvelope.parseFrom(data);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                System.out.println("Got illegal protobuf message on "+topic);
                return null;
            }
        } else {
            return DataEnvelopeSerializer.wrap(data);
        }
    }
}
