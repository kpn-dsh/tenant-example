package com.kpn.dsh.example;

import java.lang.StringBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.utils.Utils;
import com.kpn.dsh.messages.common.Envelope.KeyEnvelope;

/**
 * TopicLevelPartitioner partitions outgoing messages by hashing the 
 * first n levels of the MQTT topic path.
 */
public class TopicLevelPartitioner implements Partitioner {
    static final int defaultDepth = 12;
    private static final String writeKeyRegex = "^datastream\\..*\\.write$";

    private HashMap<String, Integer> depths = new HashMap<>();

    private static int ordinalIndexOf(String str, String substr, int n) {
        int pos = str.indexOf(substr);
        while (--n > 0 && pos != -1)
            pos = str.indexOf(substr, pos + 1);
        return pos;
    }

    public void configure(Map<String,?> configs) {
        /* trawl through the datastream.*.write configs to map topic names to partitioning depths */
        for (String key : configs.keySet()) {
            if (key.matches(writeKeyRegex)) {
                try {
                    String topic = (String) configs.get(key);
                    String strDepth = (String) configs.get(key.replaceAll("\\.write$", ".partitioningDepth"));
                    Integer depth = Integer.parseInt(strDepth);
                    depths.put(topic, depth);
                } catch (Exception e) {
                    // whatever exception occurs here, just log it and ignore the topic
                    System.err.println("[error] Could not parse streams config for " + key + ". Exception was: " + e);
                }
            }
        }
    }

    public int partition(String topic, Object keyEnvelope, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        /* look up the correct partitioning depth for this topic */
        Integer depth = depths.get(topic);
        if (depth == null) {
            System.err.println("[error] No depth config for topic " + topic + ". Defaulting to " + defaultDepth + ".");
            depth = defaultDepth;
        }

        String key = ((KeyEnvelope) keyEnvelope).getKey();
        int truncPosition = ordinalIndexOf(key, "/", depth);
        String truncated;

        if (truncPosition < 0) 
            truncated = key;
        else 
            truncated = (key).substring(0,truncPosition);

        return Utils.toPositive(Utils.murmur2(truncated.getBytes())) % cluster.partitionCountForTopic(topic);
    }

    public void close() {
    }
}
