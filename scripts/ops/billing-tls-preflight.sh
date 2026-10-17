#!/bin/sh
# One-shot deployment check. Never invoke with shell tracing enabled.
# Receives only the TLS secret, never Mongo or LC signing credentials.
set -eu
umask 077
stage=inputs
server_pid=
finish() {
    result=$?
    if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi
    if [ "$result" -ne 0 ]; then printf 'TLS_PREFLIGHT_FAILED stage=%s\n' "$stage"; fi
    # The task's private ephemeral filesystem is discarded when it stops.
}
trap finish EXIT
: "${TLS_PKCS12_BASE64:?missing TLS keystore}"
: "${TLS_KEYSTORE_PASSWORD:?missing TLS password}"
: "${TLS_HOST:?missing TLS hostname}"
: "${TLS_CERT_SHA256:?missing expected certificate fingerprint}"
work_tls_dir=$(mktemp -d /tmp/billing-tls-preflight.XXXXXX)
cd "$work_tls_dir"
stage=decode
printf '%s' "$TLS_PKCS12_BASE64" | base64 -d > server.p12
unset TLS_PKCS12_BASE64
stage=java-keystore
timeout 30 keytool -list -storetype PKCS12 -keystore server.p12 \
    -storepass:env TLS_KEYSTORE_PASSWORD > keystore-check.txt 2>/dev/null
grep -q PrivateKeyEntry keystore-check.txt
stage=certificate
openssl pkcs12 -in server.p12 -passin env:TLS_KEYSTORE_PASSWORD \
    -clcerts -nokeys -out server.pem 2>/dev/null
openssl pkcs12 -in server.p12 -passin env:TLS_KEYSTORE_PASSWORD \
    -nocerts -nodes -out server.key 2>/dev/null
unset TLS_KEYSTORE_PASSWORD
actual_fingerprint=$(openssl x509 -in server.pem -outform DER | openssl dgst -sha256 | awk '{print $NF}')
[ "$actual_fingerprint" = "$TLS_CERT_SHA256" ]
openssl x509 -in server.pem -checkend 86400 -noout > /dev/null
stage=handshake
openssl s_server -accept 127.0.0.1:8443 -cert server.pem -key server.key \
    -www -quiet > server-check.txt 2>&1 &
server_pid=$!
attempt=0
while [ "$attempt" -lt 10 ]; do
    if curl --noproxy '*' --silent --fail --max-time 3 --cacert server.pem \
        --resolve "$TLS_HOST:8443:127.0.0.1" "https://$TLS_HOST:8443/" > /dev/null 2>&1; then
        printf 'TLS_PREFLIGHT_OK java_pkcs12=true fingerprint=true verified_https=true\n'
        exit 0
    fi
    attempt=$((attempt + 1))
    sleep 1
done
exit 1
