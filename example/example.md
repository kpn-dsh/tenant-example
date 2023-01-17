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

## References

* https://docs.dsh-dev.dsh.np.aws.kpn.com/dsh-protocol-adapters/authentication/data-access-mqtt-tokens/
* https://docs.dsh-dev.dsh.np.aws.kpn.com/tutorial/tiod/mqtt/
* https://docs.dsh-dev.dsh.np.aws.kpn.com/tutorial/tiod/deploying/
