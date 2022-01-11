#!/bin/bash
#
# Script for DSH to fetch a certificate that can be used to authenticate
# against kafka. 
#

# TODO: For debugging only (otherwise it will echo back all the passwords)
set -eux

# This script receives (via the environment):

# PKI_CONFIG_DIR: Directory to store the jks with the certificates: NEEDS TO BE
# SET BEFORE CALLING THIS SCRIPT

# DSH_KAFKA_CONFIG_ENDPOINT: DNS of the PKI: automatically set by mesos

# DSH_SECRET_TOKEN: token that can be used to identify against the PKI:
# automatically set by mesos

# MESOS_TASK_ID: passed in by mesos to identify this container: automatically
# set by mesos

if [ -z "${PKI_CONFIG_DIR:-}" ]
then
        echo "PKI_CONFIG_DIR not set"
        exit 0
fi
# make sure the PKI_CONFIG_DIR really exists
mkdir -p "${PKI_CONFIG_DIR}"

if [ -z "${DSH_KAFKA_CONFIG_ENDPOINT:-}" ]
then
        echo "DSH_KAFKA_CONFIG_ENDPOINT not set"
        rm -f ${PKI_CONFIG_DIR}/datastreams.properties
        touch ${PKI_CONFIG_DIR}/datastreams.properties
        exit 0
fi

if [ -z "${DSH_SECRET_TOKEN:-}" ]
then
        echo "DSH_SECRET_TOKEN not set"
        rm -f ${PKI_CONFIG_DIR}/datastreams.properties
        touch ${PKI_CONFIG_DIR}/datastreams.properties
        exit 0
fi

if [ -z "${MESOS_TASK_ID:-}" ]
then
        echo "MESOS_TASK_ID not set"
        rm -f ${PKI_CONFIG_DIR}/datastreams.properties
        touch ${PKI_CONFIG_DIR}/datastreams.properties
        exit 0
fi

function get_ip_address() {
    # first method: hostname -i
    if [ "$(hostname -i 2> /dev/null | wc -l)" == "1" ]  ; then
        hostname -i
        return
    fi

    # second method: ip addr
    local device=$(ip route show match 1.1.1.1 2> /dev/null | sed 's/.* dev \([^ ]\+\).*/\1/')
    if [ ! -z "${device}" ] ; then
        local addr=$(ip addr show ${device} 2> /dev/null | grep inet | sed 's/.* \([0-9]\+\.[0-9]\+\.[0-9]\+\.[0-9]\+\).*/\1/')
        if [ ! -z "${addr}" ] ; then
            echo ${addr}
            return
        fi
    fi

    >&2 echo "could not determine IP address; IP SAN will not be added in the certificate"
}

# The group this application is running in. Typically this is the name of the
# tenant.
KAFKA_GROUP=`echo ${MARATHON_APP_ID} | cut -d / -f 2`

# DNS =
# marathon app id,
# split on /
# reverse the list
# remove empty lines
# replace line endings by . 
# append marathon.mesos
# Which becomes something like container_name.tenant.marathon.mesos

DNS=`echo ${MARATHON_APP_ID} | tr "/" "\n" | sed '1!G;h;$!d' | grep -v "^$" | tr "\n" "." | sed "s/$/marathon.mesos/g"`

# Get DSH CA certificate into a file
echo "${DSH_CA_CERTIFICATE}" > ${PKI_CONFIG_DIR}/ca.crt

# Use ca certificate to request DN needed for csr 
DN=`curl --cacert ${PKI_CONFIG_DIR}/ca.crt -s "${DSH_KAFKA_CONFIG_ENDPOINT}/dn/${KAFKA_GROUP}/${MESOS_TASK_ID}"`


# Bail out if we get back an invalid DN 
if echo "${DN}" | grep "^CN="
then
        echo "DN OK"
else
        echo "Could not get distinguished name: " ${DN}
        exit -1
fi

PKI_TRUSTPASS=`(tr -dc A-Za-z0-9 < /dev/urandom | head -c32)`
PKI_PASS=`(tr -dc A-Za-z0-9 < /dev/urandom | head -c32)`
PKI_STOREPASS=${PKI_PASS} #left for backwards compat
PKI_KEYPASS=${PKI_PASS} #left for backwards compat
PKI_TRUSTSTORE=${PKI_CONFIG_DIR}/truststore.jks
PKI_KEYSTORE=${PKI_CONFIG_DIR}/keystore.jks

# Make sure jks does not yet exists
rm -f ${PKI_KEYSTORE} ${PKI_TRUSTSTORE}

