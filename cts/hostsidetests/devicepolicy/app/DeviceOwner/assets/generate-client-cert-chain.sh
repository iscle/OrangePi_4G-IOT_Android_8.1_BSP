#!/bin/bash

#
# Generates:
#  - user-cert-chain.crt
#  - user-cert-chain.key
#

set -e

WORKDIR='temp'

mkdir "$WORKDIR"
cp ca.conf "$WORKDIR/"
pushd "$WORKDIR"

## Generate root CA
mkdir -p rootca/{certs,crl,newcerts,private}
pushd rootca
touch index.txt
echo '1000' > serial
openssl req \
    -config ../ca.conf \
    -new \
    -x509 \
    -days 7300 \
    -sha256 \
    -extensions v3_ca \
    -keyout private/ca.key.pem \
    -out certs/ca.cert.pem
popd

## Generate Intermediate CA
mkdir intermediate intermediate/{certs,crl,csr,newcerts,private}
touch intermediate/index.txt

echo '1000' > intermediate/serial
echo '1000' > intermediate/crlnumber

openssl req \
    -config ca.conf \
    -new \
    -sha256 \
    -keyout intermediate/private/intermediate.key.pem \
    -out intermediate/csr/intermediate.csr.pem

openssl ca \
    -config ca.conf \
    -name RootCA \
    -extensions v3_intermediate_ca \
    -days 3650 \
    -notext \
    -md sha256 \
    -in intermediate/csr/intermediate.csr.pem \
    -out intermediate/certs/intermediate.cert.pem

## Generate client cert
openssl req \
    -config ca.conf \
    -newkey rsa:1024 \
    -keyout user.key.pem \
    -nodes \
    -days 3650 \
    -out user.csr.pem

openssl ca \
    -config ca.conf \
    -name IntermediateCA \
    -extensions usr_cert \
    -days 365 \
    -notext \
    -md sha256 \
    -in user.csr.pem \
    -out user.cert.pem

popd # WORKDIR

## Convert client cert to acceptable form
cat \
    "$WORKDIR"/user.cert.pem \
    "$WORKDIR"/intermediate/certs/intermediate.cert.pem \
    "$WORKDIR"/rootca/certs/ca.cert.pem \
    > user-cert-chain.crt

openssl pkcs8 \
    -topk8 \
    -nocrypt \
    -inform PEM \
    -outform DER \
    -in "$WORKDIR"/user.key.pem \
    -out user-cert-chain.key

rm -r "$WORKDIR"