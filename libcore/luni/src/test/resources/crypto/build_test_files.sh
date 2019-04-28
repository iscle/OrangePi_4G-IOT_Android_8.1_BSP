#!/usr/bin/env bash
#
# Writes out all CSV files for crypto test data based on NIST test vectors.
#
# NIST vectors can be obtained from
# http://csrc.nist.gov/groups/STM/cavp/block-ciphers.html#test-vectors

if [ -z "$1" ] || [ ! -d "$1" ]; then
  echo "The directory of files to process must be supplied as an argument."
  exit 1
fi

cat "$1"/CBC*.rsp | ./parse_records.py > aes-cbc.csv
cat "$1"/CFB8*.rsp | ./parse_records.py > aes-cfb8.csv
cat "$1"/CFB128*.rsp | ./parse_records.py > aes-cfb128.csv
cat "$1"/ECB*.rsp | ./parse_records.py > aes-ecb.csv
cat "$1"/OFB*.rsp | ./parse_records.py > aes-ofb.csv
cat "$1"/TCBC*.rsp | ./parse_records.py > desede-cbc.csv
cat "$1"/TCFB8*.rsp | ./parse_records.py > desede-cfb8.csv
cat "$1"/TCFB64*.rsp | ./parse_records.py > desede-cfb64.csv
cat "$1"/TECB*.rsp | ./parse_records.py > desede-ecb.csv
cat "$1"/TOFB*.rsp | ./parse_records.py > desede-ofb.csv
cat "$1"/gcm*.rsp | ./parse_records.py > aes-gcm.csv
