# Example using MQTT

## Steps

Deploy service to _\<tenant\>_ based on example config in _tenant-config-example.json_.

```
.mqtt.sh sub <tenant> <apikey> /tt/<topic>/response/#
.mqtt.sh pub <tenant> <apikey> /tt/<topic>/command
```

## Documentation

* https://docs.dsh-dev.dsh.np.aws.kpn.com/dsh-protocol-adapters/authentication/data-access-mqtt-tokens/
* https://docs.dsh-dev.dsh.np.aws.kpn.com/tutorial/tiod/mqtt/
* https://docs.dsh-dev.dsh.np.aws.kpn.com/tutorial/tiod/deploying/
