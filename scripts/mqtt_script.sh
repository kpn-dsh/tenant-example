set -eu



usage() {
    cat <<EOF
Usage: $(basename $0) <tenant> <apikey> <topic> [<platform>]



The default value for <platform> is dev.kpn-dsh.com.
EOF
    exit 1
}



get_mqtt_token() {
    local tenant="$1"
    local apikey="$2"
    local clientid="$3"
    local platform="$4"



    REST_TOKEN=$(curl -X POST --data "{ \"tenant\": \"$tenant\"}"  -H apikey:"$apikey" https://api.dsh-dev.dsh.np.aws.kpn.com/auth/v0/token)
    MQTT_TOKEN=$(curl -X POST --data "{ \"tenant\": \"$tenant\", \"id\": \"$clientid\" }" -H "Authorization: Bearer $REST_TOKEN" https://api.dsh-dev.dsh.np.aws.kpn.com/datastreams/v0/mqtt/token)
    echo "$MQTT_TOKEN"
}



decode_base64_url() {
  local len=$((${#1} % 4))
  local result="$1"
  if [ $len -eq 2 ]; then result="$1"'=='
  elif [ $len -eq 3 ]; then result="$1"'='
  fi
  echo "$result" | tr '_-' '/+' | openssl enc -d -base64
}



decode_jwt(){
   decode_base64_url $(echo -n $1 | cut -d "." -f 2) | jq .
}



if [ $# -lt 3 -o $# -gt 4 ] ; then
    usage
fi



TENANT="$1"
APIKEY="$2"
TOPIC="$3"
PLATFORM="${4:-dsh-dev.dsh.np.aws.kpn.com}"
CLIENTID="$(date | md5sum | cut -c 1-10)"



# CA certs file has a different location on different systems
: <<'END_COMMENT'
if [ -f /etc/ssl/certs/ca-certificates.crt ] ; then
    CACERT=/etc/ssl/certs/ca-certificates.crt
elif [ -f /opt/homebrew/etc/openssl@1.1/cert.pem ] ; then
    CACERT=/opt/homebrew/etc/openssl@1.1/cert.pem
elif [ -f /usr/local/etc/openssl/cert.pem ] ; then
    CACERT=/usr/local/etc/openssl/cert.pem
elif [ -f /usr/local/etc/ca-certificates/cert.pem ] ; then
    CACERT=/usr/local/etc/ca-certificates/cert.pem
else
    echo "Don't know where to find the trusted CA certificates file on your system."
    exit 1
fi
END_COMMENT



MQTT_TOKEN=$(get_mqtt_token "$TENANT" "$APIKEY" "$CLIENTID" "$PLATFORM")
echo $MQTT_TOKEN > mqtt_token

#ENDPOINT=$(decode_jwt "$MQTT_TOKEN" | jq -r .endpoint)
#PORT=$(decode_jwt "$MQTT_TOKEN" | jq -r '.ports.mqtts[0]')
