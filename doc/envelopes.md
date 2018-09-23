# Message Envelopes

On public streams, the DSH platform mandates the use of so-called message
envelopes. The message key must be wrapped in a key envelope, and the message
payload must be wrapped in a data envelope.

This document discusses the structure and usage of these message envelopes.

## Formal definition

The serialization scheme used on enveloped streams is [Google Protocol
Buffers](https://developers.google.com/protocol-buffers/).

### Key envelopes

Key envelopes are defined in the protocol buffers IDL as follows:

```protobuf
syntax = "proto3";

message KeyEnvelope {
    KeyHeader header = 1;           // header is mandatory on stream topics
    string key = 2;                 // MQTT topic (minus prefixes) on stream topics
    reserved 3;                     // deprecated field
}

message KeyHeader {
    // identifies the message origin
    Identity identifier = 1;
    // marks the message as 'retained'
    // this makes the Latest Value Store keep a copy of the message in memory for later retrieval
    bool retained = 2;
    // the QOS with which the message is handled within the system
    QoS qos = 3;
}

// identifies the data origin
message Identity {
    string  tenant    = 1;
    string  publisher = 2;
}

// System QOS identifiers
enum QoS {
    BEST_EFFORT  = 0;   // might be dropped in case of resources running low (~ highest throughput)
    RELIABLE     = 1;   // will *never* be dropped and retried until success (~ lowest throughput)
}

```

The key envelope mostly contains metadata that allows the DSH
protocol adapter(s) (currently the DSH platform only supports an MQTT protocol
adapter) to figure out how to properly handle the message.

- the `header` consists of
  - the `identifier` that identifies the message origin
    - the `tenant` field contains the tenant ID of the message (regardless of
      whether it entered the stream via a protocol adapter or via direct
      injection on Kafka). This field _must_ be set correctly, messages will
      be dropped (i.e., they will not be retained or forwarded to device
      clients) otherwise.
    - the `publisher` field is a free-from string that identifies a particular
      data source. For messages that are ingested over a protocol adapter, this
      field contains the client ID of the device that published the message.
      For messages injected directly on Kafka (i.e. from a container that runs
      on the DSH platform), the tenant is free to choose its own naming
      scheme. This field is purely informative, the DSH platform does not
      attach any semantic meaning to it.
  - the `retained` field indicates whether this message should be retained in
    the Last Value Store - i.e. whether it should be delivered to late
    subscribers on the MQTT side.
  - the `qos` field indicates the MQTT QoS level with which the protocol
    adapter will treat the message.
    - `BEST_EFFORT` (0) QoS means messages may be dropped in rare cases if the
      platform is under high load. Use this for messages that are frequently
      refreshed (e.g. a vehicle that publishes its position every second)
    - `RELIABLE` (1) QoS means that delivery of messages will be retried until
      successful. Using this option has a cost in terms of throughput, so only
      do this for messages that must absolutely in all cases be delivered.
- the `key` field contains the actual message key, which corresponds to the
  MQTT topic on which the message will be exposed on the protocol adapter,
  minus the prefix and stream name. For example, a message that is exposed on
  the MQTT side on topic `/tt/foo/a/b/c`, corresponds with a message on DSH
  stream `foo` with key `a/b/c` (note that there is no `/` character in front
  of the key)

**Note** The QoS field in the key envelope only applies to QoS on the MQTT
side: once a message has been ingested on a Kafka stream, it is reliably
available to all Kafka (i.e. on-platform) consumers, regardless of the QoS
value.

### Data envelopes

Data envelopes are defined in the protocol buffers IDL as follows:

```protobuf
syntax = "proto3";

message DataEnvelope {
    oneof kind {                      // main payload for data messages;
                                      // leave empty for DELETE semantics in Latest Value Store
        bytes payload = 1;
    }
    map<string, string> tracing = 2; // tracing data: used for passing span contexts between applications
    // tenant-specific fields are ONLY allowed in the 500-1000 range
}
```

- the `payload` field contains the actual message payload. Note the use of the
  `oneof` construct: this allows to make a distinction between a data envelope
  with empty payload  and a data envelope with _no_ payload. The difference
  between the two is relevant for the interpretation of the message by the
  Latest Value Store and the MQTT protocol adapter. See
  [below](#empty-payload) for a detailed discussion.
- the `tracing` field is used to transport span contexts between different
  platform components. See the [Distributed Tracing](tracing.md) document for
  more details.

#### Empty payload

There are three ways in which one could conceivably encode a DELETE message
(i.e. a message that removes a previously retained message from the Latest
Value Store) as a Kafka message:

- as a Kafka message with a properly enveloped key, and a `null` value.
  **This is not allowed. Messages on public streams must _always_ have a
  properly enveloped value.**
- as a Kafka message with a properly enveloped key, and a data envelope value
  that has a zero-length binary payload. **This will not be treated as a
  DELETE message, but rather as a pathological case of a regular message with
  a zero-length content.** In other words, injecting such a message on a Kafka
  stream will cause the message (if it is marked as retained) to be stored in
  the Latest Value Store.
- as a Kafka message with a properly enveloped key, and a data envelope value
  that has an _empty_ `kind`. **This is the only correct way to encode a
  DELETE message on Kafka.** Upon receipt of such a message, the Latest Value
  Store will remove the corresponding key from its state store entirely.

The following code fragment illustrates the three alternatives in Java:

```java
KeyEnvelope key = KeyEnvelope.newBuilder()
                    .setKey("a/b/c/d")
                    .setHeader(KeyHeader.newBuilder()
                        .setQosValue(0)
                        .setRetained(true)
                        .setIdentifier(Identity.newBuilder()
                            .setTenant("foo")
                            .setPublisher("bar")
                        )
                    ).build();

/* NOT ALLOWED: Kafka message with null value on public stream */
producer.send(new ProducerMessage<KeyEnvelope, DataEnvelope>(topic, key, null));

/* will not act as a DELETE message: zero-length binary payload */
DataEnvelope data1 = DataEnvelope.newBuilder()
                        .setPayload(ByteString.EMPTY)
                        .build();
producer.send(new ProducerMessage<KeyEnvelope, DataEnvelope>(topic, key, data1));

/* will act as a proper DELETE message: empty payload */
DataEnvelope data2 = DataEnvelope.newBuilder()
                        .clearKind()
                        .build();
producer.send(new ProducerMessage<KeyEnvelope, DataEnvelope>(topic, key, data2));
```
