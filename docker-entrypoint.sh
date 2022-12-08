#!/bin/bash
set -eux

export PKI_CONFIG_DIR="/usr/share/tenant-example/conf"

. get_signed_certificate.sh

export PKI_TRUSTSTORE
export PKI_TRUSTPASS
export PKI_KEYSTORE
export PKI_STOREPASS
export PKI_KEYPASS

exec /usr/local/openjdk-8/bin/java -jar /usr/share/tenant-example/tenant-example.jar
