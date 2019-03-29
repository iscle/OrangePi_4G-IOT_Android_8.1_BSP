#!/bin/bash

#
# Creates or overwrites 3 files in ./res/raw:
#   - cacert.der
#   - userkey.der
#   - usercert.der
#

tmpdir=$(mktemp -d './XXXXXXXX')
trap 'rm -r ${tmpdir}; echo; exit 1' EXIT INT QUIT

# CA_default defined in openssl.cnf
CA_DIR='demoCA'

SUBJECT=\
'/C=US'\
'/ST=CA'\
'/L=Mountain View'\
'/O=Android'\
'/CN=localhost'
PASSWORD='androidtest'

echo "Creating directory '$CA_DIR'..."
mkdir -p "$tmpdir"/"$CA_DIR"/newcerts \
    && echo '01' > "$tmpdir"/"$CA_DIR"/serial \
    && touch "$tmpdir"/"$CA_DIR"/index.txt

echo "Generating CA certificate..."
(cd "$tmpdir" \
    && openssl req \
        -new \
        -x509 \
        -days 3650 \
        -extensions v3_ca \
        -keyout 'cakey.pem' \
        -out 'cacert.pem' \
        -subj "$SUBJECT" \
        -passout 'pass:'"$PASSWORD" \
    && openssl x509 \
        -outform DER \
        -in 'cacert.pem' \
        -out 'cacert.der')

echo "Generating user key..."
(cd "$tmpdir" \
    && openssl req \
        -newkey rsa:2048 \
        -sha256 \
        -keyout 'userkey.pem' \
        -nodes \
        -days 3650 \
        -out 'userkey.req' \
        -subj "$SUBJECT" \
    && openssl pkcs8 \
        -topk8 \
        -outform DER \
        -in 'userkey.pem' \
        -out 'userkey.der' \
        -nocrypt)

echo "Generating user certificate..."
(cd "$tmpdir" \
    && openssl ca \
        -out 'usercert.pem' \
        -in 'userkey.req' \
        -cert 'cacert.pem' \
        -keyfile 'cakey.pem' \
        -days 3650 \
        -passin 'pass:'"$PASSWORD" \
        -batch \
    && openssl x509 \
        -outform DER \
        -in 'usercert.pem' \
        -out 'usercert.der')

# Copy important files to raw resources directory
cp \
    "$tmpdir"/cacert.der \
    "$tmpdir"/userkey.der \
    "$tmpdir"/usercert.der \
    'res/raw/'

echo "Finished"
exit
