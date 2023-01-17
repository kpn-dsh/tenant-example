set -eu

usage() {
  cat <<EOF
Usage: $(basename "$0") <mode> <tenant> <apikey> <topic> <platform>

<mode> can be sub or pub.
The default value for <platform> is dsh-dev.dsh.np.aws.kpn.com.
EOF
  exit 1
}

get_mqtt_token() {
  local tenant="$1"
  local apikey="$2"
  local clientid="$3"
  local platform="$4"

  REST_TOKEN=$(curl -sfX POST --data "{ \"tenant\": \"$tenant\"}" -H apikey:"$apikey" https://api."$PLATFORM"/auth/v0/token)
  MQTT_TOKEN=$(curl -sfX POST --data "{ \"tenant\": \"$tenant\", \"id\": \"$clientid\" }" -H "Authorization: Bearer $REST_TOKEN" https://api."$PLATFORM"/datastreams/v0/mqtt/token)
  echo "$MQTT_TOKEN"
}

if [[ $# -lt 4 || $# -gt 5 ]]; then
  usage
fi

MODE="$1"
TENANT="$2"
APIKEY="$3"
TOPIC="$4"
CLIENTID="$(date | md5sum | cut -c 1-10)"
PLATFORM="${5:-dsh-dev.dsh.np.aws.kpn.com}"

MQTT_TOKEN=$(get_mqtt_token "$TENANT" "$APIKEY" "$CLIENTID" "$PLATFORM")

case $MODE in
sub)
  mosquitto_sub -h mqtt."$PLATFORM" -p 8883 -t "$TOPIC" -d -P "$MQTT_TOKEN" -u "$CLIENTID" -i "$CLIENTID" -v
  ;;
pub)
  mosquitto_pub -h mqtt."$PLATFORM" -p 8883 -t "$TOPIC" -d -P "$MQTT_TOKEN" -u "$CLIENTID" -i "$CLIENTID" -l
  ;;
*)
  echo "<mode> should be sub or pub"
  exit
  ;;
esac
