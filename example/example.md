# Example using MQTT

## Steps
From the DSH console you can deploy the service to _\<tenant\>_ based on the example config in _tenant-config-example.json_.
Change the _image_ and _userId_ fields based on the tag you gave the image and the userId applicable for your tenant.
Assuming that there is a public stream called _training_ in your tenant with R/W access, 
you can now run the following commands to subscribe and publish to the topic.

```
.mqtt.sh sub <tenant> <apikey> /tt/training/response/#
.mqtt.sh pub <tenant> <apikey> /tt/training/command
```

Sample output for the _sub_ part:
```
> ./example/mqtt.sh sub greenbox-training <apikey> /tt/greenbox-training/response/#
Client 781b106bd6 sending CONNECT
Client 781b106bd6 received CONNACK (0)
Client 781b106bd6 sending SUBSCRIBE (Mid: 1, Topic: /tt/greenbox-training/response/#, QoS: 0, Options: 0x00)
Client 781b106bd6 received SUBACK
Subscribed (mid: 1): 0
Client 781b106bd6 received PUBLISH (d0, q0, r0, m0, '/tt/greenbox-training/response', ... (105 bytes))
/tt/greenbox-training/response greenbox-training/tenant-example-training says you are: tenant: "greenbox-training"
client: "e06c0637f8"
```

Sample output for the _pub_ part:
```
./example/mqtt.sh pub greenbox-training 5KUq4tQ5AvtXXa6brZwU /tt/greenbox-training/command
Client e06c0637f8 sending CONNECT
Client e06c0637f8 received CONNACK (0)
whoami
Client e06c0637f8 sending PUBLISH (d0, q0, r0, m1, '/tt/greenbox-training/command', ... (6 bytes))
```

## References

* https://docs.dsh-dev.dsh.np.aws.kpn.com/dsh-protocol-adapters/authentication/data-access-mqtt-tokens/
* https://docs.dsh-dev.dsh.np.aws.kpn.com/tutorial/tiod/mqtt/
* https://docs.dsh-dev.dsh.np.aws.kpn.com/tutorial/tiod/deploying/
