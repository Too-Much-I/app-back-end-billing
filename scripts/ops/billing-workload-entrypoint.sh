#!/bin/sh
# Deployment-only TLS materialization; never enable shell tracing.
set -eu
umask 077
: "${BILLING_EXPECTED_MONGO_HOST:?missing expected Mongo host}"
: "${BILLING_EXPECTED_MONGO_DATABASE:?missing expected Mongo database}"
: "${MONGODB_URI:?missing Mongo configuration}"
case "$MONGODB_URI" in
    "mongodb+srv://"*@"$BILLING_EXPECTED_MONGO_HOST/$BILLING_EXPECTED_MONGO_DATABASE"|\
    "mongodb+srv://"*@"$BILLING_EXPECTED_MONGO_HOST/$BILLING_EXPECTED_MONGO_DATABASE"\?*) ;;
    *) printf 'BILLING_BOOTSTRAP_TARGET_DB_MISMATCH\n'; exit 1 ;;
esac
: "${BILLING_WORKLOAD_TLS_PKCS12_BASE64:?missing TLS material}"
: "${BILLING_WORKLOAD_TLS_KEY_STORE_PASSWORD:?missing TLS password}"
: "${BILLING_EXPECTED_TLS_CERT_SHA256:?missing certificate fingerprint}"
mkdir /tmp/billing-workload-tls
printf '%s' "$BILLING_WORKLOAD_TLS_PKCS12_BASE64" | base64 -d > /tmp/billing-workload-tls/server.p12
unset BILLING_WORKLOAD_TLS_PKCS12_BASE64
openssl pkcs12 -in /tmp/billing-workload-tls/server.p12 \
    -passin env:BILLING_WORKLOAD_TLS_KEY_STORE_PASSWORD -clcerts -nokeys \
    -out /tmp/billing-workload-tls/server.pem 2>/dev/null
actual_fingerprint=$(openssl x509 -in /tmp/billing-workload-tls/server.pem -outform DER | openssl dgst -sha256 | awk '{print $NF}')
if [ "$actual_fingerprint" != "$BILLING_EXPECTED_TLS_CERT_SHA256" ]; then
    printf 'BILLING_BOOTSTRAP_TLS_FINGERPRINT_MISMATCH\n'
    exit 1
fi
openssl x509 -in /tmp/billing-workload-tls/server.pem -checkend 86400 -noout > /dev/null
export BILLING_WORKLOAD_TLS_KEY_STORE=/tmp/billing-workload-tls/server.p12
printf 'BILLING_BOOTSTRAP_TARGET_AND_TLS_OK\n'
exec java -XX:MaxRAMPercentage=65.0 -jar /app/billing.jar
