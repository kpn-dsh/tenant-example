package com.kpn.dsh.example;

import com.kpn.dsh.example.kafka.RecordProcessor;

/**
 * This example implements a really simple automated bot that responds to commands that are sent
 * to it over MQTT.
 *
 * Whenever a supported command is sent on /tt/&lt;input-stream&gt;/command/&lt;whatever/suffix&gt;,
 * the bot responds appropriately with a message on /tt/&lt;output-stream&gt;/response/&lt;whatever/suffix&gt;.
 * The bot understands following commands:
 * - "whoami": the response will include the sender's identity (tenant id/publisher id)
 * - "restart": the response will say "bye bye" and the container will restart.
 */
public class Main {
    public static void main(String[] args) {

        RecordProcessor recordProcessor = new RecordProcessor();
        recordProcessor.runProcess();

    }
}