# In the trust store we import the ca certificate
keytool -importcert -noprompt -trustcacerts -alias ca -file ${PKI_CONFIG_DIR}/ca.crt -storepass ${PKI_TRUSTPASS} -keystore ${PKI_TRUSTSTORE}

# In the keystore we will do the same
keytool -importcert -noprompt -trustcacerts -alias ca -file ${PKI_CONFIG_DIR}/ca.crt -storepass ${PKI_PASS} -keypass ${PKI_PASS} -keystore ${PKI_KEYSTORE}

# Generate a new key
keytool -genkey -dname "${DN}" -alias client -keyalg RSA -keysize 2048 -storepass ${PKI_PASS} -keypass ${PKI_PASS} -keystore ${PKI_KEYSTORE}

# And request a certificate for it, using dns and (if we can deduce it) IP address as SANs
IPSAN=""
IP=$(get_ip_address)
if [ ! -z "$IP" ] ; then
    IPSAN="-ext SAN=ip:${IP}"
fi
keytool -certreq -alias client -ext san=dns:${DNS} -file ${PKI_CONFIG_DIR}/client.csr -storepass ${PKI_PASS} -keypass ${PKI_PASS} -keystore ${PKI_KEYSTORE} -ext SAN=dns:${DSH_CONTAINER_DNS_NAME} $IPSAN

# Ask PKI to sign the request (need to provide DSH_SECRET_TOKEN)
curl --cacert ${PKI_CONFIG_DIR}/ca.crt -s -X POST --data-binary @${PKI_CONFIG_DIR}/client.csr -H "X-Kafka-Config-Token: ${DSH_SECRET_TOKEN}" "${DSH_KAFKA_CONFIG_ENDPOINT}/sign/${KAFKA_GROUP}/${MESOS_TASK_ID}" > ${PKI_CONFIG_DIR}/client.crt

# Import signed certificate
keytool -importcert -alias client -file ${PKI_CONFIG_DIR}/client.crt -storepass ${PKI_PASS} -keypass ${PKI_PASS} -keystore ${PKI_KEYSTORE}

# fetch Kafka bootstrap broker list and stream configuration from PKI
# we need to jump through some hoops to get to the client cert and key in a format that is convenient for curl
# PKCS12 doesn't allow different password for store and key hence the store password is used for the keys.
keytool -importkeystore -srckeystore ${PKI_KEYSTORE} -destkeystore ${PKI_CONFIG_DIR}/client.pfx -deststoretype PKCS12 -srcalias client -srcstorepass ${PKI_PASS} -srckeypass ${PKI_PASS} -deststorepass ${PKI_PASS} -destkeypass ${PKI_PASS}
openssl pkcs12 -in ${PKI_CONFIG_DIR}/client.pfx -out ${PKI_CONFIG_DIR}/client.p12 -passin pass:${PKI_PASS} -passout pass:${PKI_PASS} 

# Fetch tenant and application specific configuration from the PKI including:
# streams, kafka consumergroup ids and bootstrap servers
curl -sf --cacert ${PKI_CONFIG_DIR}/ca.crt --cert ${PKI_CONFIG_DIR}/client.p12:${PKI_PASS} "${DSH_KAFKA_CONFIG_ENDPOINT}/kafka/config/${KAFKA_GROUP}/${MESOS_TASK_ID}?format=java" > ${PKI_CONFIG_DIR}/datastreams.properties

# Remove intermediate files
rm -f ${PKI_CONFIG_DIR}/client.csr ${PKI_CONFIG_DIR}/client.crt ${PKI_CONFIG_DIR}/client.pfx ${PKI_CONFIG_DIR}/client.p12

# TODO: For debugging: can be removed
keytool -list -storepass ${PKI_PASS} -keypass ${PKI_PASS} -keystore ${PKI_KEYSTORE}

# pick the first shared consumer group as the default consumer group
DEFAULT_KAFKA_CONSUMER_GROUP=$(cat ${PKI_CONFIG_DIR}/datastreams.properties | grep consumerGroups.shared | sed 's/^[^,]\+[=: ] *\([^,]\+\),\?.*/\1/')

# Generate kafka server config
cat >> ${PKI_CONFIG_DIR}/datastreams.properties <<EOF

# a default consumer group, can be overridden
group.id=${DEFAULT_KAFKA_CONSUMER_GROUP}
security.protocol=SSL
ssl.truststore.location=${PKI_TRUSTSTORE}
ssl.truststore.password=${PKI_TRUSTPASS}
ssl.keystore.location=${PKI_KEYSTORE}
ssl.keystore.password=${PKI_PASS}
ssl.key.password=${PKI_PASS}
EOF

echo "full properties file:"
cat ${PKI_CONFIG_DIR}/datastreams.properties
