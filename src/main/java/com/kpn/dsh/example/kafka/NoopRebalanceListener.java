package com.kpn.dsh.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;

/**
 * Noop consumer rebalance listener for Kafka.
 *
 * The kafka client API requires you to specify a rebalance listener for wildcard subscribes.
 */
class NoopRebalanceListener implements ConsumerRebalanceListener {
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) { }
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) { }
}