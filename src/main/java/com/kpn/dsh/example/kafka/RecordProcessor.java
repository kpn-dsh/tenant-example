package com.kpn.dsh.example.kafka;

import com.kpn.dsh.example.envelope.DataEnvelopeSerializer;
import com.kpn.dsh.example.envelope.KeyEnvelopeSerializer;
import com.kpn.dsh.messages.common.Envelope;
import io.jaegertracing.Configuration;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.util.GlobalTracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.regex.Pattern;

import static com.kpn.dsh.example.kafka.KafkaUtils.*;
import static com.kpn.dsh.messages.common.Envelope.DataEnvelope.KindCase.KIND_NOT_SET;

public class RecordProcessor {

    KafkaConsumer<Envelope.KeyEnvelope, Envelope.DataEnvelope> consumer = getConsumer();
    KafkaProducer<Envelope.KeyEnvelope, Envelope.DataEnvelope> producer = getProducer();
    String outputTopic = getOutputTopic();
    Pattern inputTopicPattern = getInputTopic();

    private void processMessage(ConsumerRecord<Envelope.KeyEnvelope, Envelope.DataEnvelope> record, String answerId) {
        // ignore messages with missing key or value
        Envelope.KeyEnvelope wrappedKey = record.key();
        if (wrappedKey == null) {
            return;
        }
        Envelope.DataEnvelope wrappedValue = record.value();
        if (wrappedValue == null) {
            return;
        }

        Envelope.KeyHeader header = wrappedKey.getHeader();
        Envelope.Identity identity = header.getIdentifier();

        // wrappedKey.getKey() returns the MQTT topic path fragment past /tt/<stream-name>
        String[] splitKey = wrappedKey.getKey().split("/");

        if (!splitKey[0].equals("command")) {
            System.out.println("Key not handled: "+wrappedKey.getKey());
            return;
        }

        // if the incoming message contains tracing information, start a tracing span as well
        Span span = null;
        SpanContext parentSpanContext = spanContextFromDataEnvelope(wrappedValue);
        if (parentSpanContext != null) {
            Span inTransit = makeInTransitSpan(parentSpanContext,record) ;
            span = GlobalTracer.get()
                    .buildSpan("process-message")
                    .addReference(References.FOLLOWS_FROM, inTransit.context())
                    .start();
        }

        String command = (KIND_NOT_SET == wrappedValue.getKindCase())
                ? null
                : wrappedValue.getPayload().toStringUtf8();

        System.out.println("Received command: " + command);

        splitKey[0] = "response";
        Envelope.KeyEnvelope responseKey = wrapKey(String.join("/", splitKey));

        switch (command) {
            case "whoami":
                String response = answerId + " says you are: " + identity.toString();
                Envelope.DataEnvelope responseData = wrapData(response, span);
                System.out.println("Response key: " + responseKey);
                System.out.println("Output topic: "+ outputTopic);
                System.out.println("Response data: "+ responseData);
                producer.send(
                        new ProducerRecord<>(outputTopic, responseKey, responseData));
                break;
            case "restart":
                System.out.println("Response key: " + responseKey);
                System.out.println("Output topic: "+ outputTopic);
                producer.send(
                        new ProducerRecord<>(outputTopic, responseKey, wrapData("bye bye", span)));
                consumer.wakeup(); // this will raise the WakeupException that will break us out of the infinite loop
                break;
            default:
                System.out.println("Command not handled: "+command);
                break;
        }
        if (span != null) {
            span.finish();
        }
    }

    public void runProcess(){
        /* When a container is started on the DSH platform, the MARATHON_APP_ID
         * environment is set. It contains a string that looks like:
         * /tenant/name_of_the_application. We split that string to get the
         * tenant and the application name. */

        String[] identifier = getEnvOrDie("MARATHON_APP_ID").replaceFirst("^/", "").split("/",2);
        /* Set our identity. The identity consists of the tenant name and It will be included in the key envelope of every
         * outgoing message. */
        KeyEnvelopeSerializer.setIdentifier(identifier[0], identifier.length > 1 ? identifier[1] : "tenant-example");

        String answerId=identifier[0] + "/" + (identifier.length > 1 ? identifier[1] : "tenant-example");

        Configuration jaegerCfg = Configuration.fromEnv();
        Configuration.ReporterConfiguration reporterCfg = jaegerCfg.getReporter();

        GlobalTracer.register(
                jaegerCfg
                        .withReporter(reporterCfg.withSender(reporterCfg.getSenderConfiguration().withEndpoint(System.getenv("DSH_TRACING_ENDPOINT"))))
                        .getTracer()
        );

        try {
            consumer.subscribe(inputTopicPattern, new NoopRebalanceListener());
            System.out.println("inputTopicPattern" + inputTopicPattern);

            while (true) {
                ConsumerRecords<Envelope.KeyEnvelope, Envelope.DataEnvelope> records = consumer.poll(Long.MAX_VALUE);
                for (ConsumerRecord<Envelope.KeyEnvelope, Envelope.DataEnvelope> record : records) {
                    System.out.println("Processing the record...");
                    System.out.println("Record:" + record.key().toString());
                    System.out.println("Record:" + record.value().toString());
                    processMessage(record, answerId);
                }
            }
        } catch (WakeupException e) {
            // ignore for shutdown
        } finally {
            consumer.close();
            producer.close();
        }
    }
    private Envelope.KeyEnvelope wrapKey(String key) {
        return KeyEnvelopeSerializer.wrap(key);
    }

    private Envelope.DataEnvelope wrapData(String data, Span span) {
        byte[] asBytes = data.getBytes(Charset.forName("UTF-8"));
        if (span == null) {
            return DataEnvelopeSerializer.wrap(asBytes);
        }
        HashMap<String,String> trace=new HashMap<>();
        GlobalTracer.get().inject(span.context(), Format.Builtin.TEXT_MAP, new TextMapAdapter(trace));
        return DataEnvelopeSerializer.wrap(asBytes, trace);
    }

    // returns null if the envelope doesn't contain tracing information
    private SpanContext spanContextFromDataEnvelope(Envelope.DataEnvelope d) {
        if (d.getTracingMap().size() == 0) {
            return null;
        }
        return GlobalTracer.get().extract(Format.Builtin.TEXT_MAP, new TextMapAdapter(d.getTracingMap()));
    }
    // creates a span that covers the period between message creation and now
    private Span makeInTransitSpan(SpanContext envSpan, ConsumerRecord<Envelope.KeyEnvelope, Envelope.DataEnvelope> record) {
        Span inTransit = GlobalTracer.get()
                .buildSpan("message-fetch-delay")
                .withStartTimestamp(record.timestamp()*1000)
                .addReference(References.FOLLOWS_FROM, envSpan)
                .start();
        inTransit.finish();
        return inTransit;
    }
}
