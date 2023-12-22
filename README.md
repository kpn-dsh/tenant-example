# DSH Tenant Example

This repository contains a very simple getting-started DSH application for a
tenant.

## Building

Make sure to first log in to the container registry:

```
docker login registry.cp.kpn-dsh.com
```

To build the image and push it to your container registry, run:

```
mvn clean package dockerfile:build dockerfile:push -Dtenant=<tenant-name> -Duserid=<tenant-userid>
```

where 
* `<tenant-name>` is the name of your tenant
* `<tenant-userid>` is the numeric UNIX userid assigned to your tenant

## Running

### Prerequisites

Your tenant must have at least the following permissions:
* READ access to a public stream
* WRITE access to a public stream

These streams may be the same stream, or may be different streams.

To interact with the application, you also need access to an API Client (you
will typically have one of these with the same name as your tenant) that has
permission to PUBLISH to the stream on which your tenant has READ access, and
SUBSCRIBE to the stream on which your tenant has WRITE access.

In other words, there is
* an _input stream_ that your tenant must be able to read from on Kafka, and
  your API Client must be able to publish to via MQTT.
* an _output stream_ that your tenant must be able to write to on Kafka, and
  your API Client must be able to subscribe to via MQTT.

### Deployment

To deploy the application, use the tenant console to create a "New Service".
Name the service "tenant-example", and use the following template for the
application definition:

```json
{
  "name": "tenant-example",
  "image": "registry.cp.kpn-dsh.com/<tenant-name>/tenant-example:1.1.0",
  "cpus": 0.1,
  "mem": 256,
  "env": {
    "INPUT_STREAM": "stream.<input-stream>",
    "OUTPUT_STREAM": "stream.<output-stream>"
  },
  "instances": 1,
  "singleInstance": false,
  "needsToken": true,
  "user": "<tenant-userid>:<tenant-userid>"
}
```

### Interact with the application

To interact with the application, you must set up an MQTT connection to the
platform. Consult the DSH documentation on how to do so.

Subscribe to `/tt/<output-stream>/response/#`.

Publish to `/tt/<input-stream>/command/hello`, with payload `whoami`.

If all goes well, you should now receive a response telling you who you are
through the MQTT subscription, on MQTT topic
`/tt/<output-stream>/response/hello`.
