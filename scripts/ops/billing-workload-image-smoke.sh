#!/bin/sh
# Run only inside a disposable candidate container with --network none.
# Generates fake local material; never reads deployment credentials.
set -eu
umask 077
test_dir=$(mktemp -d /tmp/workload-smoke.XXXXXX)
cd "$test_dir"
export BILLING_EXPECTED_MONGO_HOST=cluster.example.invalid
export BILLING_EXPECTED_MONGO_DATABASE=example-billing-prod
export MONGODB_URI=mongodb+srv://fake:fake@cluster.example.invalid/example-billing-prod
export BILLING_WORKLOAD_TLS_KEY_STORE_PASSWORD=smoke-only-not-a-secret
openssl req -x509 -newkey rsa:2048 -nodes -days 2 \
    -subj /CN=workload.example.invalid -addext subjectAltName=DNS:workload.example.invalid \
    -keyout test.key -out test.pem >/dev/null 2>&1
openssl pkcs12 -export -inkey test.key -in test.pem -name workload \
    -passout env:BILLING_WORKLOAD_TLS_KEY_STORE_PASSWORD -out test.p12
BILLING_WORKLOAD_TLS_PKCS12_BASE64=$(base64 < test.p12 | tr -d '\n')
BILLING_EXPECTED_TLS_CERT_SHA256=$(openssl x509 -in test.pem -outform DER | openssl dgst -sha256 | awk '{print $NF}')
export BILLING_WORKLOAD_TLS_PKCS12_BASE64 BILLING_EXPECTED_TLS_CERT_SHA256
# Stub only the final Java launch; the actual image TLS/DB checks run unchanged.
mkdir bin
printf '%s\n' '#!/bin/sh' 'set -eu' 'test -r "$BILLING_WORKLOAD_TLS_KEY_STORE"' \
    'test "$(stat -c %a "$BILLING_WORKLOAD_TLS_KEY_STORE")" = 600' \
    'test -z "${BILLING_WORKLOAD_TLS_PKCS12_BASE64+x}"' \
    'echo SMOKE_JAVA_LAUNCH_OK' > bin/java
chmod 700 bin/java
export PATH="$test_dir/bin:$PATH"
case "${1:-valid}" in
    valid) expected=SMOKE_JAVA_LAUNCH_OK ;;
    wrong-db) export MONGODB_URI=mongodb+srv://fake:fake@cluster.example.invalid/other; expected=BILLING_BOOTSTRAP_TARGET_DB_MISMATCH ;;
    wrong-cert) export BILLING_EXPECTED_TLS_CERT_SHA256=incorrect; expected=BILLING_BOOTSTRAP_TLS_FINGERPRINT_MISMATCH ;;
    *) exit 2 ;;
esac
result=0
/app/workload-entrypoint.sh > result.txt 2>&1 || result=$?
grep -q "$expected" result.txt
if [ "${1:-valid}" = valid ]; then
    test "$result" = 0
else
    test "$result" != 0
    ! grep -q SMOKE_JAVA_LAUNCH_OK result.txt
fi
printf 'WORKLOAD_IMAGE_SMOKE_OK case=%s\n' "${1:-valid}"
