package com.kpn.dsh.example;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * EnvelopeSerdes holds common functionality for the 
 * key and data envelope serializers.
 */
public class EnvelopeSerdes {
    static String STREAM_CONFIG = "envelope.streams";
    static Pattern streamTopicRegex = Pattern.compile("^stream\\.(.+)\\.[^.]+$");

    Set<String> nonEnvelopedStreams;

    public void configure(Map<String, ?> configs, boolean isKey) {
        nonEnvelopedStreams = new HashSet<String>();
        String csv = (String) configs.get(STREAM_CONFIG);
        if (csv != null) {
            String[] streams = csv.split(" *, *");
            for (String stream: streams) {
                nonEnvelopedStreams.add(stream);
            }
        }
    }

    public boolean isEnvelopedStream(String stream) {
        return !nonEnvelopedStreams.contains(stream);
    }

    public boolean isEnvelopedTopic(String topic) {
        Matcher m = streamTopicRegex.matcher(topic);
        if (m.matches()) {
            return isEnvelopedStream(m.group(1));
        }
        return false;
    }
}

